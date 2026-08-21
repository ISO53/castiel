package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.HealthStatus;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;
import io.github.iso53.castiel.model.ProviderConnectResponse;
import io.github.iso53.castiel.model.UserSettings;
import io.github.iso53.castiel.service.LlmClientFactory;
import io.github.iso53.castiel.service.UserSettingsService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
			HealthStatus health = llmClientFactory.healthCheck(settings.requireProvider(id));
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
			return llmClientFactory.listModels(userSettingsService.get().requireProvider(id));
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
}
