package io.github.iso53.castiel.agent;

import java.util.Set;

// Central sub-agent rules that no tool call can bypass.
public final class AgentGuardrails {

	// Tools no sub-agent may ever receive, regardless of what the orchestrator allows.
	public static final Set<String> FORBIDDEN_TOOLS = Set.of(
		"ask_user_question",
		"sub_agent",
		"edit_file",
		"cvss_score",
		"bg_start",
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

	public static final int DEFAULT_MAX_ROUNDS = 16;
	public static final String WORKER_LABEL = "worker";
	public static final int MIN_MAX_ROUNDS = 2;
	public static final int MAX_MAX_ROUNDS = 32;

	private AgentGuardrails() {}
}
