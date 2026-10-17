package io.github.iso53.castiel.model;

import io.github.iso53.castiel.agent.AgentGuardrails;

import java.util.Map;
import java.util.Set;

/**
 * Full user settings document stored under the OS config directory. MCP servers
 * live in their own mcp.json file is not part of this document.
 *
 * @param providers         Provider id â†’ config (ids are UI-defined, e.g. {@code llama.cpp}).
 * @param activeProviderId  Provider the UI preselects for new chats.
 * @param workerModel       Provider/model sub-agents run on, picked by the user in settings.
 * @param kaliModel         Provider/model the Kali dispatch sub-agent runs on. Unlike the worker
 *                          model there is no fallback: it must be set explicitly.
 * @param defaultMaxRounds  Default tool-round budget for one sub-agent generation (24-256).
 * @param subAgentTimeoutMinutes Wall-clock ceiling for one sub-agent run (1-240). The round
 *                              budget is the real bound; this only stops a provider stream
 *                              that never answers from parking the worker thread.
 * @param pocMinStars       Minimum GitHub stars exploit_search shows public PoCs for (0-1000).
 *                              Low-star PoC repos are frequently noise or outright malware.
 * @param looperModel       Provider/model the looper agent runs on. Must be set before a
 *                          session can be locked.
 * @param looperDirectiveCap Character cap for one looper directive (500-5000).
 * @param looperMaxRounds   Tool-round budget for one looper run (8-64).
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
	Integer verbatimToolCalls,
	Boolean checkForUpdatesOnStartup,
	Integer pocMinStars,
	AgentModelRef looperModel,
	Integer looperDirectiveCap,
	Integer looperMaxRounds
) {

	public static final int DEFAULT_MAX_TOOL_ROUNDS = 256;
	public static final int DEFAULT_CHECKPOINT_INTERVAL_ROUNDS = 32;
	public static final int DEFAULT_VERBATIM_TOOL_CALLS = 32;
	public static final int DEFAULT_SUB_AGENT_TIMEOUT_MINUTES = 60;
	public static final boolean DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP = true;
	public static final int DEFAULT_POC_MIN_STARS = 25;
	public static final int DEFAULT_LOOPER_DIRECTIVE_CAP = 2000;
	public static final int DEFAULT_LOOPER_MAX_ROUNDS = 24;
	public static final int MIN_LOOPER_DIRECTIVE_CAP = 500;
	public static final int MAX_LOOPER_DIRECTIVE_CAP = 5000;
	public static final int MIN_LOOPER_MAX_ROUNDS = 8;
	public static final int MAX_LOOPER_MAX_ROUNDS = 64;

	public UserSettings {
		providers = providers == null ? Map.of() : Map.copyOf(providers);
		workerModel = workerModel == null || workerModel.isBlank() ? AgentModelRef.EMPTY : workerModel;
			kaliModel = kaliModel == null || kaliModel.isBlank() ? AgentModelRef.EMPTY : kaliModel;
		looperModel = looperModel == null || looperModel.isBlank() ? AgentModelRef.EMPTY : looperModel;
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
		checkForUpdatesOnStartup = checkForUpdatesOnStartup == null
			? DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP
			: checkForUpdatesOnStartup;
		pocMinStars = pocMinStars == null
			? DEFAULT_POC_MIN_STARS
			: (int) Math.clamp(pocMinStars.longValue(), 0, 1000);
		looperDirectiveCap = looperDirectiveCap == null
			? DEFAULT_LOOPER_DIRECTIVE_CAP
			: (int) Math.clamp(looperDirectiveCap.longValue(), MIN_LOOPER_DIRECTIVE_CAP, MAX_LOOPER_DIRECTIVE_CAP);
		looperMaxRounds = looperMaxRounds == null
			? DEFAULT_LOOPER_MAX_ROUNDS
			: (int) Math.clamp(looperMaxRounds.longValue(), MIN_LOOPER_MAX_ROUNDS, MAX_LOOPER_MAX_ROUNDS);
	}

	/**
	 * Empty settings: no providers, every limit at its default. The single place a
	 * blank document is built, so a new field needs no edit here.
	 */
	public static UserSettings empty() {
		return new UserSettings(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
			verbatimToolCalls,
			checkForUpdatesOnStartup,
			pocMinStars,
			looperModel,
			looperDirectiveCap,
			looperMaxRounds
		);
	}

	/** Copy with the sub-agent models and round budget replaced. */
	public UserSettings withAgents(
		AgentModelRef workerModel,
		AgentModelRef kaliModel,
		AgentModelRef looperModel,
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
			verbatimToolCalls,
			checkForUpdatesOnStartup,
			pocMinStars,
			looperModel,
			looperDirectiveCap,
			looperMaxRounds
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
			verbatimToolCalls,
			checkForUpdatesOnStartup,
			pocMinStars,
			looperModel,
			looperDirectiveCap,
			looperMaxRounds
		);
	}

	/** Copy with the harness limits replaced (null keeps the current value). */
	public UserSettings withLimits(
		Integer maxToolRounds,
		Integer checkpointIntervalRounds,
		Integer verbatimToolCalls,
		Integer looperDirectiveCap,
		Integer looperMaxRounds
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
			verbatimToolCalls,
			checkForUpdatesOnStartup,
			pocMinStars,
			looperModel,
			looperDirectiveCap,
			looperMaxRounds
		);
	}

	/** Copy with the check-on-startup preference replaced. */
	public UserSettings withUpdatePrefs(boolean checkForUpdatesOnStartup) {
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
			verbatimToolCalls,
			checkForUpdatesOnStartup,
			pocMinStars,
			looperModel,
			looperDirectiveCap,
			looperMaxRounds
		);
	}

	/** Copy with the PoC star threshold replaced. */
	public UserSettings withPocMinStars(Integer pocMinStars) {
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
			verbatimToolCalls,
			checkForUpdatesOnStartup,
			pocMinStars,
			looperModel,
			looperDirectiveCap,
			looperMaxRounds
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
