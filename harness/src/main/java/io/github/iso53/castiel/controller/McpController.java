package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.config.McpServerConfig;
import io.github.iso53.castiel.mcp.McpManager;
import io.github.iso53.castiel.model.McpServerStatus;
import io.github.iso53.castiel.model.UserSettings;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoints for registering and inspecting MCP servers.
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

	private final McpManager mcpManager;

	public McpController(McpManager mcpManager) {
		this.mcpManager = mcpManager;
	}

	/** Handshakes with a not-yet-registered server so the UI can confirm it before adding. */
	@PostMapping("/probe")
	public McpServerStatus probe(@RequestBody McpServerConfig body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			return mcpManager.probe(body);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	@GetMapping("/servers")
	public List<McpServerStatus> servers() {
		return mcpManager.statuses();
	}

	/** Retries the handshake with a registered server, e.g. after it was started late. */
	@PostMapping("/servers/{id}/reconnect")
	public McpServerStatus reconnect(@PathVariable("id") String id) {
		try {
			return mcpManager.reconnect(id);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
		}
	}

	@PostMapping("/servers")
	public UserSettings add(@RequestBody McpServerConfig body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			return mcpManager.add(body);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	@DeleteMapping("/servers/{id}")
	public UserSettings remove(@PathVariable("id") String id) {
		return mcpManager.remove(id);
	}
}
