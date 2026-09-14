package io.github.iso53.castiel.model;

import io.github.iso53.castiel.agent.AgentGuardrails;

import java.util.Map;

/**
 * Full user settings document stored under the OS config directory. MCP servers
 * live in their own mcp.json file is not part of this document.
 *
 * @param providers         Provider id → config (ids are UI-defined, e.g. {@code llama.cpp}).
 * @param activeProviderId  Provider the UI preselects for new chats.
 * @param workerModel       Provider/model sub-agents run on, picked by the user in settings.
 * @param defaultMaxRounds  Default tool-round budget for one sub-agent generation (2–32).
 */
public record UserSettings(
	Map<String, LlmProviderConfig> providers,
	String activeProviderId,
	AgentModelRef workerModel,
	Integer defaultMaxRounds
) {

	public UserSettings {
		providers = providers == null ? Map.of() : Map.copyOf(providers);
		workerModel = workerModel == null || workerModel.isBlank() ? AgentModelRef.EMPTY : workerModel;
		defaultMaxRounds = defaultMaxRounds == null
			? AgentGuardrails.DEFAULT_MAX_ROUNDS
			: (int) Math.clamp(defaultMaxRounds.longValue(), AgentGuardrails.MIN_MAX_ROUNDS, AgentGuardrails.MAX_MAX_ROUNDS);
	}

	public LlmProviderConfig requireProvider(String providerId) {
		LlmProviderConfig config = providers.get(providerId);
		if (config == null) {
			throw new IllegalArgumentException("Unknown provider: " + providerId);
		}
		return config;
	}
}
