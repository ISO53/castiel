package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.service.UpdateService;
import io.github.iso53.castiel.service.UserSettingsService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * The update feature's REST surface: status, check, install, the live SSE feed,
 * the startup-check preference and the log-directory reveal.
 */
@RestController
@RequestMapping("/api/update")
public class UpdateController {

	private final UpdateService updateService;
	private final UserSettingsService userSettingsService;

	public UpdateController(UpdateService updateService, UserSettingsService userSettingsService) {
		this.updateService = updateService;
		this.userSettingsService = userSettingsService;
	}

	@GetMapping
	public UpdateService.UpdateStatus status() {
		return updateService.status();
	}

	/** Compares this build with the latest GitHub release; force skips the ten-minute cache. */
	@PostMapping("/check")
	public UpdateService.UpdateStatus check(@RequestParam(defaultValue = "false") boolean force) {
		try {
			return updateService.check(force);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Update check failed: " + ex.getMessage(), ex);
		}
	}

	/** Starts the install of a checked, installable update. */
	@PostMapping("/install")
	public UpdateService.UpdateStatus install() {
		try {
			updateService.install();
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
		}
		return updateService.status();
	}

	/** Live status feed; one event per phase or progress change. */
	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> events() {
		return updateService.events();
	}

	/** Replaces the check-on-startup preference. */
	@PutMapping("/prefs")
	public UpdateService.UpdateStatus putPrefs(@RequestBody UpdatePrefs body) {
		if (body == null || body.checkOnStartup() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkOnStartup is required");
		}
		userSettingsService.setCheckForUpdatesOnStartup(body.checkOnStartup());
		return updateService.status();
	}

	/** Opens the update log directory; headless builds answer with the path instead. */
	@PostMapping("/logs/open")
	public LogOpenResult openLogs() {
		try {
			String path = updateService.openLogs();
			return new LogOpenResult(path == null, path);
		} catch (IOException ex) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not open the log directory: " + ex.getMessage(),
				ex
			);
		}
	}

	/** Request body for {@link #putPrefs}. */
	public record UpdatePrefs(Boolean checkOnStartup) {}

	/** Response of {@link #openLogs}: opened says a file manager took it; path is the fallback text. */
	public record LogOpenResult(boolean opened, String path) {}
}
