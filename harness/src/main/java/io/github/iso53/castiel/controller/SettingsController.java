package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.*;
import io.github.iso53.castiel.service.ClineOAuthService;
import io.github.iso53.castiel.service.LlmClientFactory;
import io.github.iso53.castiel.service.UserSettingsService;
import io.github.iso53.castiel.tool.ToolCatalog;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Persisted user settings and provider connectivity checks.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

	private final UserSettingsService userSettingsService;
	private final ToolCatalog toolCatalog;
	private final LlmClientFactory llmClientFactory;
	private final ClineOAuthService clineOAuthService;

	public SettingsController(
		UserSettingsService userSettingsService,
		ToolCatalog toolCatalog,
		LlmClientFactory llmClientFactory,
		ClineOAuthService clineOAuthService
	) {
		this.userSettingsService = userSettingsService;
		this.toolCatalog = toolCatalog;
		this.llmClientFactory = llmClientFactory;
		this.clineOAuthService = clineOAuthService;
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

	// Only Cline supports account sign-in today; other providers get a clear rejection.
	private void requireOAuthCapable(String providerId) {
		if (!"cline".equalsIgnoreCase(providerId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider does not support account sign-in");
		}
	}

	/**
	 * Starts an OAuth device sign-in: returns the code to enter in the browser and the URL to open.
	 */
	@PostMapping("/providers/{id}/oauth/start")
	public ClineOAuthStartResponse startOAuth(@PathVariable("id") String id) {
		requireOAuthCapable(id);
		try {
			return clineOAuthService.start();
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
		}
	}

	/**
	 * Reports the sign-in state; on {@code AUTHORIZED} the provider entry is already saved and a
	 * health check is included.
	 */
	@GetMapping("/providers/{id}/oauth/status")
	public ClineOAuthStatusResponse statusOAuth(@PathVariable("id") String id) {
		requireOAuthCapable(id);
		ClineOAuthStatusResponse result = clineOAuthService.poll();
		if (result.status() == ClineOAuthStatusResponse.State.AUTHORIZED) {
			HealthStatus health = llmClientFactory.create(result.settings().requireProvider(id)).healthCheck();
			result = result.withHealth(health);
		}
		return result;
	}

	/**
	 * Signs out: removes the OAuth-backed provider entry entirely.
	 */
	@PostMapping("/providers/{id}/oauth/disconnect")
	public UserSettings disconnectOAuth(@PathVariable("id") String id) {
		requireOAuthCapable(id);
		return userSettingsService.removeProvider(id);
	}

	/**
	 * Replaces the sub-agent settings: the worker model, the Kali model and the
	 * default tool-round budget. The rest of the document is untouched.
	 */
	@PutMapping("/agents")
	public UserSettings putAgents(@RequestBody AgentUpdate body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			UserSettings current = userSettingsService.get();
			return userSettingsService.save(
				new UserSettings(
					current.providers(),
					current.activeProviderId(),
					body.workerModel(),
					body.kaliModel(),
					body.defaultMaxRounds(),
					current.disabledToolGroups(),
					Objects.requireNonNullElse(body.maxToolRounds(), current.maxToolRounds()),
					Objects.requireNonNullElse(body.checkpointIntervalRounds(), current.checkpointIntervalRounds()),
					Objects.requireNonNullElse(body.verbatimToolCalls(), current.verbatimToolCalls())
				)
			);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}

	/** Request body for {@link #putAgents}; both fields optional, nulls apply defaults. */
	public record AgentUpdate(
		AgentModelRef workerModel,
		AgentModelRef kaliModel,
		Integer defaultMaxRounds,
		Integer maxToolRounds,
		Integer checkpointIntervalRounds,
		Integer verbatimToolCalls
	) {}

	/**
	 * Lists the harness tool groups with their current state, so the UI never hardcodes
	 * a list that the backend already owns.
	 */
	@GetMapping("/tools")
	public List<ToolGroupView> listTools() {
		Set<String> disabled = userSettingsService.get().disabledToolGroups();
		return toolCatalog.groups().stream()
			.map(group -> new ToolGroupView(
				group.id(),
				group.label(),
				group.description(),
				group.tools(),
				group.locked() || !disabled.contains(group.id()),
				group.locked(),
				group.critical()
			))
			.toList();
	}

	/**
	 * Replaces the set of disabled tool groups. Only the ids the catalogue recognises as
	 * toggleable are stored; locked groups can never be switched off from here.
	 */
	@PutMapping("/tools")
	public UserSettings putTools(@RequestBody ToolUpdate body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		try {
			return userSettingsService.setDisabledToolGroups(body.disabledGroups(), toolCatalog);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}

	/** Request body for {@link #putTools}. */
	public record ToolUpdate(Set<String> disabledGroups) {}

	/** One tool group as the settings UI needs it. Locked groups carry no live switch. */
	public record ToolGroupView(
		String id,
		String label,
		String description,
		List<String> tools,
		boolean enabled,
		boolean locked,
		boolean critical
	) {}
}
