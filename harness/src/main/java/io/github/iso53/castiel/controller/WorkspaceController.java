package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.CreateWorkspaceRequest;
import io.github.iso53.castiel.model.OpenWorkspaceRequest;
import io.github.iso53.castiel.model.WorkspaceState;
import io.github.iso53.castiel.service.WorkspaceSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the active workspace session to the frontend.
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

	private final WorkspaceSession workspaceSession;

	public WorkspaceController(WorkspaceSession workspaceSession) {
		this.workspaceSession = workspaceSession;
	}

	/**
	 * Returns the current workspace state.
	 */
	@GetMapping
	public WorkspaceState get() {
		return workspaceSession.get();
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
