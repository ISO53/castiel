package io.github.iso53.castiel.model;

import io.github.iso53.castiel.config.McpServerConfig;
import java.util.List;

/**
 * State of one MCP server as reported to the frontend.
 *
 * @param id        Unique id of the server entry.
 * @param name      Display name of the server.
 * @param type      Transport type, {@code HTTP} or {@code STDIO}.
 * @param target    Human-readable connection target.
 * @param connected Whether the server currently answers pings.
 * @param tools     Names of the tools the server offers.
 */
public record McpServerStatus(
	String id,
	String name,
	McpServerConfig.Type type,
	String target,
	boolean connected,
	List<String> tools
) {

	public McpServerStatus {
		tools = tools == null ? List.of() : List.copyOf(tools);
	}
}
