package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.TokenUsage;
import io.github.iso53.castiel.agent.AgentGuardrails;
import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.agent.SubAgentRunner;
import io.github.iso53.castiel.service.HarnessService;
import io.github.iso53.castiel.service.UserSettingsService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * The {@code sub_agent} tool: lets the orchestrator delegate a narrow task to a single
 * "worker" sub-agent — a small model whose short system prompt and tool allow-list the
 * orchestrator writes per task. No canned agent types exist.
 *
 * <p>Execution is foreground — the tool result carries the sub-agent's final summary. But
 * the run itself executes on its own virtual thread and is tracked in the
 * {@link AgentRunManager} registry that feeds the bottom-dock Agents view. Sub-agents can
 * never ask the user questions, spawn further sub-agents, edit raw files, or use the
 * tracked background-process tools.
 */
@Service
public class SubAgentTool implements ToolProvider {

	public static final String NAME = "sub_agent";

	private static final long JOIN_TIMEOUT_MS = 31 * 60 * 1000L;

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
			"prompt and a restricted toolset) and returns its result. The sub-agent sees none of",
			"this conversation; put everything it needs into the task text and its systemPrompt:",
			"exact targets, constraints, and where to save findings. You write the systemPrompt",
			"(2-6 sentences: role, constraints, output location) and pick `allowedTools` as the",
			"minimal set the task needs. Use it for high-volume work like enumeration, scanning,",
			"OSINT, or document formatting; keep critical reasoning, decisions, exploitation, and",
			"anything needing judgment for yourself. Sub-agents cannot ask the user questions,",
			"spawn further sub-agents, edit files, or use the bg_* background-process tools.",
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
		@P("Optional max tool-call rounds between 2 and 32; default comes from the harness settings") Integer maxRounds
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
			systemPrompt,
			Set.copyOf(effectiveTools),
			task,
			rounds
		);

		String taskSummary = task.strip().length() <= 120 ? task.strip() : task.strip().substring(0, 117) + "...";
		AgentRunManager.AgentRun run = runs.create(
			HarnessService.currentGenerationId(),
			AgentGuardrails.WORKER_LABEL,
			taskSummary
		);

		// Pre-filled so a crashed or timed-out worker thread still renders as a failed run.
		SubAgentRunner.SubAgentOutcome notFinished = new SubAgentRunner.SubAgentOutcome(
			"failed",
			"the sub-agent did not finish within its time limit and was cancelled",
			AgentRunManager.AgentRun.NO_TRANSCRIPT,
			new TokenUsage(0, 0, 0),
			-1
		);
		AtomicReference<SubAgentRunner.SubAgentOutcome> outcome = new AtomicReference<>(notFinished);
		Thread worker = Thread.ofVirtual()
			.name("subagent-" + run.id())
			.start(() -> outcome.set(runner.execute(run, spec)));
		try {
			worker.join(JOIN_TIMEOUT_MS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			runs.cancel(run.id());
			return "Error: interrupted while the sub-agent ran; it was cancelled";
		}

		SubAgentRunner.SubAgentOutcome result = outcome.get();
		if (result == notFinished) {
			runs.cancel(run.id());
		}

		StringBuilder answer = new StringBuilder();
		answer
			.append("Sub-agent ")
			.append(run.id())
			.append(" (worker) finished with status ")
			.append(result.status())
			.append(" after ")
			.append(result.rounds())
			.append(" round(s), ")
			.append(result.usage().totalTokenCount())
			.append(" tokens.\n\n")
			.append(result.summary());
		if (!result.transcriptPath().toString().isEmpty()) {
			answer.append("\n\nFull transcript saved to: ").append(result.transcriptPath());
		}
		return answer.toString();
	}
}
