package io.github.iso53.castiel.config;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.*;

/**
 * Configuration for one MCP server, parsed from the standard {@code mcp.json}
 * format (see {@link #fromFileJson}). Two transport types are supported:
 * HTTP servers are reached through a URL, stdio servers are started as
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

	/**
	 * Parses the standard config file shape ({@code {"mcpServers": {...}}}) into
	 * servers keyed by their id. Blank ids and invalid entries fail the whole file
	 * so the UI can surface one clear error instead of a half-applied config.
	 */
	public static Map<String, McpServerConfig> fromFileJson(JsonNode root) {
		if (root == null || !root.isObject()) {
			throw new IllegalArgumentException("the config file must contain a JSON object");
		}
		JsonNode servers = root.get("mcpServers");
		if (servers == null) {
			return Map.of();
		}
		if (!servers.isObject()) {
			throw new IllegalArgumentException("\"mcpServers\" must be an object");
		}
		Map<String, McpServerConfig> result = new LinkedHashMap<>();
		for (Iterator<Map.Entry<String, JsonNode>> it = servers.fields(); it.hasNext(); ) {
			Map.Entry<String, JsonNode> entry = it.next();
			String id = entry.getKey() == null ? "" : entry.getKey().trim();
			if (id.isBlank()) {
				throw new IllegalArgumentException("MCP server ids must not be blank");
			}
			try {
				result.put(id, fromFileEntry(id, entry.getValue()));
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("MCP server '" + id + "': " + ex.getMessage(), ex);
			}
		}
		return result;
	}

	/** Parses one entry: {@code command} means stdio, {@code url} means streamable HTTP. */
	public static McpServerConfig fromFileEntry(String id, JsonNode node) {
		if (node == null || !node.isObject()) {
			throw new IllegalArgumentException("each server entry must be an object");
		}
		String name = text(node, "name");
		String command = text(node, "command");
		if (command != null) {
			List<String> fileArgs = new ArrayList<>();
			JsonNode argsNode = node.get("args");
			if (argsNode != null) {
				if (!argsNode.isArray()) {
					throw new IllegalArgumentException("\"args\" must be an array of strings");
				}
				argsNode.forEach(arg -> fileArgs.add(arg.asText()));
			}
			Map<String, String> env = new LinkedHashMap<>();
			JsonNode envNode = node.get("env");
			if (envNode != null) {
				if (!envNode.isObject()) {
					throw new IllegalArgumentException("\"env\" must be an object");
				}
				for (Iterator<Map.Entry<String, JsonNode>> it = envNode.fields(); it.hasNext(); ) {
					Map.Entry<String, JsonNode> var = it.next();
					env.put(var.getKey(), var.getValue().asText());
				}
			}
			return new McpServerConfig(id, name, Type.STDIO, null, null, null, command, fileArgs, env);
		}
		String url = text(node, "url");
		if (url != null) {
			URI uri;
			try {
				uri = URI.create(url);
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("\"url\" is not a valid URI: " + url, ex);
			}
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
			if (!scheme.equals("http")) {
				throw new IllegalArgumentException("\"url\" must be an http:// endpoint (https is not supported yet)");
			}
			if (uri.getHost() == null || uri.getHost().isBlank()) {
				throw new IllegalArgumentException("\"url\" is missing a host: " + url);
			}
			int port = uri.getPort() > 0 ? uri.getPort() : 80;
			String path = uri.getPath() == null ? "" : uri.getPath();
			return new McpServerConfig(id, name, Type.HTTP, uri.getHost(), port, path, null, List.of(), Map.of());
		}
		throw new IllegalArgumentException("either \"command\" or \"url\" is required");
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
	}
}
