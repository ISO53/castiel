package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.mcp.McpManager;
import io.github.iso53.castiel.model.McpServerStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the MCP servers declared in the mcp.json config file.
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

	private final McpManager mcpManager;

	public McpController(McpManager mcpManager) {
		this.mcpManager = mcpManager;
	}

	/** Config file path and last parse error, for the settings UI. */
	@GetMapping("/config")
	public McpManager.ConfigInfo config() {
		return mcpManager.configInfo();
	}

	@GetMapping("/servers")
	public List<McpServerStatus> servers() {
		return mcpManager.statuses();
	}

	/** SSE feed of reachability changes; each ping names the server that changed. */
	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> events() {
		return mcpManager.events();
	}

	/** Reconnects every currently unreachable server and returns the refreshed statuses. */
	@PostMapping("/servers/reconnect")
	public List<McpServerStatus> reconnect() {
		return mcpManager.reconnectDisconnected();
	}

	/** Enables or disables one server; the flag is persisted in mcp.json and applied live. */
	@PostMapping("/servers/{id}/enabled")
	public List<McpServerStatus> setEnabled(@PathVariable String id, @RequestBody EnabledRequest request) {
		return mcpManager.setEnabled(id, request.enabled());
	}

	/** Request body for the per-server enabled toggle. */
	public record EnabledRequest(boolean enabled) {}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> notFound(IllegalArgumentException ex) {
		return Map.of("error", ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, String> conflict(IllegalStateException ex) {
		return Map.of("error", ex.getMessage());
	}
}