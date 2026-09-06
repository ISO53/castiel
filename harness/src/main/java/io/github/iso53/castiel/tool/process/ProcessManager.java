package io.github.iso53.castiel.tool.process;

import io.github.iso53.castiel.service.NudgeScheduler;
import io.github.iso53.castiel.service.WorkspaceSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Spawns shell commands as managed background processes and tracks them until they die.
 *
 * <p>Each process gets a stable registry id, a mandatory human-readable purpose line, a
 * bounded output buffer tailed independently by the agent and the UI, and an open stdin
 * fed through a queued writer thread. Killing walks the full process tree. The child we
 * spawn is a shell wrapper, and destroying only the shell would orphan the real work.
 */
@Service
public class ProcessManager {

	private static final Logger LOG = LoggerFactory.getLogger(ProcessManager.class);

	/** Per-process output retention; older output is evicted from the front. */
	static final int OUTPUT_CAPACITY_CHARS = 256 * 1024;


	// A prefix that let's agent know the process is started by the user.
	public static final String USER_START_PREFIX = "[user-started] ";

	private final WorkspaceSession workspace;
	private final NudgeScheduler nudgeScheduler;

	/** Random suffix source for registry ids. */
	private static final SecureRandom ID_RANDOM = new SecureRandom();

	/** id -> entry. */
	private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();

	/** Fan-out of registry change pings for the UI's SSE feed. */
	private final Sinks.Many<String> changes = Sinks.many().replay().latest();

	public ProcessManager(WorkspaceSession workspace, NudgeScheduler nudgeScheduler) {
		this.workspace = workspace;
		this.nudgeScheduler = nudgeScheduler;
	}

	/**
	 * Starts {@code command} in the workspace root under the platform shell and begins
	 * tracking it. Output drains into the bounded buffer; stdin stays open until exit.
	 *
	 * <p>Windows note: commands are passed via {@code -EncodedCommand} (base64 UTF-16LE)
	 * because plain {@code -Command} mangles embedded double quotes through the Java →
	 * CreateProcess → PowerShell argument-quoting chain, breaking any command that quotes
	 * strings.
	 */
	public ManagedProcess start(String command, String purpose) throws IOException {
		ProcessBuilder builder = shellCommand(command);
		builder.directory(workspace.root().map(Path::toFile).orElse(null));

		Process process = builder.start();
		String id = newId();
		ManagedProcess entry = new ManagedProcess(id, process, command, purpose.strip());
		processes.put(id, entry);

		Thread.ofVirtual().name("bg-out-" + id).start(() -> drain(entry, false));
		Thread.ofVirtual().name("bg-err-" + id).start(() -> drain(entry, true));
		Thread.ofVirtual().name("bg-in-" + id).start(() -> pumpInput(entry));
		process.onExit().thenRun(() -> complete(entry));

		LOG.info("Started background process {} (pid {}): {}", id, process.pid(), purpose);
		notifyChange();
		return entry;
	}

	/** Timestamp plus a random suffix, so ids never repeat across app restarts. */
	private static String newId() {
		byte[] suffix = new byte[2];
		ID_RANDOM.nextBytes(suffix);
		return "p_" + System.currentTimeMillis() + "_" + HexFormat.of().formatHex(suffix);
	}

	/** Registry snapshot ordered oldest first. */
	public List<ManagedProcess> list() {
		return processes.values()
				.stream()
				.sorted(Comparator.comparing(ManagedProcess::id))
				.toList();
	}

	public ManagedProcess get(String id) {
		return processes.get(id);
	}

	/** Live change feed for the UI dock: a ping fires whenever the registry changes. */
	public Flux<ServerSentEvent<String>> events() {
		return changes.asFlux().map(tick -> ServerSentEvent.builder(tick).build());
	}

	/** Notifies UI subscribers that the registry changed; a dropped ping is harmless. */
	private void notifyChange() {
		changes.tryEmitNext("changed");
	}

	/** Marks an entry as read by the agent, refreshing the UI's unread flag. */
	public void markSeen(String id) {
		require(id).markSeenByAgent();
		notifyChange();
	}

	/** Queues text for the process stdin; returns false when the process has exited. */
	public boolean sendInput(String id, String text) {
		return require(id).sendInput(text);
	}

	/** Removes a finished entry from the registry; running entries cannot be removed. */
	public boolean remove(String id) {
		ManagedProcess entry = processes.get(id);
		if (entry == null || entry.isRunning()) {
			return false;
		}
		boolean removed = processes.remove(id, entry);
		if (removed) {
			notifyChange();
		}
		return removed;
	}

	/**
	 * Kills the whole process tree rooted at the tracked entry.
	 *
	 * <p>Kills descendants before their parents on repeated tree snapshots, so newly spawned
	 * grandchildren are caught by the next pass instead of escaping as orphans. Completion
	 * still flows through {@code onExit}, which records the KILLED state.
	 *
	 * @return descriptive text about what was terminated, for agent-visible feedback.
	 */
	public String kill(String id) {
		ManagedProcess entry = require(id);
		if (!entry.isRunning()) {
			return "Process " + id + " was not running (state: " + entry.state() + ").";
		}
		entry.requestKill();
		List<String> victims = destroyTree(entry.underlying().toHandle());
		return "Terminated "
				+ id
				+ " (pid "
				+ entry.osPid()
				+ ")"
				+ (victims.isEmpty()
						? "."
						: " together with " + victims.size() + " child process(es):\n" + String.join("\n", victims));
	}

	/** Last-resort cleanup so closing Castiel never leaves scans running headless. */
	@PreDestroy
	public void killAllOnShutdown() {
		for (ManagedProcess entry : processes.values()) {
			if (entry.isRunning()) {
				LOG.info("Killing background process {} on shutdown", entry.id());
				try {
					entry.requestKill();
					destroyTree(entry.underlying().toHandle());
				} catch (RuntimeException ex) {
					LOG.warn("Failed to kill {} on shutdown: {}", entry.id(), ex.getMessage());
				}
			}
		}
	}

	private void complete(ManagedProcess entry) {
		Integer code = safeExitCode(entry);
		boolean killed = entry.wasKillRequested();
		entry.terminate(killed ? ManagedProcess.State.KILLED : ManagedProcess.State.EXITED, code);
		try {
			entry.closeStdin();
		} catch (IOException ignored) {
			// Pipe already gone.
		}
		LOG.info("Background process {} exited with code {}: {}", entry.id(), code, summary(entry.purpose()));
		nudgeScheduler.notifyProcessEvent();
		notifyChange();
	}

	/** Reads decoded output chunks and appends them to the shared bounded buffer. */
	private static void drain(ManagedProcess entry, boolean errorStream) {
		try (Reader reader = new InputStreamReader(
				errorStream ? entry.underlying().getErrorStream() : entry.underlying().getInputStream(),
				StandardCharsets.UTF_8)) {
			char[] chunk = new char[4_096];
			int read = reader.read(chunk);
			while (read > 0) {
				entry.output().append(new String(chunk, 0, read));
				read = reader.read(chunk);
			}
		} catch (IOException ex) {
			entry.output().append("\n[harness] output stream error: "
					+ ex.getMessage() + "\n");
		}
	}

	/**
	 * Delivers queued stdin payloads. Writes happen here. Never inline in the tool call.
	 * So a process that stopped reading its pipe can block this worker freely without ever
	 * stalling the model's tool execution loop.
	 */
	private static void pumpInput(ManagedProcess entry) {
		while (true) {
			String item = entry.pollInput();
			if (item != null) {
				deliver(entry, item);
			}
			if (!entry.isRunning() && entry.pollInput() == null) {
				return;
			}
			try {
				Thread.sleep(25);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static void deliver(ManagedProcess entry, String item) {
		try {
			if (!entry.inputStillOpen()) {
				reportUndelivered(entry, item);
				return;
			}
			entry.stdinStream().write(ManagedProcess.utf8(item));
			entry.stdinStream().flush();
		} catch (IOException | RuntimeException ex) {
			reportUndelivered(entry, item);
			entry.markInputBroken(ex.getMessage());
		}
	}

	private static void reportUndelivered(ManagedProcess entry, String item) {
		entry.output().append("\n[harness] input could not be delivered: "
				+ item.replace("\r", "\\r").replace("\n", "\\n") + "\n");
	}

	/**
	 * Descendant-first, repeated-passes forceful termination of a whole process tree.
	 *
	 * @return description lines of every signaled descendant, best effort.
	 */
	private List<String> destroyTree(ProcessHandle root) {
		List<String> victims = new ArrayList<>();
		try {
			for (int pass = 0; pass < 25 && root.isAlive(); pass++) {
				List<ProcessHandle> descendants = root.descendants().toList();
				if (descendants.isEmpty()) {
					break;
				}
				for (ProcessHandle descendant : descendants) {
					descendant.destroyForcibly();
					victims.add(describe(descendant));
				}
			}
			root.destroyForcibly();
			root.onExit().get(5, TimeUnit.SECONDS);
		} catch (Exception ex) {
			LOG.warn("Process tree kill did not finish cleanly: {}", ex.getMessage());
		}
		return victims;
	}

	private static String describe(ProcessHandle handle) {
		return "pid " + handle.pid() + ": " + handle.info().commandLine().orElse("unknown");
	}

	private static Integer safeExitCode(ManagedProcess entry) {
		try {
			return entry.underlying().exitValue();
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private static String summary(String purpose) {
		return purpose.length() <= 60 ? purpose : purpose.substring(0, 57) + "...";
	}

	/** Builds the platform shell invocation. Shared with {@code BashTool}. */
	public static ProcessBuilder shellCommand(String command) {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		if (!windows) {
			return new ProcessBuilder("bash", "-c", command);
		}
		String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
		return new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
	}

	private ManagedProcess require(String id) {
		ManagedProcess entry = processes.get(id);
		if (entry == null) {
			throw new IllegalArgumentException("No tracked process with id " + id + "; check bg_list");
		}
		return entry;
	}
}
