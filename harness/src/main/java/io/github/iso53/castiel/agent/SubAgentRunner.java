package io.github.iso53.castiel.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecutor;
import io.github.iso53.castiel.model.AgentModelRef;
import io.github.iso53.castiel.provider.GenerationOptions;
import io.github.iso53.castiel.provider.LlmProvider;
import io.github.iso53.castiel.service.EngagementService;
import io.github.iso53.castiel.service.HarnessService;
import io.github.iso53.castiel.service.LlmClientFactory;
import io.github.iso53.castiel.service.UserSettingsService;
import io.github.iso53.castiel.service.WorkspaceSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Runs one sub-agent generation: a short prompt, a filtered toolset, and a small model,
 * driven through the same tool-calling discipline as the orchestrator loop.
 *
 * <p>A run is foreground from the orchestrator's point of view (its tool call blocks until
 * the sub-agent finishes), but executes on its own virtual thread so the caller's HTTP
 * handler thread merely joins it. Every round streams text into the run's live transcript
 * buffer, which the bottom-dock Agents view tails.
 */
@Service
public class SubAgentRunner {

	private static final Logger log = LoggerFactory.getLogger(SubAgentRunner.class);

	// Result text kept for the orchestrator's context; more lives in the transcript file.
	private static final int RESULT_CHAR_CAP = 4_000;

	// Scope/context excerpt capped before it reaches the sub-agent's prompt.
	private static final int CONTEXT_CHAR_CAP = 2_000;

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
	public record RunSpec(String label, String systemPrompt, Set<String> allowedTools, String task, int maxRounds) {
		public RunSpec {
			label = label == null || label.isBlank() ? AgentGuardrails.WORKER_LABEL : label.strip();
			systemPrompt = systemPrompt == null ? "" : systemPrompt.strip();
			allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
			task = task == null ? "" : task.strip();
		}
	}

	// What a finished run reports back to the orchestrator's tool result.
	public record SubAgentOutcome(String status, String summary, Path transcriptPath, TokenUsage usage, int rounds) {}

	// Mutable accumulator for one executed run.
	private record Outcome(
		String status,
		String error,
		String summary,
		TokenUsage usage,
		int rounds,
		String transcript
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
			StreamingChatModel model = resolveModel();
			List<ChatMessage> messages = buildMessages(spec);
			HarnessService.AgentToolset toolset = harnessProvider.getObject().toolsFor(spec.allowedTools());
			Outcome loop = loop(run, spec, model, messages, toolset);
			Path transcriptPath = persistTranscript(run, spec, loop.transcript(), loop.usage());
			run.setTotalUsage(loop.usage());
			run.setTranscriptPath(transcriptPath);
			run.setResultSummary(cap(loop.summary(), RESULT_CHAR_CAP));
			AgentRunManager.State state = switch (loop.status()) {
				case "done" -> AgentRunManager.State.DONE;
				case "cancelled" -> AgentRunManager.State.CANCELLED;
				default -> AgentRunManager.State.FAILED;
			};
			run.setState(state);
			run.setError(loop.error());
			return new SubAgentOutcome(
				loop.status(),
				cap(loop.summary(), RESULT_CHAR_CAP),
				transcriptPath,
				loop.usage(),
				loop.rounds()
			);
		} catch (Exception ex) {
			String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			log.warn("Sub-agent run {} failed: {}", run.id(), message);
			run.setError(message);
			run.setState(AgentRunManager.State.FAILED);
			return new SubAgentOutcome("failed", "Error: " + message, run.transcriptPath(), run.totalUsage(), -1);
		} finally {
			HarnessService.setCurrentGenerationId(null);
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
		StringBuilder transcript = new StringBuilder();
		TokenUsage total = new TokenUsage(0, 0, 0);
		int round = 0;

		for (; round < spec.maxRounds(); round++) {
			if (run.cancelled().get()) {
				return new Outcome("cancelled", "", transcript.toString().strip(), total, round, transcript.toString());
			}
			long remainingNanos = deadlineNanos - System.nanoTime();
			if (remainingNanos <= 0) {
				return new Outcome(
					"failed",
					"wall-clock limit of " + MAX_RUNTIME.toMinutes() + " min reached",
					transcript.toString().strip(),
					total,
					round,
					transcript.toString()
				);
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
					return new Outcome(
						"cancelled",
						"",
						transcript.toString().strip(),
						total,
						round,
						transcript.toString()
					);
				}
				String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
				return new Outcome(
					"failed",
					message,
					transcript.toString().strip(),
					total,
					round,
					transcript.toString()
				);
			}
			total = TokenUsage.sum(total, roundResult.usage());

			AiMessage ai = roundResult.message();
			if (!ai.hasToolExecutionRequests()) {
				String summary =
					ai.text() == null || ai.text().isBlank() ? "(the sub-agent returned an empty answer)" : ai.text();
				return new Outcome("done", "", summary, total, round + 1, transcript.toString());
			}

			thread.add(ai);
			for (ToolExecutionRequest toolRequest : ai.toolExecutionRequests()) {
				String result = executeTool(toolset, toolRequest);
				transcript
					.append("\n\n### tool call: ")
					.append(toolRequest.name())
					.append('\n')
					.append(toolRequest.arguments() == null ? "" : toolRequest.arguments())
					.append('\n')
					.append("--- result ---\n")
					.append(cap(result, RESULT_CHAR_CAP));
				thread.add(ToolExecutionResultMessage.from(toolRequest, result));
				if (run.cancelled().get()) {
					return new Outcome(
						"cancelled",
						"",
						transcript.toString().strip(),
						total,
						round + 1,
						transcript.toString()
					);
				}
			}
		}

		return new Outcome(
			"done",
			"",
			"The sub-agent used its full tool-round budget of " +
				spec.maxRounds() +
				" without producing a final answer. Partial work only; re-delegate a narrower task if needed.",
			total,
			round,
			transcript.toString()
		);
	}

	// Result of one model round: the (possibly tool-calling) message and its token usage.
	private record Round(AiMessage message, TokenUsage usage) {}

	// Minimum spacing between UI change pings triggered by fresh transcript text, per run.
	private static final long TRANSCRIPT_PING_INTERVAL_MS = 1_000;

	// Ping the UI periodically while the model streams, so the transcript pane updates lively.
	private void claimTranscriptPing(AgentRunManager.AgentRun run) {
		if (run.tryClaimTranscriptPing(TRANSCRIPT_PING_INTERVAL_MS)) {
			runs.notifyChange();
		}
	}

	/**
	 * Runs one streaming model round and waits for it. Streaming chunks flow into the run's
	 * live transcript buffer while the round is in flight, so the dock pane shows progress.
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
						partialThinking != null && partialThinking.text() != null && !partialThinking.text().isBlank()
					) {
						if (!inThinking[0]) {
							inThinking[0] = true;
							run.transcript().append("\n[thinking]\n");
						}
						run.transcript().append(partialThinking.text());
						claimTranscriptPing(run);
					}
				}

				@Override
				public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
					if (partialResponse != null && partialResponse.text() != null) {
						if (inThinking[0]) {
							inThinking[0] = false;
							run.transcript().append("\n[/thinking]\n\n");
						}
						run.transcript().append(partialResponse.text());
						claimTranscriptPing(run);
					}
				}

				@Override
				public void onCompleteResponse(ChatResponse response) {
					if (inThinking[0]) {
						inThinking[0] = false;
						run.transcript().append("\n[/thinking]\n\n");
					}
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

	// Resolves the sub-agent's model: the worker model the user picked in settings.
	private StreamingChatModel resolveModel() {
		AgentModelRef worker = userSettingsService.get().workerModel();
		if (worker.isBlank()) {
			throw new IllegalStateException(
				"no sub-agent model configured: pick a worker model in Settings > Sub-agents"
			);
		}
		LlmProvider provider = llmClientFactory.create(userSettingsService.get().requireProvider(worker.providerId()));
		return provider.chatModel(worker.modelName(), GenerationOptions.none());
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
			return cap(excerpt, CONTEXT_CHAR_CAP);
		} catch (Exception ex) {
			return "(scope unreadable: " + ex.getMessage() + ")";
		}
	}

	// Writes the full transcript under evidence/agents/ and registers it as a known file.
	private Path persistTranscript(AgentRunManager.AgentRun run, RunSpec spec, String transcript, TokenUsage usage) {
		Path root = workspace.root().orElse(null);
		if (root == null) {
			return null;
		}
		try {
			Path dir = root.resolve("evidence").resolve("agents");
			Files.createDirectories(dir);
			StringBuilder document = new StringBuilder();
			document
				.append("# Sub-agent run ")
				.append(run.id())
				.append("\n\n")
				.append("- Worker: ")
				.append(spec.label())
				.append('\n')
				.append("- Started: ")
				.append(run.startedAt())
				.append('\n')
				.append("- Finished: ")
				.append(Instant.now())
				.append('\n')
				.append("- Tokens: ")
				.append(usage.totalTokenCount())
				.append("\n\n")
				.append("## Task\n\n")
				.append(spec.task())
				.append("\n\n## Transcript\n\n")
				.append(transcript.strip());
			Path target = dir.resolve(run.id() + "-" + safeName(spec.label()) + ".md");
			Files.writeString(target, document.toString(), StandardCharsets.UTF_8);
			workspace.remember(target);
			return target;
		} catch (IOException | RuntimeException ex) {
			log.warn("Could not persist transcript for run {}: {}", run.id(), ex.getMessage());
			return null;
		}
	}

	private static String safeName(String value) {
		String cleaned = value.strip().replaceAll("[^A-Za-z0-9._-]", "_");
		return cleaned.isBlank() ? "worker" : cleaned;
	}

	private static String cap(String text, int limit) {
		if (text.length() <= limit) {
			return text;
		}
		return text.substring(0, limit) + "\n[harness: truncated at " + limit + " of " + text.length() + " characters]";
	}
}
