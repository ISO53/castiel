package io.github.iso53.castiel.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.iso53.castiel.config.McpServerConfig;
import io.github.iso53.castiel.model.McpServerStatus;
import io.github.iso53.castiel.model.UserSettings;
import io.github.iso53.castiel.service.UserSettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Owns every live MCP connection: registering servers, reconnecting on startup,
 * and exposing their tools to the chat loop. Tool names must be unique across
 * servers; duplicates are dropped.
 */
@Service
public class McpManager {

	private static final Logger log = LoggerFactory.getLogger(McpManager.class);

	private final UserSettingsService userSettingsService;
	private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();

	public McpManager(UserSettingsService userSettingsService) {
		this.userSettingsService = userSettingsService;
	}

	/** Reconnects to every registered server in the background so a dead server never blocks startup. */
	@PostConstruct
	void connectRegisteredServers() {
		for (McpServerConfig config : userSettingsService.get().mcpServers().values()) {
			Thread.ofVirtual().name("mcp-connect-" + config.id()).start(() -> connect(config));
		}
	}

	@PreDestroy
	void closeAll() {
		for (String id : connections.keySet()) {
			disconnect(id);
		}
	}

	/**
	 * Handshakes with a not-yet-registered server and reports what it offers.
	 * Nothing is persisted; the caller decides whether to add the server afterwards.
	 */
	public McpServerStatus probe(McpServerConfig config) {
		config.validate();
		try (McpConnection connection = new McpConnection(config)) {
			List<ToolSpecification> tools = connection.connect();
			return new McpServerStatus(config.id(), config.displayName(), config.type(), config.target(), true, names(tools));
		} catch (Exception ex) {
			throw new IllegalArgumentException("Could not reach an MCP server there: " + ex.getMessage(), ex);
		}
	}

	/** Persists a server and opens a connection to it. */
	public UserSettings add(McpServerConfig config) {
		config.validate();
		if (config.id().isBlank()) {
			throw new IllegalArgumentException("MCP server id is required");
		}
		if (userSettingsService.get().mcpServers().containsKey(config.id())) {
			throw new IllegalArgumentException("An MCP server with id '" + config.id() + "' already exists");
		}
		userSettingsService.upsertMcpServer(config);
		connect(config);
		return userSettingsService.get();
	}

	/** Forgets a server and closes its connection. */
	public UserSettings remove(String id) {
		disconnect(id);
		userSettingsService.removeMcpServer(id);
		return userSettingsService.get();
	}

	/**
	 * Opens a connection to the server and registers it. Failures are logged;
	 * the server simply shows as disconnected until it becomes reachable again.
	 */
	public void connect(McpServerConfig config) {
		disconnect(config.id());
		McpConnection connection = new McpConnection(config);
		try {
			connection.connect();
			connections.put(config.id(), connection);
			log.info("Connected to MCP server '{}' ({} tools)", config.id(), connection.tools().size());
		} catch (Exception ex) {
			connection.close();
			log.warn("Could not connect to MCP server '{}': {}", config.id(), ex.getMessage());
		}
	}

	public void disconnect(String id) {
		McpConnection existing = connections.remove(id);
		if (existing != null) {
			existing.close();
		}
	}

	/** Reports every registered server with its live connection state. */
	public List<McpServerStatus> statuses() {
		List<McpServerStatus> statuses = new ArrayList<>();
		for (McpServerConfig config : userSettingsService.get().mcpServers().values()) {
			McpConnection connection = connections.get(config.id());
			boolean connected = connection != null && connection.checkHealth();
			List<String> tools = connection == null ? List.of() : names(connection.tools());
			statuses.add(new McpServerStatus(config.id(), config.displayName(), config.type(), config.target(), connected, tools));
		}
		return statuses;
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
					return connection.executeTool(request);
				} catch (Exception ex) {
					return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
				}
			}
		}
		return "Error: unknown tool \"" + request.name() + "\"";
	}

	private static List<String> names(List<ToolSpecification> tools) {
		return tools.stream().map(ToolSpecification::name).toList();
	}
}
