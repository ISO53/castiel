package io.github.iso53.castiel.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.github.iso53.castiel.config.McpServerConfig;

import java.time.Duration;
import java.util.List;

/**
 * Owns the LangChain4j MCP client for one server. Construction is cheap and
 * cannot fail; {@link #connect()} performs the MCP handshake, and afterwards
 * the tool list is kept for the chat loop.
 */
public class McpConnection implements AutoCloseable {

	private final McpServerConfig config;
	private volatile McpClient client;
	private volatile List<ToolSpecification> tools = List.of();

	public McpConnection(McpServerConfig config) {
		this.config = config;
	}

	/**
	 * Performs the handshake and fetches the tool list from the server. The
	 * handshake happens eagerly when the client is built, so any connection
	 * failure surfaces here and the half-open resources are released.
	 */
	public List<ToolSpecification> connect() {
		McpClient client = buildClient(config);
		try {
			tools = List.copyOf(client.listTools());
		} catch (Exception ex) {
			closeQuietly(client);
			throw ex;
		}
		this.client = client;
		return tools;
	}

	/** Pings the server; returns false when it is unreachable. */
	public boolean checkHealth() {
		try {
			client.checkHealth();
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	public boolean hasTool(String name) {
		return tools.stream().anyMatch(specification -> specification.name().equals(name));
	}

	public List<ToolSpecification> tools() {
		return tools;
	}

	public McpServerConfig config() {
		return config;
	}

	/** Executes a tool on the server and returns the result as plain text. */
	public String executeTool(ToolExecutionRequest request) {
		ToolExecutionResult result = client.executeTool(request);
		String text = extractText(result);
		return result.isError() ? "Error: " + text : text;
	}

	@Override
	public void close() {
		McpClient client = this.client;
		this.client = null;
		closeQuietly(client);
	}

	private static void closeQuietly(McpClient client) {
		if (client == null) {
			return;
		}
		try {
			client.close();
		} catch (Exception ignored) {
			// Closing a half-open connection must never fail the caller.
		}
	}

	private static McpClient buildClient(McpServerConfig config) {
		McpTransport transport = switch (config.type() == null ? McpServerConfig.Type.HTTP : config.type()) {
			case HTTP -> StreamableHttpMcpTransport.builder()
				.url(config.url())
				.timeout(Duration.ofSeconds(30))
				.build();
			case STDIO -> StdioMcpTransport.builder()
				.command(config.commandLine())
				.environment(config.env())
				.build();
		};
		try {
			// Building the client performs the MCP handshake immediately.
			return DefaultMcpClient.builder()
				.key(config.id())
				.clientName("castiel")
				.pingTimeout(Duration.ofSeconds(5))
				.transport(transport)
				.build();
		} catch (Exception ex) {
			try {
				transport.close();
			} catch (Exception ignored) {
				// The failure to report is the handshake failure, not this one.
			}
			throw ex;
		}
	}

	// Flattens the result contents into the plain text the chat loop expects.
	private static String extractText(ToolExecutionResult result) {
		StringBuilder text = new StringBuilder();
		for (Content content : result.resultContents()) {
			if (content instanceof TextContent textContent) {
				text.append(textContent.text());
			} else {
				text.append("[").append(content.type()).append(" content]");
			}
		}
		return text.toString();
	}
}
