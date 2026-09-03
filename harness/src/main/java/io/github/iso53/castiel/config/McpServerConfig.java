package io.github.iso53.castiel.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persisted configuration for one MCP server. Two transport types are supported:
 * HTTP servers are reached through a host and port, stdio servers are started as
 * local subprocesses via a command.
 *
 * @param id      Unique id of the server entry.
 * @param name    Optional display name; falls back to the id when missing.
 * @param type    Transport type, {@code HTTP} or {@code STDIO}.
 * @param host    HTTP only: host the MCP server listens on (e.g. 127.0.0.1).
 * @param port    HTTP only: port the MCP server listens on.
 * @param path    HTTP only: endpoint path, defaults to "mcp".
 * @param command STDIO only: executable that runs the MCP server.
 * @param args    STDIO only: arguments passed to the executable.
 * @param env     STDIO only: extra environment variables for the subprocess.
 */
public record McpServerConfig(
	String id,
	String name,
	Type type,
	String host,
	Integer port,
	String path,
	String command,
	List<String> args,
	Map<String, String> env
) {

	public enum Type { HTTP, STDIO }

	public McpServerConfig {
		id = id == null ? "" : id.trim();
		name = name == null || name.isBlank() ? id : name.trim();
		path = path == null || path.isBlank() ? "mcp" : path.trim();
		args = args == null ? List.of() : List.copyOf(args);
		env = env == null ? Map.of() : Map.copyOf(env);
	}

	/** Checks that the fields required by the configured transport are present. */
	public void validate() {
		switch (type == null ? Type.HTTP : type) {
			case HTTP -> {
				if (host == null || host.isBlank()) {
					throw new IllegalArgumentException("host is required for HTTP MCP servers");
				}
				if (port == null || port < 1 || port > 65535) {
					throw new IllegalArgumentException("port must be between 1 and 65535 for HTTP MCP servers");
				}
			}
			case STDIO -> {
				if (command == null || command.isBlank()) {
					throw new IllegalArgumentException("command is required for stdio MCP servers");
				}
			}
		}
	}

	/** Display name shown in the UI; falls back to the id. */
	public String displayName() {
		return name == null || name.isBlank() ? id : name;
	}

	/** Human-readable connection target, e.g. "127.0.0.1:3000/mcp" or the command line. */
	public String target() {
		return type == Type.STDIO ? String.join(" ", commandLine()) : host + ":" + port + "/" + path;
	}

	/** HTTP only: streamable HTTP endpoint of the MCP server. */
	public String url() {
		String endpoint = path.startsWith("/") ? path : "/" + path;
		return "http://" + host + ":" + port + endpoint;
	}

	/** STDIO only: full command line, executable first, then its arguments. */
	public List<String> commandLine() {
		List<String> line = new ArrayList<>();
		if (command != null && !command.isBlank()) {
			line.add(command);
		}
		line.addAll(args);
		return line;
	}
}
