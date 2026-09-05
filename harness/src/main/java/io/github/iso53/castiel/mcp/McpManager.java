package io.github.iso53.castiel.mcp;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.iso53.castiel.config.McpServerConfig;
import io.github.iso53.castiel.model.McpServerStatus;
import io.github.iso53.castiel.util.AppPaths;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Owns every live MCP connection. Servers are declared in the standard
 * {@code mcp.json} file under the OS config directory; the file is watched
 * and changes are applied live. Tool names must be unique across servers;
 * duplicates are dropped.
 */
@Service
public class McpManager {

	private static final Logger log = LoggerFactory.getLogger(McpManager.class);

	private static final String CONFIG_FILE_NAME = "mcp.json";

	// Duplicate keys are rejected so a pasted block cannot silently shadow another server.
	private static final ObjectMapper CONFIG_JSON = new ObjectMapper()
		.configure(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature(), true);

	private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();
	/** Desired server set from the config file; replaced on each successful reload. */
	private volatile Map<String, McpServerConfig> registeredConfigs = Map.of();
	/** Last config-file parse error; empty while the file is valid. */
	private volatile String configError = "";
	/** Last reachability state per server id; change pings are emitted only on transitions. */
	private final Map<String, Boolean> lastKnownState = new ConcurrentHashMap<>();
	/** Fan-out of reachability-change pings for the UI's SSE feed. */
	private final Sinks.Many<String> stateChanges = Sinks.many().replay().latest();
	private WatchService watchService;

	/** The config file path and the last parse error, for the settings UI. */
	public record ConfigInfo(String path, String error) {}

	@PostConstruct
	void start() {
		ensureConfigFile();
		reloadConfigFile();
		for (McpServerConfig config : registeredConfigs.values()) {
			Thread.ofVirtual().name("mcp-connect-" + config.id()).start(() -> connect(config));
		}
		watchConfigFile();
	}

	@PreDestroy
	void closeAll() {
		for (String id : connections.keySet()) {
			disconnect(id);
		}
		if (watchService != null) {
			try {
				watchService.close();
			} catch (IOException ignored) {
				// Shutdown; the watcher thread exits on the closed service.
			}
		}
	}

	/** The config file path and the last parse error, for the settings UI. */
	public ConfigInfo configInfo() {
		return new ConfigInfo(configFile().toString(), configError);
	}

	/**
	 * Opens a connection to the server and registers it. Failures are logged;
	 * the server simply shows as disconnected until it becomes reachable again.
	 */
	public void connect(McpServerConfig config) {
		disconnect(config.id());
		// Construction cannot fail; every connection attempt (handshake included)
		// happens inside the try, so failures are always caught here.
		McpConnection connection = new McpConnection(config);
		try {
			connection.connect();
			connections.put(config.id(), connection);
			notifyState(config.id(), true);
			log.info("Connected to MCP server '{}' ({} tools)", config.id(), connection.tools().size());
		} catch (Exception ex) {
			connection.close();
			notifyState(config.id(), false);
			log.warn("Could not connect to MCP server '{}': {}", config.id(), describe(ex));
			log.debug("MCP connection failure for '{}'", config.id(), ex);
		}
	}

	/** Renders an exception for a log line, falling back to the class name when the message is empty. */
	private static String describe(Exception ex) {
		String message = ex.getMessage();
		return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
	}

	public void disconnect(String id) {
		McpConnection existing = connections.remove(id);
		if (existing != null) {
			existing.close();
			notifyState(id, false);
		}
	}

	/** SSE feed of reachability changes; each ping names the server that changed. */
	public Flux<ServerSentEvent<String>> events() {
		return stateChanges.asFlux().map(tick -> ServerSentEvent.builder(tick).build());
	}

	/** Emits a ping only when a server's reachability flips, so status fetches never loop back here. */
	private void notifyState(String id, boolean connected) {
		if (!Objects.equals(lastKnownState.put(id, connected), connected)) {
			stateChanges.tryEmitNext(id);
		}
	}

	/** Reports every registered server with its live connection state. */
	public List<McpServerStatus> statuses() {
		List<McpServerStatus> statuses = new ArrayList<>();
		for (McpServerConfig config : registeredConfigs.values()) {
			statuses.add(status(config));
		}
		return statuses;
	}

	/** Builds the status report for one registered server from its live connection. */
	private McpServerStatus status(McpServerConfig config) {
		McpConnection connection = connections.get(config.id());
		boolean connected = connection != null && connection.checkHealth();
		List<String> tools = connection == null ? List.of() : names(connection.tools());
		return new McpServerStatus(config.id(), config.displayName(), config.type(), config.target(), connected, tools);
	}

	/** Reconnects every unreachable server and returns the refreshed statuses. */
	public List<McpServerStatus> reconnectDisconnected() {
		for (McpServerConfig config : registeredConfigs.values()) {
			McpConnection connection = connections.get(config.id());
			if (connection == null || !connection.checkHealth()) {
				connect(config);
			}
		}
		return statuses();
	}

	/** Tool specifications from every connected server; MCP-internal duplicates are skipped. */
	public List<ToolSpecification> toolSpecifications() {
		List<ToolSpecification> specifications = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (McpConnection connection : connections.values()) {
			for (ToolSpecification specification : connection.tools()) {
				if (seen.add(specification.name())) {
					specifications.add(specification);
				} else {
					log.warn("Skipping MCP tool '{}' offered by more than one server", specification.name());
				}
			}
		}
		return specifications;
	}

	/**
	 * Executes an MCP tool on the server that offers it. Returns an error string
	 * when no connected server owns the tool, mirroring the local tool loop.
	 */
	public String executeTool(ToolExecutionRequest request) {
		for (McpConnection connection : connections.values()) {
			if (connection.hasTool(request.name())) {
				try {
					String result = connection.executeTool(request);
					notifyState(connection.config().id(), true);
					return result;
				} catch (Exception ex) {
					// A failed request is the only signal that a registered server went away.
					notifyState(connection.config().id(), false);
					return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
				}
			}
		}
		return "Error: unknown tool \"" + request.name() + "\"";
	}

	private static List<String> names(List<ToolSpecification> tools) {
		return tools.stream().map(ToolSpecification::name).toList();
	}

	private static Path configFile() {
		return AppPaths.configDir().resolve(CONFIG_FILE_NAME);
	}

	/** Creates an empty template when the config file is missing. */
	private void ensureConfigFile() {
		Path file = configFile();
		if (Files.isRegularFile(file)) {
			return;
		}
		ObjectNode root = CONFIG_JSON.createObjectNode();
		root.putObject("mcpServers");
		try {
			Files.createDirectories(file.getParent());
			CONFIG_JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
			log.info("Created MCP config file {}", file);
		} catch (IOException ex) {
			log.warn("Could not create MCP config file {}", file, ex);
		}
	}

	/** Applies the config file: connects new servers, disconnects removed ones, reconnects changed ones. */
	private void reloadConfigFile() {
		if (!Files.isRegularFile(configFile())) {
			ensureConfigFile();
			applyConfigSet(Map.of());
			return;
		}
		Map<String, McpServerConfig> loaded = readConfigFile();
		if (loaded != null) {
			applyConfigSet(loaded);
		}
	}

	private void applyConfigSet(Map<String, McpServerConfig> loaded) {
		Map<String, McpServerConfig> previous = registeredConfigs;
		registeredConfigs = loaded;
		for (String id : previous.keySet()) {
			if (!loaded.containsKey(id)) {
				disconnect(id);
			}
		}
		for (McpServerConfig config : loaded.values()) {
			McpServerConfig old = previous.get(config.id());
			if (old == null || !old.equals(config)) {
				Thread.ofVirtual().name("mcp-connect-" + config.id()).start(() -> connect(config));
			}
		}
	}

	/** Reads and validates the config file; returns null (keeping the last good set) on error. */
	private Map<String, McpServerConfig> readConfigFile() {
		try {
			JsonNode root = CONFIG_JSON.readTree(configFile().toFile());
			configError = "";
			return McpServerConfig.fromFileJson(root);
		} catch (IOException | IllegalArgumentException ex) {
			configError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			log.warn("Invalid MCP config file {}: {}", configFile(), configError);
			return null;
		}
	}

	/** Watches the config directory and reloads mcp.json on changes (debounced). */
	private void watchConfigFile() {
		try {
			watchService = FileSystems.getDefault().newWatchService();
			configFile().getParent().register(watchService,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_DELETE);
		} catch (IOException ex) {
			log.warn("Could not watch {}; config changes need an app restart", configFile(), ex);
			return;
		}
		Thread.ofVirtual().name("mcp-config-watcher").start(() -> {
			while (true) {
				try {
					WatchKey key = watchService.take();
					boolean relevant = false;
					for (WatchEvent<?> event : key.pollEvents()) {
						Object context = event.context();
						relevant |= context != null && CONFIG_FILE_NAME.equals(context.toString());
					}
					key.reset();
					if (!relevant) {
						continue;
					}
					// Editors fire bursts of events per save; wait once, then drain.
					Thread.sleep(1000);
					WatchKey pending = watchService.poll();
					while (pending != null) {
						pending.pollEvents();
						pending.reset();
						pending = watchService.poll();
					}
					reloadConfigFile();
				} catch (InterruptedException | ClosedWatchServiceException ex) {
					return;
				} catch (Exception ex) {
					log.warn("MCP config watch failed: {}", ex.toString());
				}
			}
		});
	}
}
