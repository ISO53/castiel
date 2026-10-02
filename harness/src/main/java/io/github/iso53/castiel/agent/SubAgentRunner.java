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
 * <p>A run is foreground from the orchestrator's point of view (its tool call blocks until
 * the sub-agent finishes), but executes on its own virtual thread so the caller's HTTP
 * handler thread merely joins it. Each round streams from the model, but the tokens are
 * discarded: the run reports only the phase it is in, plus a final summary, so the
 * bottom-dock Agents view stays a monitor rather than a second transcript viewer.
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

	// Hard wall-clock limit for one sub-agent run when no tighter deadline applies.
	private static final Duration MAX_RUNTIME = Duration.ofMinutes(30);

	private static final ObjectMapper JSON = new ObjectMapper();

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

	// What a finished run reports back to the orchestrator's tool result.
	public record SubAgentOutcome(String status, String summary, TokenUsage usage, int rounds) {}

	// Mutable accumulator for one executed run.
	private record Outcome(
		String status,
		String error,
		String summary,
		TokenUsage usage,
		int rounds
	) {}

	/**
	 * Executes {@code spec} on behalf of {@code run}, blocking until the sub-agent finishes,
	 * the wall clock expires, or the run is cancelled. Always leaves the run in a terminal
	 * state and returns an outcome the caller can render into its tool result.
	 */
	public SubAgentOutcome execute(AgentRunManager.AgentRun run, RunSpec spec) {
		long startNanos = System.nanoTime();
		run.setState(AgentRunManager.State.RUNNING);
		runs.notifyChange();
		// Tool shells spawned by this run register under the run id, so cancelling the
		// run (from the dock, or via the orchestrator's cancel fan-out) tree-kills them.
		HarnessService.setCurrentGenerationId(run.id());
		try {
			StreamingChatModel model = resolveModel(spec);
			List<ChatMessage> messages = buildMessages(spec);
			HarnessService.AgentToolset toolset = harnessProvider.getObject().toolsFor(spec.allowedTools());
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
			log.info("Run {} ended as {} after {} rounds", run.id(), state, loop.rounds());
			return new SubAgentOutcome(loop.status(), Text.truncate(summary, RESULT_CHAR_CAP, CAP_NOTICE), loop.usage(), loop.rounds());
		} catch (Throwable ex) {
			// Throwable, not Exception: an Error here would otherwise escape and strand the run
			// in RUNNING with a dead thread, leaving the cancel button with nothing to stop.
			String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			log.warn("Sub-agent run {} failed: {}", run.id(), message, ex);
			run.setError(message);
			run.setState(AgentRunManager.State.FAILED);
			return new SubAgentOutcome("failed", "Error: " + message, run.totalUsage(), -1);
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
		long deadlineNanos = System.nanoTime() + MAX_RUNTIME.toNanos();
		List<ChatMessage> thread = new ArrayList<>(messages);
		TokenUsage total = new TokenUsage(0, 0, 0);
		int round = 0;

		for (; round < spec.maxRounds(); round++) {
			if (run.cancelled().get()) {
				log.info("Run {} stopped before round {}: cancel was requested", run.id(), round + 1);
				return new Outcome("cancelled", "", "", total, round);
			}
			long remainingNanos = deadlineNanos - System.nanoTime();
			if (remainingNanos <= 0) {
				log.warn("Run {} stopped at round {}: wall-clock limit reached", run.id(), round + 1);
				return new Outcome(
					"failed",
					"wall-clock limit of " + MAX_RUNTIME.toMinutes() + " min reached",
					"",
					total,
					round
				);
			}

			// Opened the next round: the previous phase is over, so say so before the model
			// starts producing. Covers the gap between a tool returning and the first token.
			enterActivity(run, AgentRunManager.Activity.WORKING);

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
			if (!ai.hasToolExecutionRequests()) {
				String summary =
					ai.text() == null || ai.text().isBlank() ? "(the sub-agent returned an empty answer)" : ai.text();
				log.info("Run {} finished in round {}: the model asked for no further tools", run.id(), round + 1);
				return new Outcome("done", "", summary, total, round + 1);
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
				// The run signalled it has nothing left to do; skip the final summary round.
				if (run.isStopRequested()) {
					log.info("Run {} stopped after round {}: it requested to stop", run.id(), round + 1);
					return new Outcome(
						"done",
						"",
						"The run finished its tool work and stopped on request.",
						total,
						round + 1
					);
				}
			}
		}

		log.info("Run {} exhausted its budget of {} rounds without settling", run.id(), spec.maxRounds());
		return new Outcome(
			"done",
			"",
			"The sub-agent used its full tool-round budget of " +
				spec.maxRounds() +
				" without producing a final answer. Partial work only; re-delegate a narrower task if needed.",
			total,
			round
		);
	}

	// Result of one model round: the (possibly tool-calling) message and its token usage.
	private record Round(AiMessage message, TokenUsage usage) {}

	// Collapses a tool result to its first line, so a debug line stays readable.
	private static String firstLine(String text) {
		int newline = text.indexOf('\n');
		return (newline < 0 ? text : text.substring(0, newline)).strip();
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
			.append("- Save anything worth keeping into the workspace (under evidence/ for raw output)\n")
			.append("  before you finish, then end with a terse summary of what you found and where\n")
			.append("  you saved it. Your final message is returned to the orchestrator as your result.\n");

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
