package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.EngagementPhase;
import io.github.iso53.castiel.model.SetPhaseRequest;
import io.github.iso53.castiel.service.EngagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the current pentest phase to the frontend: the phase bar reads it and the
 * user advances the engagement through it.
 */
@RestController
@RequestMapping("/api/engagement")
public class EngagementController {

	private final EngagementService engagementService;

	public EngagementController(EngagementService engagementService) {
		this.engagementService = engagementService;
	}

	/**
	 * Returns the current engagement phase.
	 */
	@GetMapping
	public EngagementPhase get() {
		requireWorkspace();
		return engagementService.currentPhase();
	}

	/**
	 * Sets the current engagement phase.
	 */
	@PutMapping("/phase")
	public EngagementPhase set(@RequestBody SetPhaseRequest request) {
		if (request == null || request.phase() < EngagementService.FIRST_PHASE) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"A phase number between " + EngagementService.FIRST_PHASE + " and "
					+ EngagementService.LAST_PHASE + " is required"
			);
		}
		requireWorkspace();
		return engagementService.setPhase(request.phase());
	}

	private void requireWorkspace() {
		try {
			engagementService.currentPhase();
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
		}
	}
}