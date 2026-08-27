package io.github.iso53.castiel.tool.process;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registry entry for one agent-spawned background process.
 *
 * <p>Lifecycle: {@code RUNNING} until the process exits — either on its own
 * ({@link State#EXITED}) or killed through {@link ProcessManager#kill}
 * ({@link State#KILLED}). Finished entries stay in the registry until the agent reads
 * them or the user removes them from the UI.
 *
 * <p>Threads owned elsewhere (see {@link ProcessManager}): two drain pumps append decoded
 * output into {@link #output()}, one input pump writes queued stdin payloads. All state
 * transitions synchronize on this instance; the output buffer synchronizes internally.
 */
public final class ManagedProcess {

	public enum State {
		RUNNING, EXITED, KILLED
	}

	private final String id;
	private final String command;
	private final String purpose;
	private final Instant startedAt;

	private final Process process;
	private final OutputStream stdin;
	private final BoundedOutputBuffer output;
	private final ConcurrentLinkedQueue<String> pendingInput = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean inputOpen = new AtomicBoolean(true);
	private final AtomicBoolean killRequested = new AtomicBoolean(false);

	private volatile State state = State.RUNNING;
	private volatile Integer exitCode;
	private volatile Instant endedAt;
	private volatile boolean seenByAgent;

	ManagedProcess(String id, Process process, String command, String purpose) {
		this.id = id;
		this.command = command;
		this.purpose = purpose;
		this.startedAt = Instant.now();
		this.process = process;
		this.stdin = process.getOutputStream();
		this.output = new BoundedOutputBuffer(ProcessManager.OUTPUT_CAPACITY_CHARS);
	}

	public String id() {
		return id;
	}

	public long osPid() {
		return process.pid();
	}

	public String command() {
		return command;
	}

	public String purpose() {
		return purpose;
	}

	public Instant startedAt() {
		return startedAt;
	}

	public State state() {
		return state;
	}

	public Integer exitCode() {
		return exitCode;
	}

	public boolean isRunning() {
		return state == State.RUNNING;
	}

	public boolean isSeenByAgent() {
		return seenByAgent;
	}

	public void markSeenByAgent() {
		seenByAgent = true;
	}

	/** Records that a forceful kill was requested for this entry. */
	public void requestKill() {
		killRequested.set(true);
	}

	public boolean wasKillRequested() {
		return killRequested.get();
	}

	/** Notes an stdin failure into the visible output so the agent can see why input died. */
	public void markInputBroken(String reason) {
		inputOpen.set(false);
		output().append("\n[harness] stdin closed: " + (reason == null ? "unknown error" : reason) + "\n");
	}

	public BoundedOutputBuffer output() {
		return output;
	}

	/** How long the process ran (or has been running). */
	public Duration runtime() {
		return Duration.between(startedAt, endedAt != null ? endedAt : Instant.now());
	}

	/** True when the process is alive but exits while being killed. */
	boolean sameUnderlying(Process other) {
		return process == other;
	}

	/** Queues text for delivery to the process stdin; returns false after exit. */
	public boolean sendInput(String text) {
		if (!inputOpen.get()) {
			return false;
		}
		pendingInput.add(text);
		return true;
	}

	String pollInput() {
		return pendingInput.poll();
	}

	OutputStream stdinStream() {
		return stdin;
	}

	boolean inputStillOpen() {
		return inputOpen.get();
	}

	void closeStdin() throws IOException {
		inputOpen.set(false);
		stdin.close();
	}

	Process underlying() {
		return process;
	}

	/** Single transition point; called exactly once when the OS process terminates. */
	void terminate(State finalState, Integer code) {
		synchronized (this) {
			if (state != State.RUNNING) {
				return;
			}
			state = finalState;
			exitCode = code;
			endedAt = Instant.now();
		}
		inputOpen.set(false);
	}

	static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}
}
