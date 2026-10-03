package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.agent.AgentGuardrails;
import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.agent.SubAgentRunner;
import io.github.iso53.castiel.service.UserSettingsService;
import io.github.iso53.castiel.util.Text;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * The {@code sub_agent} tool: lets the orchestrator delegate a narrow task to a single
 * "worker" sub-agent — a small model whose short system prompt and tool allow-list the
 * orchestrator writes per task. No canned agent types exist.
 *
 * <p>Execution is background, exactly like {@code bg_start}: the call registers the run and
 * returns its id immediately, and the sub-agent keeps working on its own virtual thread while
 * the orchestrator carries on and the user keeps talking to it. The {@code agent_*} family is
 * the read and control surface that replaces the old blocking result: list runs, collect a
 * finished one, kill one. Runs are tracked in the {@link AgentRunManager} registry that feeds
 * the bottom-dock Agents view. Sub-agents can never ask the user questions, spawn further
 * sub-agents, edit raw files, or reach the tracked background-process and agent tools.
 */
@Service
public class SubAgentTool implements ToolProvider {

	public static final String NAME = "sub_agent";

	private final AgentRunManager runs;
	private final SubAgentRunner runner;
	private final UserSettingsService userSettingsService;

	public SubAgentTool(AgentRunManager runs, SubAgentRunner runner, UserSettingsService userSettingsService) {
		this.runs = runs;
		this.runner = runner;
		this.userSettingsService = userSettingsService;
	}

	@Tool(
		name = NAME,
		value = {
			"Delegates a narrow, mechanical task to a sub-agent (a smaller model with its own short",
			"prompt and a restricted toolset) and returns its run id IMMEDIATELY. The sub-agent keeps",
			"working in the background while you carry on with other work and the user keeps talking",
			"to you. It does NOT return the result: collect it later with agent_read, check progress",
			"with agent_list, or stop it with agent_kill.",
			"The sub-agent sees none of this conversation; put everything it needs into the task text",
			"and its systemPrompt: exact targets, constraints, and where to save findings. You write",
			"the systemPrompt (2-6 sentences: role, constraints, output location) and pick",
			"`allowedTools` as the minimal set the task needs. Use it for high-volume work like",
			"enumeration, scanning, OSINT, or document formatting; keep critical reasoning, decisions,",
			"exploitation, and anything needing judgment for yourself. Always tell it to save its",
			"findings into the workspace: its summary is the only thing you get back, so anything it",
			"fails to write down is lost. Every summary is also written to agents/<run-id>.md in the",
			"workspace, so an uncollected result can still be recovered later with read_file.",
			"Sub-agents cannot ask the user questions, spawn further sub-agents, edit files, or",
			"monitor background processes or other sub-agents.",
		}
	)
	public String runSubAgent(
		@P(
			"Self-contained task for the sub-agent: exact targets, constraints, expected output, and where to save findings"
		) String task,
		@P(
			"Short role prompt for the sub-agent, 2-6 sentences: its role, constraints, and where to write output"
		) String systemPrompt,
		@P("Tool names the sub-agent may use (local or MCP). Omit for a safe default set") List<String> allowedTools,
		@P("Optional max tool-call rounds between 2 and 64; default comes from the harness settings") Integer maxRounds
	) {
		if (task == null || task.isBlank()) {
			return "Error: task is required";
		}
		if (systemPrompt == null || systemPrompt.isBlank()) {
			return (
				"Error: systemPrompt is required — write a short role prompt (2-6 sentences:" +
				" role, constraints, where to save output) so the sub-agent knows exactly what it is"
			);
		}

		Set<String> effectiveTools = (allowedTools == null ? Stream.<String>empty() : allowedTools.stream())
			.filter(Objects::nonNull)
			.map(String::strip)
			.filter(s -> !s.isEmpty())
			.filter(s -> !AgentGuardrails.FORBIDDEN_TOOLS.contains(s))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		if (effectiveTools.isEmpty()) {
			effectiveTools = AgentGuardrails.DEFAULT_TOOLS.stream()
				.filter(s -> !AgentGuardrails.FORBIDDEN_TOOLS.contains(s))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		}
		effectiveTools.removeAll(AgentGuardrails.FORBIDDEN_TOOLS);

		int rounds =
			maxRounds == null
				? userSettingsService.get().defaultMaxRounds()
				: (int) Math.clamp(
						maxRounds.longValue(),
						AgentGuardrails.MIN_MAX_ROUNDS,
						AgentGuardrails.MAX_MAX_ROUNDS
					);

		SubAgentRunner.RunSpec spec = new SubAgentRunner.RunSpec(
			AgentGuardrails.WORKER_LABEL,
			userSettingsService.get().workerModel(),
			systemPrompt,
			Set.copyOf(effectiveTools),
			task,
			rounds
		);

		String taskSummary = task.strip().length() <= 120 ? task.strip() : task.strip().substring(0, 117) + "...";
		AgentRunManager.AgentRun run = runs.create(AgentGuardrails.WORKER_LABEL, taskSummary);
		Thread.ofVirtual()
			.name("subagent-" + run.id())
			.start(() -> runner.execute(run, spec));

		return (
			"Started " +
			run.id() +
			" (worker). It runs in the background; collect its result with agent_read, check on it" +
			" with agent_list, or stop it with agent_kill."
		);
	}

	@Tool(
		name = "agent_list",
		value = {
			"Lists the sub-agent runs you started, with id, task, state and elapsed runtime.",
			"Finished runs whose result you have not collected yet are marked UNREAD; collect them",
			"with agent_read. A run you already read stays listed until you remove it.",
		}
	)
	public String list() {
		List<AgentRunManager.AgentRun> tracked = runs.list();
		if (tracked.isEmpty()) {
			return "No sub-agent runs are tracked right now.";
		}
		StringBuilder out = new StringBuilder();
		for (AgentRunManager.AgentRun run : tracked) {
			out.append(run.id())
				.append(" | ")
				.append(Text.runtime(run.runtimeSeconds()))
				.append(" | ")
				.append(run.state().name())
				.append(run.isFinished() && !run.isSeenByAgent() ? " | UNREAD" : "")
				.append('\n')
				.append("    ")
				.append(summarize(run.task()))
				.append('\n');
		}
		return out.toString().stripTrailing();
	}

	@Tool(
		name = "agent_read",
		value = {
			"Returns a finished sub-agent run's result: its status, elapsed runtime, token cost and",
			"the summary it produced. A run that is still working reports its current phase and has",
			"no result yet — check it again later and carry on with other work meanwhile.",
			"Reading a finished run collects it and drops it from tracking, exactly like bg_read, so",
			"read every run whose work you still need.",
		}
	)
	public String read(@P("Run id from sub_agent or agent_list") String runId) {
		AgentRunManager.AgentRun run = require(runId);
		StringBuilder out = new StringBuilder();
		out.append(run.id()).append(" (").append(run.profile()).append(")\n");
		if (!run.isFinished()) {
			out.append("Still ")
				.append(run.state().name().toLowerCase(Locale.ROOT))
				.append(" after ")
				.append(Text.runtime(run.runtimeSeconds()))
				.append(", phase ")
				.append(run.activity().name().toLowerCase(Locale.ROOT))
				.append(". No result yet — check it again later.")
				.append('\n');
			return out.toString();
		}
		out.append(Text.runtime(run.runtimeSeconds()))
			.append(" | ")
			.append(run.state().name())
			.append(" | ")
			.append(run.totalUsage().totalTokenCount())
			.append(" tokens\n\n")
			.append(run.resultSummary());
		if (!run.error().isBlank()) {
			out.append("\n\nError: ").append(run.error());
		}
		runs.markSeen(run.id());
		if (!run.resultPath().isBlank()) {
			out.append("\n\nSaved to ").append(run.resultPath()).append(" in the workspace.");
		}
		// Same contract as bg_read: the result is now in the orchestrator's context, so the run
		// leaves the registry instead of lingering as a row nobody will read again.
		if (runs.remove(run.id())) {
			out.append("\nThis run was removed from tracking; its result is now in your context.");
		}
		return out.toString();
	}

	@Tool(
		name = "agent_kill",
		value = {
			"Cancels a sub-agent run that is still working, including any shell commands it started.",
			"Use it when the task is no longer needed, is going nowhere, or you already have the answer",
			"another way. Finished runs are unaffected.",
		}
	)
	public String kill(@P("Run id from sub_agent or agent_list") String runId) {
		AgentRunManager.AgentRun run = require(runId);
		if (!runs.cancel(run.id())) {
			return run.id() + " is not running (state: " + run.state().name() + ").";
		}
		return (
			"Cancelling " +
			run.id() +
			". It stops at its next step; read it with agent_read afterwards to see how far it got."
		);
	}

	private AgentRunManager.AgentRun require(String id) {
		AgentRunManager.AgentRun run = runs.get(id);
		if (run == null) {
			throw new IllegalArgumentException("No tracked sub-agent run with id " + id + "; check agent_list");
		}
		return run;
	}

	private static String summarize(String task) {
		return task.length() <= 100 ? task : task.substring(0, 97) + "...";
	}
}
