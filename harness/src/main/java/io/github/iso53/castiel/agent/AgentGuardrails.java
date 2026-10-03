package io.github.iso53.castiel.agent;

import java.util.Set;

// Central sub-agent rules that no tool call can bypass.
public final class AgentGuardrails {

	// Tools no sub-agent may ever receive, regardless of what the orchestrator allows.
	// agent_* is here for the same reason bg_* is: a sub-agent may not inspect, collect
	// or kill runs other than its own, or it would be able to steer its siblings.
	public static final Set<String> FORBIDDEN_TOOLS = Set.of(
		"ask_user_question",
		"sub_agent",
		"agent_list",
		"agent_read",
		"agent_kill",
		"edit_file",
		"cvss_score",
		"bg_list",
		"bg_read",
		"bg_send",
		"bg_kill"
	);

	// Toolset used when the orchestrator does not specify one: mechanical, read/write basics.
	public static final Set<String> DEFAULT_TOOLS = Set.of(
		"bash",
		"read_file",
		"write_file",
		"web_fetch",
		"web_search",
		"workspace_search"
	);

	public static final int DEFAULT_MAX_ROUNDS = 32;
	public static final String WORKER_LABEL = "worker";
	public static final int MIN_MAX_ROUNDS = 2;
	public static final int MAX_MAX_ROUNDS = 64;
	public static final int MIN_TIMEOUT_MINUTES = 1;
	public static final int MAX_TIMEOUT_MINUTES = 240;

	private AgentGuardrails() {}
}
