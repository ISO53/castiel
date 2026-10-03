package io.github.iso53.castiel.model;

import io.github.iso53.castiel.agent.AgentGuardrails;

import java.util.Map;
import java.util.Set;

/**
 * Full user settings document stored under the OS config directory. MCP servers
 * live in their own mcp.json file is not part of this document.
 *
 * @param providers         Provider id → config (ids are UI-defined, e.g. {@code llama.cpp}).
 * @param activeProviderId  Provider the UI preselects for new chats.
 * @param workerModel       Provider/model sub-agents run on, picked by the user in settings.
 * @param kaliModel         Provider/model the Kali dispatch sub-agent runs on. Unlike the worker
 *                          model there is no fallback: it must be set explicitly.
 * @param defaultMaxRounds  Default tool-round budget for one sub-agent generation (2-64).
 * @param subAgentTimeoutMinutes Wall-clock ceiling for one sub-agent run (1-240). The round
 *                              budget is the real bound; this only stops a provider stream
 *                              that never answers from parking the worker thread.
 */
public record UserSettings(
	Map<String, LlmProviderConfig> providers,
	String activeProviderId,
	AgentModelRef workerModel,
	AgentModelRef kaliModel,
	Integer defaultMaxRounds,
	Integer subAgentTimeoutMinutes,
	Set<String> disabledToolGroups,
	Integer maxToolRounds,
	Integer checkpointIntervalRounds,
	Integer verbatimToolCalls
) {

	public static final int DEFAULT_MAX_TOOL_ROUNDS = 256;
	public static final int DEFAULT_CHECKPOINT_INTERVAL_ROUNDS = 32;
	public static final int DEFAULT_VERBATIM_TOOL_CALLS = 32;
	public static final int DEFAULT_SUB_AGENT_TIMEOUT_MINUTES = 30;

	public UserSettings {
		providers = providers == null ? Map.of() : Map.copyOf(providers);
		workerModel = workerModel == null || workerModel.isBlank() ? AgentModelRef.EMPTY : workerModel;
			kaliModel = kaliModel == null || kaliModel.isBlank() ? AgentModelRef.EMPTY : kaliModel;
		defaultMaxRounds = defaultMaxRounds == null
			? AgentGuardrails.DEFAULT_MAX_ROUNDS
			: (int) Math.clamp(defaultMaxRounds.longValue(), AgentGuardrails.MIN_MAX_ROUNDS, AgentGuardrails.MAX_MAX_ROUNDS);
		subAgentTimeoutMinutes = subAgentTimeoutMinutes == null
			? DEFAULT_SUB_AGENT_TIMEOUT_MINUTES
			: (int) Math.clamp(
					subAgentTimeoutMinutes.longValue(),
					AgentGuardrails.MIN_TIMEOUT_MINUTES,
					AgentGuardrails.MAX_TIMEOUT_MINUTES
			);
		disabledToolGroups = disabledToolGroups == null ? Set.of() : Set.copyOf(disabledToolGroups);
		maxToolRounds = maxToolRounds == null ? DEFAULT_MAX_TOOL_ROUNDS : Math.max(1, maxToolRounds);
		checkpointIntervalRounds = checkpointIntervalRounds == null
			? DEFAULT_CHECKPOINT_INTERVAL_ROUNDS
			: Math.max(1, checkpointIntervalRounds);
		verbatimToolCalls = verbatimToolCalls == null ? DEFAULT_VERBATIM_TOOL_CALLS : Math.max(0, verbatimToolCalls);
	}

	/**
	 * Empty settings: no providers, every limit at its default. The single place a
	 * blank document is built, so a new field needs no edit here.
	 */
	public static UserSettings empty() {
		return new UserSettings(null, null, null, null, null, null, null, null, null, null);
	}

	/** Copy with a replaced provider map and UI default. */
	public UserSettings withProviders(Map<String, LlmProviderConfig> providers, String activeProviderId) {
		return new UserSettings(
			providers,
			activeProviderId,
			workerModel,
			kaliModel,
			defaultMaxRounds,
			subAgentTimeoutMinutes,
			disabledToolGroups,
			maxToolRounds,
			checkpointIntervalRounds,
			verbatimToolCalls
		);
	}

	/** Copy with the sub-agent models and round budget replaced. */
	public UserSettings withAgents(
		AgentModelRef workerModel,
		AgentModelRef kaliModel,
		Integer defaultMaxRounds,
		Integer subAgentTimeoutMinutes
	) {
		return new UserSettings(
			providers,
			activeProviderId,
			workerModel,
			kaliModel,
			defaultMaxRounds,
			subAgentTimeoutMinutes,
			disabledToolGroups,
			maxToolRounds,
			checkpointIntervalRounds,
			verbatimToolCalls
		);
	}

	/** Copy with the disabled tool groups replaced. */
	public UserSettings withDisabledGroups(Set<String> disabledToolGroups) {
		return new UserSettings(
			providers,
			activeProviderId,
			workerModel,
			kaliModel,
			defaultMaxRounds,
			subAgentTimeoutMinutes,
			disabledToolGroups,
			maxToolRounds,
			checkpointIntervalRounds,
			verbatimToolCalls
		);
	}

	/** Copy with the harness limits replaced (null keeps the current value). */
	public UserSettings withLimits(Integer maxToolRounds, Integer checkpointIntervalRounds, Integer verbatimToolCalls) {
		return new UserSettings(
			providers,
			activeProviderId,
			workerModel,
			kaliModel,
			defaultMaxRounds,
			subAgentTimeoutMinutes,
			disabledToolGroups,
			maxToolRounds,
			checkpointIntervalRounds,
			verbatimToolCalls
		);
	}

	public LlmProviderConfig requireProvider(String providerId) {
		LlmProviderConfig config = providers.get(providerId);
		if (config == null) {
			throw new IllegalArgumentException("Unknown provider: " + providerId);
		}
		return config;
	}
}
