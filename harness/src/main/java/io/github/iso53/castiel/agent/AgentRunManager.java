package io.github.iso53.castiel.agent;

import dev.langchain4j.model.output.TokenUsage;
import io.github.iso53.castiel.tool.process.ProcessManager;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registry of sub-agent runs, the read side of the bottom-dock Agents view.
 *
 * <p>Mirrors {@link io.github.iso53.castiel.tool.process.ProcessManager}: every run gets a
 * stable id, a live activity phase while it works, and state-change pings over an SSE feed.
 * A run's record is its task, its activity, and its final summary; the reasoning behind it
 * stays with the model and the workspace. Runs are recorded regardless of outcome so the user
 * can always see what was delegated; the oldest finished runs are evicted past the cap.
 */
@Service
public class AgentRunManager {

	// Maximum tracked runs; the oldest finished ones are evicted beyond this.
	private static final int MAX_RUNS = 100;

	private static final SecureRandom ID_RANDOM = new SecureRandom();

	private final ProcessManager processes;

	public AgentRunManager(ProcessManager processes) {
		this.processes = processes;
	}

	// Lifecycle of one sub-agent run.
	public enum State {
		QUEUED,
		RUNNING,
		DONE,
		FAILED,
		CANCELLED,
	}

	// What a running sub-agent is doing right now
	public enum Activity {
		WORKING,
		THINKING,
		STREAMING,
		TOOL_CALLING
	}

	// One tracked sub-agent run.
	public static final class AgentRun {

		private final String id;
		private final String profile;
		private final String task;
		private final Instant startedAt = Instant.now();

		private final AtomicBoolean cancelled = new AtomicBoolean(false);
		// Set when the run finished its work and should end without another model round.
		private final AtomicBoolean stopRequested = new AtomicBoolean(false);

		private volatile State state = State.QUEUED;
		// Stamped when the run first reaches a terminal state, so its runtime stops there
		// instead of growing for as long as the registry keeps the row.
		private volatile Instant endedAt;
		private volatile Activity activity = Activity.WORKING;
		private volatile TokenUsage totalUsage = new TokenUsage(0, 0, 0);
		private volatile String resultSummary = "";
		private volatile String error = "";
		// Workspace-relative path the summary was persisted to
		private volatile String resultPath = "";
		// Set once agent_read hands this run's result to the orchestrator. Mirrors
		// ManagedProcess.seenByAgent: it drives the UNREAD marker and lets a collected
		// run be dropped from the registry instead of lingering forever.
		private volatile boolean seenByAgent = false;

		AgentRun(String id, String profile, String task) {
			this.id = id;
			this.profile = profile;
			this.task = task;
		}

		public String id() {
			return id;
		}

		public String profile() {
			return profile;
		}

		public String task() {
			return task;
		}

		public Instant startedAt() {
			return startedAt;
		}

		/** When the run reached a terminal state, or null while it is still working. */
		public Instant endedAt() {
			return endedAt;
		}

		/**
		 * How long the run has taken. A finished run reports the time up to the moment it
		 * settled; a live one keeps counting, so the UI can tick it between server updates.
		 */
		public long runtimeSeconds() {
			Instant end = endedAt != null ? endedAt : Instant.now();
			return Math.max(0L, Duration.between(startedAt, end).toSeconds());
		}

		public State state() {
			return state;
		}

		public Activity activity() {
			return activity;
		}

		/** Records a phase change; the caller pings the UI only when this returns true. */
		public boolean setActivity(Activity activity) {
			if (this.activity == activity) {
				return false;
			}
			this.activity = activity;
			return true;
		}

		public AtomicBoolean cancelled() {
			return cancelled;
		}

		/**
		 * Asks the run to end after the current tool round. Unlike {@link #cancelled()} this is
		 * a successful finish, not a failure: it saves the model a final summary round when the
		 * run has no reason to keep talking.
		 */
		public void requestStop() {
			stopRequested.set(true);
		}

		/** True once {@link #requestStop()} was called. */
		public boolean isStopRequested() {
			return stopRequested.get();
		}

		public TokenUsage totalUsage() {
			return totalUsage;
		}

		public String resultSummary() {
			return resultSummary;
		}

		public String error() {
			return error;
		}

		/** Workspace-relative path of the persisted summary, or empty if none was written. */
		public String resultPath() {
			return resultPath;
		}

		/** Whether the orchestrator already collected this run's result via {@code agent_read}. */
		public boolean isSeenByAgent() {
			return seenByAgent;
		}

		/** Marks the result as collected. The run stays readable until it is removed. */
		public void markSeenByAgent() {
			this.seenByAgent = true;
		}

		public boolean isFinished() {
			return state == State.DONE || state == State.FAILED || state == State.CANCELLED;
		}

		void setState(State state) {
			this.state = state;
			// Only the first terminal state counts; a later re-set must not move the end.
			if (endedAt == null && (state == State.DONE || state == State.FAILED || state == State.CANCELLED)) {
				endedAt = Instant.now();
			}
		}

		void setTotalUsage(TokenUsage totalUsage) {
			this.totalUsage = totalUsage == null ? new TokenUsage(0, 0, 0) : totalUsage;
		}

		void setResultPath(String resultPath) {
			this.resultPath = resultPath == null ? "" : resultPath;
		}

		void setResultSummary(String resultSummary) {
			this.resultSummary = resultSummary;
		}

		void setError(String error) {
			this.error = error;
		}
	}

	// id -> run.
	private final ConcurrentMap<String, AgentRun> runs = new ConcurrentHashMap<>();

	// Fan-out of registry change pings for the UI's SSE feed.
	private final Sinks.Many<String> changes = Sinks.many().replay().latest();

	/**
	 * Registers a new run in the QUEUED state and pings the UI.
	 *
	 * @param profile Profile display name, or {@code custom} for orchestrator-defined agents.
	 * @param task    The task the sub-agent was given.
	 */
	public AgentRun create(String profile, String task) {
		AgentRun run = new AgentRun(newId(), profile, task.strip());
		runs.put(run.id(), run);
		evictOverflow();
		notifyChange();
		return run;
	}

	// Live SSE change feed; each ping tells the dock to refetch the run list.
	public Flux<ServerSentEvent<String>> events() {
		return changes.asFlux().map(tick -> ServerSentEvent.builder(tick).build());
	}

	// All tracked runs, oldest first.
	public List<AgentRun> list() {
		return runs.values().stream().sorted(Comparator.comparing(AgentRun::startedAt)).toList();
	}

	public AgentRun get(String id) {
		return id == null ? null : runs.get(id);
	}

	/**
	 * Marks a run's result as collected by the orchestrator, refreshing the UI's UNREAD
	 * flag. A no-op for an unknown id so a stale read never throws at the caller.
	 */
	public void markSeen(String id) {
		AgentRun run = get(id);
		if (run == null) {
			return;
		}
		run.markSeenByAgent();
		notifyChange();
	}

	/** Finished runs whose result the orchestrator has not collected yet. */
	public List<AgentRun> unreadFinished() {
		return list().stream().filter(run -> run.isFinished() && !run.isSeenByAgent()).toList();
	}

	/** Live runs (QUEUED or RUNNING), oldest first. */
	public List<AgentRun> live() {
		return list().stream().filter(run -> !run.isFinished()).toList();
	}

	// Requests cancellation; the runner honors the flag between rounds and inside callbacks.
	public boolean cancel(String id) {
		AgentRun run = get(id);
		if (run == null || run.isFinished()) {
			return false;
		}
		run.cancelled().set(true);
		// Kill any foreground shells this run's tool calls left behind.
		processes.killByGeneration(id);
		return true;
	}

	// Drops a finished run from the registry; live runs must be cancelled first.
	public boolean remove(String id) {
		AgentRun run = get(id);
		if (run == null || !run.isFinished()) {
			return false;
		}
		runs.remove(id);
		notifyChange();
		return true;
	}

	// Pings the UI feed; used by {@link SubAgentRunner} after every state change.
	void notifyChange() {
		changes.tryEmitNext(Long.toString(System.currentTimeMillis()));
	}

	// Timestamp plus a random suffix, so ids never repeat across app restarts.
	private static String newId() {
		byte[] suffix = new byte[2];
		ID_RANDOM.nextBytes(suffix);
		return (
			"agent-" +
			Long.toString(System.currentTimeMillis(), 36) +
			"-" +
			String.format("%02x", suffix[0]) +
			String.format("%02x", suffix[1])
		);
	}

	// Evicts the oldest finished runs once the registry exceeds its cap.
	private void evictOverflow() {
		if (runs.size() <= MAX_RUNS) {
			return;
		}
		list()
			.stream()
			.filter(AgentRun::isFinished)
			.limit(runs.size() - MAX_RUNS)
			.forEach(run -> runs.remove(run.id()));
	}
}
