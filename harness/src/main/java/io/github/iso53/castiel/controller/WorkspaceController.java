package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.CreateWorkspaceRequest;
import io.github.iso53.castiel.model.OpenWorkspaceRequest;
import io.github.iso53.castiel.model.WorkspaceState;
import io.github.iso53.castiel.service.WorkspaceEventBus;
import io.github.iso53.castiel.service.WorkspaceSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * Exposes the active workspace session to the frontend.
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

	private final WorkspaceSession workspaceSession;
	private final WorkspaceEventBus workspaceEvents;

	public WorkspaceController(WorkspaceSession workspaceSession, WorkspaceEventBus workspaceEvents) {
		this.workspaceSession = workspaceSession;
		this.workspaceEvents = workspaceEvents;
	}

	/**
	 * Returns the current workspace state.
	 */
	@GetMapping
	public WorkspaceState get() {
		return workspaceSession.get();
	}

	/** SSE feed of workspace file changes; the payload is the changed file's name or "*". */
	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> events() {
		return workspaceEvents.events();
	}

	/**
	 * Opens an existing directory as the workspace.
	 */
	@PutMapping
	public WorkspaceState open(@RequestBody OpenWorkspaceRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			return workspaceSession.open(request.cwd());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/**
	 * Creates a new workspace folder and sets it as the current working directory.
	 */
	@PostMapping
	public WorkspaceState create(@RequestBody CreateWorkspaceRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			return workspaceSession.create(request.parentPath(), request.name());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}
}
