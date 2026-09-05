package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.NudgeScheduler;
import io.github.iso53.castiel.tool.process.BoundedOutputBuffer;
import io.github.iso53.castiel.tool.process.ManagedProcess;
import io.github.iso53.castiel.tool.process.ProcessManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Long-running and interactive shell commands as tracked background processes.
 *
 * <p>Contrast with {@link BashTool}: that tool blocks until a quick command finishes.
 * The {@code bg_*} family starts processes that keep running across many model rounds,
 * can be polled for new output since the last read, accept stdin input for REPL-style
 * programs, and can arm harness wake-up timers so the agent continues work while
 * waiting instead of burning tool calls in busy loops.
 */
@Service
public class BackgroundShellTool implements ToolProvider {

	/** Default per-read output cap so slow-drip polls never flood the context window. */
	private static final int DEFAULT_READ_CHARS = 4_000;
	private static final int MAX_READ_CHARS = 20_000;

	private final ProcessManager manager;
	private final NudgeScheduler scheduler;

	/**
	 * Chat session the currently streaming generation belongs to; set and cleared by
	 * {@code HarnessService} around each generation. Single-user application assumption:
	 * concurrent generations would overwrite each other's value, which degrades to an
	 * error message from {@code bg_wait}, never incorrect scheduling of both.
	 */
	private volatile String activeChatId;

	/** Agent-read cursor per process so bg_read returns deltas across calls. */
	private final Map<String, Long> agentCursors = new ConcurrentHashMap<>();

	public BackgroundShellTool(ProcessManager manager, NudgeScheduler scheduler) {
		this.manager = manager;
		this.scheduler = scheduler;
	}

	public void setActiveChatId(String chatId) {
		this.activeChatId = chatId;
	}

	@Tool(
		name = "bg_start",
		value = {
			"Starts a shell command as a background process without waiting for it.",
			"Returns a registry id immediately; the process keeps running across your turns.",
			"The purpose argument is required: state what the command does and how long you",
			"expect it to run, e.g. 'SYN scan of 10.0.0.5, expect ~10 min'. Later checks show",
			"elapsed vs expected time so you can spot runs that went sideways.",
			"For commands finishing within ~1 minute prefer the plain bash tool.",
		}
	)
	public String start(
		@P("The command to execute") String command,
		@P("Short description of what this process does and its expected runtime") String purpose
	) {
		if (command == null || command.isBlank()) {
			return "Error: command is required";
		}
		if (purpose == null || purpose.isBlank()) {
			return "Error: purpose is required; describe what the process does and its expected duration";
		}
		try {
			ManagedProcess entry = manager.start(command, purpose);
			return (
				"Started " +
				entry.id() +
				" (pid " +
				entry.osPid() +
				"). Use bg_read/bg_list to follow it; bg_send to type into it if interactive."
			);
		} catch (Exception ex) {
			return "Error: could not start the command: " + ex.getMessage();
		}
	}

	@Tool(
		name = "bg_list",
		value = {
			"Lists every background process you started with its id, OS pid, purpose, expected",
			"vs elapsed runtime, and state. Finished-but-unread entries remain listed until you",
			"read them with bg_read or the user removes them.",
		}
	)
	public String list() {
		List<ManagedProcess> entries = manager.list();
		if (entries.isEmpty()) {
			return "No background processes are tracked right now.";
		}
		StringBuilder out = new StringBuilder();
		for (ManagedProcess entry : entries) {
			out.append(entry.id())
				.append(" | pid ")
				.append(entry.osPid())
				.append(" | ")
				.append(formatRuntime(entry))
				.append(" | ")
				.append(entry.state())
				.append(exitCodeSuffix(entry))
				.append(entry.isSeenByAgent() || entry.isRunning() ? "" : " | UNREAD")
				.append('\n')
				.append("    ")
				.append(summarize(entry.purpose()))
				.append('\n');
		}
		return out.toString().stripTrailing();
	}

	@Tool(
		name = "bg_read",
		value = {
			"Returns the background process output produced since your last read (delta), plus",
			"its current state and elapsed runtime. Compare elapsed against the expectation you",
			"gave at bg_start; a process running far past expectation or looping on useless data",
			"is usually worth killing via bg_kill so you can change technique.",
		}
	)
	public String read(@P("Registry id from bg_start/bg_list") String processId) {
		return read(processId, DEFAULT_READ_CHARS);
	}

	String read(String processId, Integer maxChars) {
		ManagedProcess entry = require(processId);
		int requested = maxChars == null ? DEFAULT_READ_CHARS : maxChars;
		int cap = (int) Math.clamp((long) requested, 100L, (long) MAX_READ_CHARS);
		manager.markSeen(entry.id());
		BoundedOutputBuffer.Page page = entry.output().read(cursorFor(entry), cap);
		// The harness keeps one shared cursor per entry for agent reads:
		// next call resumes exactly where this one stopped.
		agentCursors.put(entry.id(), page.cursor());
		StringBuilder out = new StringBuilder();
		out.append(entry.id())
			.append(" | pid ")
			.append(entry.osPid())
			.append(" | ")
			.append(formatRuntime(entry))
			.append(" | ")
			.append(entry.state())
			.append(exitCodeSuffix(entry));
		if (page.text().isEmpty()) {
			out.append("\n(no new output)");
		} else {
			out.append("\n").append(page.text().stripTrailing());
		}
		if (!entry.isRunning()) {
			out.append("\nThis process is finished");
			if (entry.isSeenByAgent()) {
				manager.remove(entry.id());
				out.append("; it was removed from tracking.");
			}
		}
		return out.toString();
	}

	@Tool(
		name = "bg_send",
		value = {
			"Sends input to a running background process stdin (interactive programs such as",
			"REPL-style tools). Write text directly; embed <enter> for Enter, <space> for Space,",
			"<tab> for Tab. Sending returns immediately — re-read the output after a short wait",
			"to see how the program reacted. Password prompts may not work over pipes.",
		}
	)
	public String send(
		@P("Registry id from bg_start/bg_list") String processId,
		@P("Keys or text to type") String input
	) {
		if (input == null || input.isEmpty()) {
			return "Error: input is required";
		}
		ManagedProcess entry = require(processId);
		boolean queued = manager.sendInput(entry.id(), expandKeys(input));
		return queued
			? "Input delivered to " + entry.id() + "; use bg_read shortly to see the reaction."
			: "Error: " + entry.id() + " has exited, its stdin is closed.";
	}

	@Tool(
		name = "bg_kill",
		value = {
			"Forcefully terminates a background process including all of its child processes.",
			"Use when a scan stalls, loops on useless data, runs far past your stated expectation,",
			"or you simply no longer need it.",
		}
	)
	public String kill(@P("Registry id from bg_start/bg_list") String processId) {
		try {
			return manager.kill(processId);
		} catch (IllegalArgumentException ex) {
			return "Error: " + ex.getMessage();
		}
	}

	@Tool(
		name = "bg_wait",
		value = {
			"Arms a harness timer, then STOP generating — end your turn with plain text only.",
			"When the time elapses (or a tracked process exits sooner) the harness restarts you",
			"with a [harness] message describing what changed. Use this whenever your remaining",
			"work depends on a running process instead of polling bg_read repeatedly.",
		}
	)
	public String wait(@P("Seconds to wait before the harness wakes you (15-300)") Integer seconds) {
		String chatId = activeChatId;
		if (chatId == null || chatId.isBlank()) {
			return "Error: this chat session cannot receive wake-ups (no session id); continue working without scheduling";
		}
		int effective = scheduler.schedule(chatId, seconds == null ? NudgeScheduler.MIN_WAIT_SECONDS : seconds);
		return (
			"Wake-up armed for " +
			effective +
			"s. Stop generating now — end your turn with plain text and no further tool calls."
		);
	}

	private long cursorFor(ManagedProcess entry) {
		Long cursor = agentCursors.get(entry.id());
		return cursor == null ? 0L : cursor;
	}

	private ManagedProcess require(String id) {
		ManagedProcess entry = manager.get(id);
		if (entry == null) {
			throw new IllegalArgumentException("No tracked process with id " + id + "; check bg_list");
		}
		return entry;
	}

	private static String formatRuntime(ManagedProcess entry) {
		Duration runtime = entry.runtime();
		long seconds = runtime.toSeconds();
		if (seconds < 60) {
			return seconds + "s elapsed";
		}
		long minutes = seconds / 60;
		if (minutes < 90) {
			return minutes + "m elapsed";
		}
		return "%dh%02dm elapsed".formatted(minutes / 60, minutes % 60);
	}

	private static String exitCodeSuffix(ManagedProcess entry) {
		Integer code = entry.exitCode();
		return code == null ? "" : " | exit " + code;
	}

	private static String summarize(String purpose) {
		String line = purpose.strip();
		return line.length() <= 100 ? line : line.substring(0, 97) + "...";
	}

	/** Expands {@code <enter>}-style key placeholders into real control characters. */
	static String expandKeys(String input) {
		return input
			.replace("<enter>", "\r\n")
			.replace("<Enter>", "\r\n")
			.replace("<tab>", "\t")
			.replace("<Tab>", "\t")
			.replace("<space>", " ")
			.replace("<Space>", " ");
	}
}
