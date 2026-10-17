package io.github.iso53.castiel.agent;

import java.util.Set;

// Central sub-agent rules that no tool call can bypass.
public final class AgentGuardrails {

	// Tools no sub-agent may ever receive, regardless of what the orchestrator allows.
	// agent_* is here for the same reason sub_agent is: a sub-agent may not inspect,
	// collect or steer runs other than its own. Background-process tools ARE allowed,
	// but ownership-scoped to the run that started each process.
	public static final Set<String> FORBIDDEN_TOOLS = Set.of(
		"ask_user_question",
		"sub_agent",
		"agent_list",
		"agent_read",
		"agent_kill",
		"cvss_score"
	);

	// Label of the harness-driven looper run; also its profile name in the dock.
	public static final String LOOPER_LABEL = "looper";

	// The looper may watch sub-agent runs (agent_list) but nothing else from the forbidden set.
	public static final Set<String> LOOPER_FORBIDDEN_TOOLS = FORBIDDEN_TOOLS.stream()
		.filter(name -> !"agent_list".equals(name))
		.collect(java.util.stream.Collectors.toUnmodifiableSet());

	// Toolset used when the orchestrator does not specify one: mechanical, read/write basics.
	public static final Set<String> DEFAULT_TOOLS = Set.of(
		"bash",
		"read_file",
		"write_file",
		"edit_file",
		"web_fetch",
		"web_search",
		"workspace_search",
		"bg_start",
		"bg_list",
		"bg_read",
		"bg_send",
		"bg_kill"
	);

	public static final int DEFAULT_MAX_ROUNDS = 128;
	public static final String WORKER_LABEL = "worker";
	public static final int MIN_MAX_ROUNDS = 24;
	public static final int MAX_MAX_ROUNDS = 256;
	public static final int MIN_TIMEOUT_MINUTES = 1;
	public static final int MAX_TIMEOUT_MINUTES = 240;

	private AgentGuardrails() {}
}
