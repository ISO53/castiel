package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.mcp.McpManager;
import io.github.iso53.castiel.model.McpServerStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

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
}