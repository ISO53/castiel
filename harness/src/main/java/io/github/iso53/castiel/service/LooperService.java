package io.github.iso53.castiel.service;

import io.github.iso53.castiel.agent.AgentGuardrails;
import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.agent.SubAgentRunner;
import io.github.iso53.castiel.model.ChatSession;
import io.github.iso53.castiel.model.ChatStreamRequest;
import io.github.iso53.castiel.model.ChatTurn;
import io.github.iso53.castiel.model.UserSettings;
import io.github.iso53.castiel.util.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * The looper: keeps one locked chat working while the operator is away.
 *
 * <p>A tiny state machine polled once a second. Whenever the locked chat has no live
 * generation, a looper run (a harness-internal sub-agent) reads the orchestrator's last
 * message and the workspace, and writes a short directive. The directive is submitted as
 * an ordinary user turn through {@link HarnessService#stream}, so persistence, history
 * and the tool loop all behave exactly like a human-sent message. Any failure stops the
 * loop; there is no retry.
 */
@Service
public class LooperService {

	private static final Logger log = LoggerFactory.getLogger(LooperService.class);

	private static final String LOOPER_PROMPT_PATH = "prompts/LOOPER_PROMPT.md";

	// Read-only workspace context tools; agent_list lets it see delegated run results.
	private static final Set<String> LOOPER_TOOLS = Set.of(
		"read_file",
		"workspace_search",
		"workspace_info",
		"agent_list",
		"bg_list"
	);

	private static final long POLL_MS = 1000;
	// Grace after a submit so the stop check never races the generation registration.
	private static final long GRACE_MS = 3000;
	private static final int MAX_PRIOR_DIRECTIVES = 8;
	private static final int PRIOR_DIRECTIVE_CAP = 1000;
	private static final int LAST_MESSAGE_CAP = 6000;

	private final HarnessService harness;
	private final SubAgentRunner runner;
	private final AgentRunManager runs;
	private final ChatPersistenceService chats;
	private final EngagementService engagement;
	private final UserSettingsService settings;
	private final WorkspaceSession workspace;

	// One loop, one locked chat at a time.
	private enum LoopState {
		/** No generation is live; the next tick starts a looper run. */
		IDLE,
		/** A looper run is reading the workspace and writing a directive. */
		LOOPER_RUNNING,
		/** The orchestrator is working on the last directive. */
		ORCH_RUNNING,
		/** The loop is stopped, by the operator or by a failure. */
		STOPPED,
	}

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "looper-watch");
		thread.setDaemon(true);
		return thread;
	});
	private ScheduledFuture<?> pollTask;

	// Loop state; every field is written under the service monitor only.
	private String chatId;
	private LoopState state = LoopState.STOPPED;
	private String phaseLabel = "";
	private String looperRunId;
	private long graceUntil;
	private int cycles;
	private String error;

	public LooperService(
		HarnessService harness,
		SubAgentRunner runner,
		AgentRunManager runs,
		ChatPersistenceService chats,
		EngagementService engagement,
		UserSettingsService settings,
		WorkspaceSession workspace
	) {
		this.harness = harness;
		this.runner = runner;
		this.runs = runs;
		this.chats = chats;
		this.engagement = engagement;
		this.settings = settings;
		this.workspace = workspace;
	}

	/** Locks one chat into the loop. Refuses when another chat is locked. */
	public synchronized Map<String, Object> lock(String requestedChatId) {
		if (requestedChatId == null || requestedChatId.isBlank()) {
			throw new IllegalStateException("Chat id is required");
		}
		if (state != LoopState.STOPPED && !requestedChatId.equals(chatId)) {
			throw new IllegalStateException("Another chat is already locked; unlock it first");
		}
		if (workspace.root().isEmpty()) {
			throw new IllegalStateException("Open a workspace first");
		}
		if (settings.get().looperModel().isBlank()) {
			throw new IllegalStateException("Pick a looper model in Settings > Sub-agents first");
		}
		this.chatId = requestedChatId.trim();
		this.error = null;
		this.cycles = 0;
		this.phaseLabel = phaseLabel();
		// Locking mid-generation: the loop picks up when that generation ends.
		this.state = harness.isGenerating(this.chatId) ? LoopState.ORCH_RUNNING : LoopState.IDLE;
		this.graceUntil = System.currentTimeMillis() + GRACE_MS;
		startPoller();
		log.info("Chat {} locked into the loop (phase: {})", this.chatId, this.phaseLabel);
		return status(this.chatId);
	}

	/** Unlocks the chat: stops future cycles. A live generation finishes on its own. */
	public synchronized Map<String, Object> unlock(String requestedChatId) {
		if (requestedChatId == null || !requestedChatId.equals(chatId)) {
			return status(requestedChatId);
		}
		stopPoller();
		if (looperRunId != null) {
			runs.cancel(looperRunId);
		}
		chatId = null;
		looperRunId = null;
		error = null;
		state = LoopState.STOPPED;
		log.info("Chat unlocked from the loop");
		return status(requestedChatId);
	}

	/** Loop status as the UI needs it: locked, current phase, and the last failure. */
	public synchronized Map<String, Object> status(String requestedChatId) {
		boolean locked = requestedChatId != null && requestedChatId.equals(chatId) && state != LoopState.STOPPED;
		// A failure keeps the chat context around until the next lock or unlock, so the
		// operator can read why the loop stopped.
		String reportedError = requestedChatId != null && requestedChatId.equals(chatId) ? error : null;
		String liveGeneration = harness.currentGenerationId(requestedChatId);
		return Map.of(
			"locked",
			locked,
			"state",
			locked ? state.name() : "STOPPED",
			"generationRunning",
			harness.isGenerating(requestedChatId),
			"generationId",
			liveGeneration == null ? "" : liveGeneration,
			"cycles",
			locked ? cycles : 0,
			"error",
			reportedError == null ? "" : reportedError
		);
	}

	// One watch tick: move the state machine forward by one step at most.
	private synchronized void tick() {
		if (state == LoopState.STOPPED || chatId == null) {
			return;
		}
		long now = System.currentTimeMillis();
		switch (state) {
			case IDLE -> {
				if (harness.isGenerating(chatId)) {
					state = LoopState.ORCH_RUNNING;
					graceUntil = now + GRACE_MS;
				} else if (now >= graceUntil) {
					startLooper();
				}
			}
			case LOOPER_RUNNING -> collectLooper();
			case ORCH_RUNNING -> {
				if (now >= graceUntil && !harness.isGenerating(chatId)) {
					state = LoopState.IDLE;
				}
			}
			default -> {
			}
		}
	}

	// Spawns the looper run on its own virtual thread.
	private void startLooper() {
		try {
			UserSettings userSettings = settings.get();
			String task = buildTask();
			AgentRunManager.AgentRun run = runs.create(AgentGuardrails.LOOPER_LABEL, task);
			looperRunId = run.id();
			SubAgentRunner.RunSpec spec = new SubAgentRunner.RunSpec(
				AgentGuardrails.LOOPER_LABEL,
				userSettings.looperModel(),
				looperSystemPrompt(),
				LOOPER_TOOLS,
				task,
				userSettings.looperMaxRounds()
			);
			state = LoopState.LOOPER_RUNNING;
			Thread.ofVirtual()
				.name("looper-run-" + run.id())
				.start(() -> runner.execute(run, spec));
			log.info("Looper run {} started for chat {}", run.id(), chatId);
		} catch (Exception ex) {
			stopLoop("Looper could not start: " + ex.getMessage());
		}
	}

	// Collects a finished looper run and submits its directive.
	private void collectLooper() {
		AgentRunManager.AgentRun run = runs.get(looperRunId);
		if (run == null) {
			stopLoop("The looper run disappeared from the registry");
			return;
		}
		if (!run.isFinished()) {
			return;
		}
		runs.remove(run.id());
		looperRunId = null;
		if (run.state() != AgentRunManager.State.DONE) {
			stopLoop("Looper run failed (" + run.state() + ")" + (run.error().isBlank() ? "" : ": " + run.error()));
			return;
		}
		String directive = extractDirective(run);
		if (directive.isBlank()) {
			stopLoop("The looper produced no directive");
			return;
		}
		// A manual turn during the looper run made its context stale: drop the directive
		// and let the loop re-run after that generation ends.
		if (harness.isGenerating(chatId)) {
			log.info("Directive discarded: a manual turn started while the looper was running");
			state = LoopState.ORCH_RUNNING;
			graceUntil = System.currentTimeMillis() + GRACE_MS;
			return;
		}
		submitDirective(directive);
	}

	// Submits the directive as an ordinary user turn through the standard stream pipeline.
	private void submitDirective(String directive) {
		try {
			ChatSession session = chats.loadChat(chatId);
			ChatStreamRequest request = new ChatStreamRequest(
				session.providerId(),
				session.modelName(),
				List.of(new ChatTurn("user", directive, List.of())),
				session.reasoningEffort(),
				chatId,
				AgentGuardrails.LOOPER_LABEL
			);
			state = LoopState.ORCH_RUNNING;
			graceUntil = System.currentTimeMillis() + GRACE_MS;
			cycles++;
			// Subscribe server-side: the stream persists everything on its own, so the
			// events themselves are discarded here. A live generation is never cancelled
			// by the loop; unlocking or a failure only stops future cycles.
			harness.stream(request).subscribe(
				event -> {},
				ex -> stopLoop("Generation failed: " + ex.getMessage()),
				() -> {}
			);
			log.info("Looper directive submitted to chat {} (cycle {})", chatId, cycles);
		} catch (Exception ex) {
			stopLoop("Could not submit the directive: " + ex.getMessage());
		}
	}

	// Stops the loop on any failure: no retry, the operator decides what happens next.
	private synchronized void stopLoop(String reason) {
		if (state == LoopState.STOPPED) {
			return;
		}
		log.warn("Looper stopped for chat {}: {}", chatId, reason);
		stopPoller();
		if (looperRunId != null) {
			runs.cancel(looperRunId);
		}
		looperRunId = null;
		state = LoopState.STOPPED;
		error = reason;
	}

	private void startPoller() {
		stopPoller();
		pollTask = scheduler.scheduleWithFixedDelay(this::safeTick, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
	}

	private void stopPoller() {
		if (pollTask != null) {
			pollTask.cancel(false);
			pollTask = null;
		}
	}

	// A tick must never throw: an escaping exception would silently kill the schedule.
	private void safeTick() {
		try {
			tick();
		} catch (Throwable ex) {
			log.warn("Looper tick failed", ex);
			stopLoop("Internal looper error: " + ex.getMessage());
		}
	}

	// The task packet: the orchestrator's report, prior directives, and the frozen phase.
	private String buildTask() throws IOException {
		ChatSession session = chats.loadChat(chatId);
		String lastMessage = "";
		List<String> prior = new ArrayList<>();
		// Newest first, so "last" and the directive tail come from the thread's end.
		List<io.github.iso53.castiel.model.ChatMessage> messages = session.messages();
		for (int i = messages.size() - 1; i >= 0; i--) {
			io.github.iso53.castiel.model.ChatMessage message = messages.get(i);
			String text = Text.truncate(messageText(message), LAST_MESSAGE_CAP, "\n[harness: truncated]");
			if ("assistant".equals(message.role()) && lastMessage.isEmpty()) {
				lastMessage = text;
			}
			if (
				"user".equals(message.role()) &&
				AgentGuardrails.LOOPER_LABEL.equals(message.origin()) &&
				prior.size() < MAX_PRIOR_DIRECTIVES
			) {
				prior.add(Text.truncate(text, PRIOR_DIRECTIVE_CAP, "\n[harness: truncated]"));
			}
		}
		StringBuilder task = new StringBuilder();
		task.append("Write the next directive for the orchestrator.\n\n");
		task.append("## Frozen engagement phase\n").append(phaseLabel).append("\n\n");
		task.append("## The orchestrator's last message\n");
		task.append(lastMessage.isEmpty() ? "(nothing yet)" : lastMessage).append("\n\n");
		task.append("## Your previous directives, newest first\n");
		if (prior.isEmpty()) {
			task.append("(none)");
		} else {
			for (int i = 0; i < prior.size(); i++) {
				task.append(i + 1)
					.append(". ")
					.append(prior.get(i))
					.append("\n");
			}
		}
		return task.toString();
	}

	// Joins the text parts of a persisted message.
	private static String messageText(io.github.iso53.castiel.model.ChatMessage message) {
		StringBuilder text = new StringBuilder();
		for (var part : message.parts()) {
			if ("text".equals(part.type()) && part.text() != null) {
				text.append(part.text());
			}
		}
		return text.toString();
	}

	// Reads the directive from the run's workspace file first: the registry summary is
	// capped, the file is not. Falls back to the registry summary.
	private String extractDirective(AgentRunManager.AgentRun run) {
		String text = run.resultSummary();
		if (!run.resultPath().isBlank()) {
			Path root = workspace.root().orElse(null);
			if (root != null) {
				try {
					String file = Files.readString(root.resolve(run.resultPath()), StandardCharsets.UTF_8);
					int marker = file.indexOf("## Summary");
					if (marker >= 0) {
						text = file.substring(marker + "## Summary".length()).strip();
					}
				} catch (IOException ex) {
					log.debug("Could not read the looper summary file for run {}: {}", run.id(), ex.getMessage());
				}
			}
		}
		// Drop the DONE marker line the run protocol requires.
		int newline = text.indexOf('\n');
		if (newline >= 0 && "DONE".equalsIgnoreCase(text.substring(0, newline).strip())) {
			text = text.substring(newline + 1).strip();
		}
		return Text.truncate(text.strip(), settings.get().looperDirectiveCap(), "\n[harness: directive truncated]");
	}

	// Frozen at lock time; the looper may never suggest leaving this phase.
	private String phaseLabel() {
		try {
			var phase = engagement.currentPhase();
			return (
				"Phase " +
				phase.phase() +
				" of " +
				phase.totalPhases() +
				": " +
				phase.name() +
				" (" +
				phase.summary() +
				")"
			);
		} catch (Exception ex) {
			return "(unknown)";
		}
	}

	// The looper's system prompt from the classpath, with a safe fallback.
	private String looperSystemPrompt() {
		try {
			return Files.readString(
				Path.of(new ClassPathResource(LOOPER_PROMPT_PATH).getURL().toURI()),
				StandardCharsets.UTF_8
			);
		} catch (Exception ex) {
			log.warn("Could not read {}: {}", LOOPER_PROMPT_PATH, ex.getMessage());
			return (
				"You write short directives that keep a penetration testing orchestrator working. " +
				"Reply with DONE alone on the first line, then the directive."
			);
		}
	}
}
