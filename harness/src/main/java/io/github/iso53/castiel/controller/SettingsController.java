package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.*;
import io.github.iso53.castiel.service.LlmClientFactory;
import io.github.iso53.castiel.service.UserSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Persisted user settings and provider connectivity checks.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

	private final UserSettingsService userSettingsService;
	private final LlmClientFactory llmClientFactory;

	public SettingsController(UserSettingsService userSettingsService, LlmClientFactory llmClientFactory) {
		this.userSettingsService = userSettingsService;
		this.llmClientFactory = llmClientFactory;
	}

	@GetMapping
	public UserSettings get() {
		return userSettingsService.get();
	}

	@PutMapping
	public UserSettings put(@RequestBody UserSettings body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			return userSettingsService.save(body);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}

	/**
	 * Saves (upserts) a provider config under {@code id} and health-checks it via LangChain4j.
	 */
	@PostMapping("/providers/{id}/connect")
	public ProviderConnectResponse connect(@PathVariable("id") String id, @RequestBody LlmProviderConfig body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			UserSettings settings = userSettingsService.upsertProvider(id, body);
			HealthStatus health = llmClientFactory.create(settings.requireProvider(id)).healthCheck();
			return new ProviderConnectResponse(settings, health);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}

	/**
	 * Lists models from a configured provider via LangChain4j {@code ModelCatalog}.
	 */
	@GetMapping("/providers/{id}/models")
	public List<ModelInfo> listModels(@PathVariable("id") String id) {
		try {
			return llmClientFactory.create(userSettingsService.get().requireProvider(id)).listModels();
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		} catch (Exception ex) {
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				ex.getMessage() != null ? ex.getMessage() : "Could not list models",
				ex
			);
		}
	}

	/**
	 * Replaces the sub-agent settings: the worker model and the default tool-round budget.
	 * The rest of the settings document is untouched.
	 */
	@PutMapping("/agents")
	public UserSettings putAgents(@RequestBody AgentUpdate body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			UserSettings current = userSettingsService.get();
			return userSettingsService.save(new UserSettings(
				current.providers(), current.activeProviderId(), body.workerModel(), body.defaultMaxRounds()));
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}

	/** Request body for {@link #putAgents}; both fields optional, nulls apply defaults. */
	public record AgentUpdate(AgentModelRef workerModel, Integer defaultMaxRounds) {}
}
