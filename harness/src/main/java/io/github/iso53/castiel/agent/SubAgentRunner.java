package io.github.iso53.castiel.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecutor;
import io.github.iso53.castiel.model.AgentModelRef;
import io.github.iso53.castiel.provider.GenerationOptions;
import io.github.iso53.castiel.provider.LlmProvider;
import io.github.iso53.castiel.service.*;
import io.github.iso53.castiel.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs one sub-agent generation: a short prompt, a filtered toolset, and a small model,
 * driven through the same tool-calling discipline as the orchestrator loop.
 *
 * <p>A run is background: it executes on its own virtual thread and outlives the orchestrator
 * turn that started it, so the caller never blocks and never sees the outcome directly — it
 * reads the result back out of the {@link AgentRunManager} registry. Each round streams from
 * the model, but the tokens are discarded: the run reports only the phase it is in, plus a
 * final summary, so the bottom-dock Agents view stays a monitor rather than a second
 * transcript viewer.
 */
@Service
public class SubAgentRunner {

	private static final Logger log = LoggerFactory.getLogger(SubAgentRunner.class);

	// Result text kept for the orchestrator's context; it is also the run's summary in the dock.
	private static final int RESULT_CHAR_CAP = 4_000;

	// Scope/context excerpt capped before it reaches the sub-agent's prompt.
	private static final int CONTEXT_CHAR_CAP = 2_000;

	/** Notice appended to a sub-agent result that had to be cut. */
	private static final String CAP_NOTICE = "\n[harness: truncated at %d of %d characters]";

	private static final ObjectMapper JSON = new ObjectMapper();

	// Round counts left at which the run gets a budget warning; each fires once.
	private static final int[] WARNING_LEFTS = {32, 16, 4};

	// The first line of a run's final reply must be exactly this marker
	private static final String FINAL_MARKER = "DONE";

	// Free reminder rounds a run may spend on producing a valid final answer
	private static final int MAX_FINAL_RETRIES = 3;

	/**
	 * Where run summaries are written inside a workspace. Deliberately not {@code evidence/}:
	 * a summary is a work log, not captured material, and that folder is contractually limited
	 * to artifacts registered in {@code evidence.json}.
	 */
	private static final String AGENTS_FOLDER = "agents";

	private final ObjectProvider<HarnessService> harnessProvider;
	private final LlmClientFactory llmClientFactory;
	private final UserSettingsService userSettingsService;
	private final AgentRunManager runs;
	private final WorkspaceSession workspace;
	private final EngagementService engagement;

	/**
	 * The harness is looked up lazily: the chat pipeline discovers this runner through the
	 * {@code sub_agent} tool it registers, so an eager dependency would be a bean cycle.
	 */
	public SubAgentRunner(
		ObjectProvider<HarnessService> harnessProvider,
		LlmClientFactory llmClientFactory,
		UserSettingsService userSettingsService,
		AgentRunManager runs,
		WorkspaceSession workspace,
		EngagementService engagement
	) {
		this.harnessProvider = harnessProvider;
		this.llmClientFactory = llmClientFactory;
		this.userSettingsService = userSettingsService;
		this.runs = runs;
		this.workspace = workspace;
		this.engagement = engagement;
	}

	// Everything one sub-agent generation needs, resolved by the {@code sub_agent} tool.
	public record RunSpec(
		String label,
		AgentModelRef model,
		String systemPrompt,
		Set<String> allowedTools,
		String task,
		int maxRounds
	) {
		public RunSpec {
			label = label == null || label.isBlank() ? AgentGuardrails.WORKER_LABEL : label.strip();
			model = model == null ? AgentModelRef.EMPTY : model;
			systemPrompt = systemPrompt == null ? "" : systemPrompt.strip();
			allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
			task = task == null ? "" : task.strip();
		}
	}

	/**
	 * Writes the run's final summary to {@code <workspace>/agents/<run-id>.md}, with a short
	 * provenance header. Best-effort: a run is already finished at this point, so a failed
	 * write must never turn a good result into a failed one.
	 */
	private void persistSummary(AgentRunManager.AgentRun run, RunSpec spec, String summary) {
		Path root = workspace.root().orElse(null);
		if (root == null) {
			log.debug("No workspace open; run {} summary was not persisted", run.id());
			return;
		}
		try {
			Path dir = root.resolve(AGENTS_FOLDER);
			Files.createDirectories(dir);
			Path target = dir.resolve(run.id() + ".md");
			Files.writeString(target, summaryDocument(run, spec, summary), StandardCharsets.UTF_8);
			run.setResultPath(AGENTS_FOLDER + "/" + target.getFileName());
			log.info("Persisted run {} summary to {}", run.id(), target);
		} catch (IOException | RuntimeException ex) {
			log.warn("Could not persist the summary for run {}: {}", run.id(), ex.getMessage());
		}
	}

	/** The persisted file: a provenance header, then the summary exactly as the model wrote it. */
	private String summaryDocument(AgentRunManager.AgentRun run, RunSpec spec, String summary) {
		return (
			"# Sub-agent run " +
			run.id() +
			"\n\n" +
			"- Kind: " +
			spec.label() +
			"\n" +
			"- Status: " +
			run.state().name() +
			"\n" +
			"- Started: " +
			run.startedAt() +
			"\n" +
			"- Duration: " +
			Text.runtime(run.runtimeSeconds()) +
			"\n" +
			"- Tokens: " +
			run.totalUsage().totalTokenCount() +
			"\n\n" +
			"## Task\n\n" +
			spec.task() +
			"\n\n" +
			"## Summary\n\n" +
			summary +
			"\n"
		);
	}

	// Mutable accumulator for one executed run.
	private record Outcome(
		String status,
		String error,
		String summary,
		TokenUsage usage,
		int rounds
	) {}

	/**
	 * Executes {@code spec} on behalf of {@code run} on the calling thread, returning only
	 * once the sub-agent finishes, the wall clock expires, or the run is cancelled. Always
	 * leaves the run in a terminal state; the caller reads the result off the run itself,
	 * which is why there is nothing to return.
	 */
	public void execute(AgentRunManager.AgentRun run, RunSpec spec) {
		long startNanos = System.nanoTime();
		run.setState(AgentRunManager.State.RUNNING);
		runs.notifyChange();
		// Tool shells spawned by this run register under the run id, so cancelling the
		// run (from the dock, or via the orchestrator's cancel fan-out) tree-kills them.
		HarnessService.setCurrentGenerationId(run.id());
		try {
			StreamingChatModel model = resolveModel(spec);
			List<ChatMessage> messages = buildMessages(spec);
			// The looper run is a harness-internal agent: it may watch sub-agent runs.
			HarnessService.AgentToolset toolset = AgentGuardrails.LOOPER_LABEL.equals(spec.label())
				? harnessProvider.getObject().looperToolsFor(spec.allowedTools())
				: harnessProvider.getObject().toolsFor(spec.allowedTools());
			log.info(
				"Run {} ({}) starting with tools {} and a budget of {} rounds",
				run.id(),
				spec.label(),
				toolset.specifications().stream().map(ToolSpecification::name).toList(),
				spec.maxRounds()
			);
			if (toolset.specifications().isEmpty()) {
				// Nothing to call means the loop can only ever return an empty answer.
				log.warn("Run {} has no usable tools after filtering {}", run.id(), spec.allowedTools());
			}
			Outcome loop = loop(run, spec, model, messages, toolset);
			run.setTotalUsage(loop.usage());
			String summary = summaryOrTemplate(loop);
			run.setResultSummary(Text.truncate(summary, RESULT_CHAR_CAP, CAP_NOTICE));
			AgentRunManager.State state = switch (loop.status()) {
				case "done" -> AgentRunManager.State.DONE;
				case "cancelled" -> AgentRunManager.State.CANCELLED;
				default -> AgentRunManager.State.FAILED;
			};
			run.setState(state);
			run.setError(loop.error());
			// The summary only lives in the registry until agent_read collects it, and the
			// registry is capped and evicted. Write it to the workspace so a result the
			// orchestrator never gets around to reading is still there afterwards.
			persistSummary(run, spec, summary);
			log.info("Run {} ended as {} after {} rounds", run.id(), state, loop.rounds());
			return;
		} catch (Throwable ex) {
			// Throwable, not Exception: an Error here would otherwise escape and strand the run
			// in RUNNING with a dead thread, leaving the cancel button with nothing to stop.
			String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			log.warn("Sub-agent run {} failed: {}", run.id(), message, ex);
			run.setError(message);
			run.setState(AgentRunManager.State.FAILED);
			return;
		} finally {
			HarnessService.setCurrentGenerationId(null);
			// Last resort: never leave a run marked running once its thread is gone.
			if (!run.isFinished()) {
				log.warn("Sub-agent run {} ended without a terminal state; marking it failed", run.id());
				run.setState(AgentRunManager.State.FAILED);
			}
			runs.notifyChange();
			log.info(
				"Sub-agent run {} finished in {} ms",
				run.id(),
				Duration.ofNanos(System.nanoTime() - startNanos).toMillis()
			);
		}
	}

	// The tool-calling loop, bounded by rounds, wall clock, and the run's cancelled flag.
	private Outcome loop(
		AgentRunManager.AgentRun run,
		RunSpec spec,
		StreamingChatModel model,
		List<ChatMessage> messages,
		HarnessService.AgentToolset toolset
	) {
		// Wall-clock ceiling on the whole run, from the user's Settings > Sub-agents value.
		// The round budget is the real bound; this only guarantees a provider stream that
		// never answers cannot park this thread forever. A run is background now, so nothing
		// else observes the thread — this deadline is what surfaces a hung model as FAILED.
		Duration maxRuntime = Duration.ofMinutes(userSettingsService.get().subAgentTimeoutMinutes());
		long deadlineNanos = System.nanoTime() + maxRuntime.toNanos();
		List<ChatMessage> thread = new ArrayList<>(messages);
		TokenUsage total = new TokenUsage(0, 0, 0);
		int round = 0;
		// Index into WARNING_LEFTS: the next budget warning still to fire.
		int warned = 0;
		// Free reminder rounds spent so far on collecting a valid final answer.
		int nudges = 0;

		rounds: while (true) {
			if (run.cancelled().get()) {
				log.info("Run {} stopped before round {}: cancel was requested", run.id(), round + 1);
				return new Outcome("cancelled", "", "", total, round);
			}
			long remainingNanos = deadlineNanos - System.nanoTime();
			if (remainingNanos <= 0) {
				log.warn("Run {} stopped at round {}: wall-clock limit reached", run.id(), round + 1);
				return new Outcome(
					"failed",
					"wall-clock limit of " + maxRuntime.toMinutes() + " min reached",
					"",
					total,
					round
				);
			}

			// Opened the next round: the previous phase is over, so say so before the model
			// starts producing. Covers the gap between a tool returning and the first token.
			enterActivity(run, AgentRunManager.Activity.WORKING);

			// Budget warning fires once when this many rounds remain after this round.
			// Task rounds only: the free delivery rounds after the budget never warn.
			int left = spec.maxRounds() - round - 1;
			if (round < spec.maxRounds() && warned < WARNING_LEFTS.length && left <= WARNING_LEFTS[warned]) {
				thread.add(UserMessage.from(left <= WARNING_LEFTS[2]
					? "[harness] Only " + left + " round(s) left. Stop exploring, save your findings, "
						+ "and write your final summary now."
					: "[harness] " + left + " of your " + spec.maxRounds() + " rounds remain. Prioritize "
						+ "your goal and start saving findings to the workspace."));
				warned++;
			}

			ChatRequest request = ChatRequest.builder()
				.messages(thread)
				.toolSpecifications(toolset.specifications())
				.build();
			Round roundResult;
			try {
				roundResult = blockingRound(model, request, run, Duration.ofNanos(remainingNanos));
			} catch (Exception ex) {
				if (run.cancelled().get()) {
					log.info("Run {} stopped during round {}: cancel was requested", run.id(), round + 1);
					return new Outcome("cancelled", "", "", total, round);
				}
				String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
				// The message alone hides provider errors; the trace explains them in debug runs.
				log.warn("Run {} failed during round {}: {}", run.id(), round + 1, message, ex);
				return new Outcome("failed", message, "", total, round);
			}
			total = TokenUsage.sum(total, roundResult.usage());

			AiMessage ai = roundResult.message();
			// Past the task budget nothing executes anymore: a delivery round exists only
			// to collect the final answer, so tool calls there count as a missing one.
			boolean deliveryOnly = round >= spec.maxRounds();
			if (!ai.hasToolExecutionRequests() || deliveryOnly) {
				String text = ai.hasToolExecutionRequests() ? "" : ai.text();
				if (isValidFinal(text)) {
					log.info("Run {} finished in round {}: valid final answer", run.id(), round + 1);
					return new Outcome("done", "", text, total, round + 1);
				}
				if (nudges < MAX_FINAL_RETRIES) {
					nudges++;
					log.info(
						"Run {} round {} came back without a valid final answer; reminder {} of {} sent",
						run.id(),
						round + 1,
						nudges,
						MAX_FINAL_RETRIES
					);
					// An invalid final message never enters the thread: some providers reject
					// an assistant message with neither text nor tool calls, and the re-asked
					// summary replaces it anyway.
					thread.add(UserMessage.from(deliveryOnly ? forcedSummaryNudge(spec) : finalAnswerNudge()));
					round++;
					continue;
				}
				log.warn(
					"Run {} refused to produce a valid final answer: {} reminder(s) exhausted",
					run.id(),
					MAX_FINAL_RETRIES
				);
				return new Outcome(
					"done",
					"the sub-agent refused to end with the " + FINAL_MARKER
						+ " protocol: no valid final answer after " + MAX_FINAL_RETRIES + " reminder(s)",
					"(the sub-agent refused to deliver a final answer: it was reminded "
						+ MAX_FINAL_RETRIES
						+ " time(s) to reply with "
						+ FINAL_MARKER
						+ " and a summary, and still did not. Its tool work and any files it saved"
						+ " are still in the workspace; see the agents/ folder and the workspace"
						+ " files it created for what it did.)",
					total,
					round + 1
				);
			}

			thread.add(ai);
			enterActivity(run, AgentRunManager.Activity.TOOL_CALLING);
			for (ToolExecutionRequest toolRequest : ai.toolExecutionRequests()) {
				log.debug(
					"Run {} round {} calling tool {} with {}",
					run.id(),
					round + 1,
					toolRequest.name(),
					toolRequest.arguments()
				);
				String result = executeTool(toolset, toolRequest);
				log.debug("Run {} round {} tool {} returned: {}", run.id(), round + 1, toolRequest.name(), firstLine(result));
				// Only the run's summary is exposed; tool chatter lives in the debug log above.
				thread.add(ToolExecutionResultMessage.from(toolRequest, result));
				if (run.cancelled().get()) {
					log.info("Run {} stopped after round {}: cancel was requested", run.id(), round + 1);
					return new Outcome("cancelled", "", "", total, round + 1);
				}
				// The run signalled it has nothing left to do: ask for the summary instead of
				// silently discarding whatever it meant to report.
				if (run.isStopRequested()) {
					if (nudges < MAX_FINAL_RETRIES) {
						nudges++;
						log.info(
							"Run {} stopped on request at round {}; asking for its final summary",
							run.id(),
							round + 1
						);
						thread.add(UserMessage.from(stopRequestedNudge()));
						round++;
						continue rounds;
					}
					log.warn("Run {} stopped on request at round {} with no reminders left", run.id(), round + 1);
					return new Outcome(
						"done",
						"",
						"The run finished its tool work and stopped on request without a valid final answer.",
						total,
						round + 1
					);
				}
			}
			round++;
			// The budget just ended and the model is still calling tools: hand it a free
			// delivery round instead of ending with a canned partial-work note.
			if (round >= spec.maxRounds() && nudges < MAX_FINAL_RETRIES) {
				nudges++;
				log.info(
					"Run {} used its budget of {} rounds while still calling tools; asking for its final summary",
					run.id(),
					spec.maxRounds()
				);
				thread.add(UserMessage.from(forcedSummaryNudge(spec)));
			}
		}
	}

	// Result of one model round: the (possibly tool-calling) message and its token usage.
	private record Round(AiMessage message, TokenUsage usage) {}

	// Collapses a tool result to its first line, so a debug line stays readable.
	private static String firstLine(String text) {
		int newline = text.indexOf('\n');
		return (newline < 0 ? text : text.substring(0, newline)).strip();
	}

	// A final answer is valid when its first non-blank line is the marker alone.
	private static boolean isValidFinal(String text) {
		if (text == null) {
			return false;
		}
		for (String line : text.split("\n")) {
			String stripped = line.strip();
			if (stripped.isEmpty()) {
				continue;
			}
			return FINAL_MARKER.equalsIgnoreCase(stripped);
		}
		return false;
	}

	// Reminder after a text reply that broke the final-answer protocol (blank or no marker).
	private static String finalAnswerNudge() {
		return (
			"[harness] Your last reply was not a valid final answer. Reply now with "
				+ FINAL_MARKER
				+ " alone on the first line, then your final summary in plain text: what you"
				+ " worked on, what you did, what failed, and where you saved findings."
				+ " Do not call any tools."
		);
	}

	// Reminder when the round budget ended while the model was still calling tools.
	private static String forcedSummaryNudge(RunSpec spec) {
		return (
			"[harness] You used your full tool-round budget of "
				+ spec.maxRounds()
				+ ". Reply now with "
				+ FINAL_MARKER
				+ " alone on the first line, then your final summary in plain text: what you"
				+ " worked on, what you did, what failed, and where you saved findings."
				+ " Do not call any tools."
		);
	}

	// Reminder after the run asked to stop mid-tools.
	private static String stopRequestedNudge() {
		return (
			"[harness] You asked to stop. Reply now with "
				+ FINAL_MARKER
				+ " alone on the first line, then your final summary in plain text."
				+ " Do not call any tools."
		);
	}

	/**
	 * The run's summary, or a plain sentence when it ended without producing one. A run that
	 * was cancelled or died mid-flight has nothing to report, but the Agents dock still shows
	 * a summary for every terminal row, and the orchestrator still needs a non-empty result to
	 * reason about. Both come from here so they never disagree.
	 */
	private static String summaryOrTemplate(Outcome loop) {
		if (loop.summary() != null && !loop.summary().isBlank()) {
			return loop.summary();
		}
		return switch (loop.status()) {
			case "cancelled" -> "Stopped before finishing; no result was produced.";
			case "done" -> "Finished its tool work without producing a final answer.";
			default -> "Failed before producing a result" + (loop.error().isBlank() ? "." : ": " + loop.error());
		};
	}

	/**
	 * Runs one streaming model round and waits for it. The streamed tokens themselves are
	 * dropped: only the phase (thinking, answering) is recorded on the run, so the dock shows
	 * what the sub-agent is doing without the harness copying every token into memory.
	 */
	private Round blockingRound(
		StreamingChatModel model,
		ChatRequest request,
		AgentRunManager.AgentRun run,
		Duration timeout
	) throws Exception {
		CompletableFuture<ChatResponse> future = new CompletableFuture<>();
		boolean[] inThinking = { false };
		model.chat(
			request,
			new StreamingChatResponseHandler() {
				@Override
				public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
					if (
						partialThinking != null && partialThinking.text() != null && !partialThinking.text().isBlank() && !inThinking[0]
					) {
						inThinking[0] = true;
						enterActivity(run, AgentRunManager.Activity.THINKING);
					}
				}

				@Override
				public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
					if (partialResponse != null && partialResponse.text() != null && inThinking[0]) {
						inThinking[0] = false;
						enterActivity(run, AgentRunManager.Activity.STREAMING);
					}
				}

				@Override
				public void onCompleteResponse(ChatResponse response) {
					future.complete(response);
				}

				@Override
				public void onError(Throwable error) {
					future.completeExceptionally(error);
				}
			}
		);
		ChatResponse response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		TokenUsage used = response.metadata() == null ? null : response.metadata().tokenUsage();
		return new Round(response.aiMessage(), used == null ? new TokenUsage(0, 0, 0) : used);
	}

	// Records a phase change and pings the UI only when the phase actually moved.
	private void enterActivity(AgentRunManager.AgentRun run, AgentRunManager.Activity activity) {
		if (run.setActivity(activity)) {
			runs.notifyChange();
		}
	}

	// Runs one allowed tool; anything unknown or failing becomes an error string, never a throw.
	private String executeTool(HarnessService.AgentToolset toolset, ToolExecutionRequest request) {
		ToolExecutor executor = toolset.executors().get(request.name());
		if (executor == null) {
			return "Error: tool '" + request.name() + "' is not available to this sub-agent";
		}
		try {
			return executor.execute(request, null);
		} catch (Exception ex) {
			log.debug("Sub-agent tool {} failed with arguments: {}", request.name(), request.arguments(), ex);
			return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
		}
	}

	// Resolves the sub-agent's model from the spec: each kind carries the model the
	// user picked for it, so a worker run and a Kali dispatch can differ.
	private StreamingChatModel resolveModel(RunSpec spec) {
		AgentModelRef ref = spec.model();
		if (ref.isBlank()) {
			throw new IllegalStateException(
				"no sub-agent model configured: pick a " + spec.label() + " model in Settings > Sub-agents"
			);
		}
		LlmProvider provider = llmClientFactory.create(userSettingsService.get().requireProvider(ref.providerId()));
		return provider.chatModel(ref.modelName(), GenerationOptions.none());
	}

	// System prompt (role + runtime/scope context) plus the task as the user message.
	private List<ChatMessage> buildMessages(RunSpec spec) {
		StringBuilder system = new StringBuilder();
		system.append(spec.systemPrompt()).append("\n\n## Runtime context\n");
		system.append("Operating system: ").append(System.getProperty("os.name", "unknown")).append('\n');
		Path root = workspace.root().orElse(null);
		system
			.append("Workspace: ")
			.append(root == null ? "(none open — pass absolute paths and save findings nowhere)" : root.toString())
			.append('\n');
		try {
			var phase = engagement.currentPhase();
			system
				.append("Engagement phase ")
				.append(phase.phase())
				.append(" of ")
				.append(phase.totalPhases())
				.append(": ")
				.append(phase.name())
				.append('\n');
		} catch (Exception ignored) {
			// No workspace/engagement document; the scope block below says so.
		}
		system.append("\n## Target scope\n").append(scopeExcerpt()).append('\n');
		system
			.append("\n## Rules\n")
			.append("- Stay strictly inside the target scope above; refuse out-of-scope actions.\n")
			.append("- Do not exploit anything and do not send attack payloads unless your role allows it.\n")
			.append("- Your budget is ")
			.append(spec.maxRounds())
			.append(" tool-call rounds; the harness warns you as it runs low.\n")
			.append("- Workspace files you did not create belong to the orchestrator. Read them, but edit\n")
			.append("  only files you created; report needed changes to other files in your summary.\n")
			.append("- Background processes you start are private to you while you run. They become visible\n")
			.append("  to the orchestrator when you finish; cancelling your run kills them.\n")
			.append("- Save anything worth keeping into the workspace (under evidence/ for raw output)\n")
			.append("  before you finish, then end with a final plain-text summary of what you worked\n")
			.append("  on, what you did, what failed, and where you saved it. Your final message is\n")
			.append("  the only thing returned to the orchestrator as your result.\n")
			.append("- The FIRST LINE of that final reply must be exactly ")
			.append(FINAL_MARKER)
			.append(", alone on the line. An empty\n")
			.append("  reply, or a reply whose first line is not ")
			.append(FINAL_MARKER)
			.append(", is rejected and you will be asked\n")
			.append("  again with no tool rounds. Say failures plainly: work that found nothing or hit\n")
			.append("  a dead end, honestly reported, is a delivered result.\n");

		return List.of(SystemMessage.from(system.toString()), UserMessage.from(spec.task()));
	}

	// Compact scope excerpt from engagement.json; capped, never a failure.
	private String scopeExcerpt() {
		Path root = workspace.root().orElse(null);
		if (root == null) {
			return "(no workspace open)";
		}
		Path file = root.resolve("engagement.json");
		if (!Files.isRegularFile(file)) {
			return "(engagement.json missing)";
		}
		try {
			JsonNode target = JSON.readTree(file.toFile()).get("target");
			if (target == null || !target.isObject()) {
				return "(scope not filled in yet)";
			}
			String excerpt = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(target);
			return Text.truncate(excerpt, CONTEXT_CHAR_CAP, CAP_NOTICE);
		} catch (Exception ex) {
			return "(scope unreadable: " + ex.getMessage() + ")";
		}
	}
}
