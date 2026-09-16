package io.github.iso53.castiel.agent;

import dev.langchain4j.model.output.TokenUsage;
import io.github.iso53.castiel.tool.process.BoundedOutputBuffer;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Registry of sub-agent runs, the read side of the bottom-dock Agents view.
 *
 * <p>Mirrors {@link io.github.iso53.castiel.tool.process.ProcessManager}: every run gets a
 * stable id, a live bounded transcript buffer tailed by the UI, and state-change pings over
 * an SSE feed. Runs are recorded regardless of outcome so the user can always inspect what
 * a sub-agent did; the oldest finished runs are evicted once the registry grows past its cap.
 */
@Service
public class AgentRunManager {

	// Live transcript tail retained per run; older characters are evicted from the front.
	static final int TRANSCRIPT_CAPACITY_CHARS = 256 * 1024;

	// Maximum tracked runs; the oldest finished ones are evicted beyond this.
	private static final int MAX_RUNS = 100;

	private static final SecureRandom ID_RANDOM = new SecureRandom();

	// Lifecycle of one sub-agent run.
	public enum State {
		QUEUED,
		RUNNING,
		DONE,
		FAILED,
		CANCELLED,
	}

	// One tracked sub-agent run.
	public static final class AgentRun {

		private final String id;
		private final String parentGenerationId;
		private final String profile;
		private final String task;
		private final Instant startedAt = Instant.now();

		private final BoundedOutputBuffer transcript = BoundedOutputBuffer.of(TRANSCRIPT_CAPACITY_CHARS);
		private final AtomicBoolean cancelled = new AtomicBoolean(false);
		private final AtomicLong lastTranscriptPingAt = new AtomicLong(0);

		private volatile State state = State.QUEUED;
		private volatile TokenUsage totalUsage = new TokenUsage(0, 0, 0);
		// Empty path means "no transcript persisted"; callers test {@code toString().isEmpty()}.
		public static final Path NO_TRANSCRIPT = Path.of("");
		private volatile Path transcriptPath = NO_TRANSCRIPT;
		private volatile String resultSummary = "";
		private volatile String error = "";

		AgentRun(String id, String parentGenerationId, String profile, String task) {
			this.id = id;
			this.parentGenerationId = parentGenerationId;
			this.profile = profile;
			this.task = task;
		}

		public String id() {
			return id;
		}

		// Chat generation id of the orchestrator that spawned this run; used for cancel fan-out.
		public String parentGenerationId() {
			return parentGenerationId;
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

		public State state() {
			return state;
		}

		// Live transcript buffer; readers track their own cursor.
		public BoundedOutputBuffer transcript() {
			return transcript;
		}

		/**
		 * Claims the right to emit one UI change ping for fresh transcript text; true at
		 * most once per {@code intervalMs} per run, so token-level streaming cannot flood
		 * the SSE feed.
		 */
		public boolean tryClaimTranscriptPing(long intervalMs) {
			long now = System.currentTimeMillis();
			long last = lastTranscriptPingAt.get();
			if (now - last < intervalMs) {
				return false;
			}
			return lastTranscriptPingAt.compareAndSet(last, now);
		}

		public AtomicBoolean cancelled() {
			return cancelled;
		}

		public TokenUsage totalUsage() {
			return totalUsage;
		}

		public Path transcriptPath() {
			return transcriptPath;
		}

		public String resultSummary() {
			return resultSummary;
		}

		public String error() {
			return error;
		}

		public boolean isFinished() {
			return state == State.DONE || state == State.FAILED || state == State.CANCELLED;
		}

		void setState(State state) {
			this.state = state;
		}

		void setTotalUsage(TokenUsage totalUsage) {
			this.totalUsage = totalUsage == null ? new TokenUsage(0, 0, 0) : totalUsage;
		}

		void setTranscriptPath(Path transcriptPath) {
			this.transcriptPath = transcriptPath == null ? NO_TRANSCRIPT : transcriptPath;
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

	// parentGenerationId -> child run ids, for cancel fan-out from the orchestrator.
	private final ConcurrentMap<String, Set<String>> childrenByParent = new ConcurrentHashMap<>();

	// Fan-out of registry change pings for the UI's SSE feed.
	private final Sinks.Many<String> changes = Sinks.many().replay().latest();

	/**
	 * Registers a new run in the QUEUED state and pings the UI.
	 *
	 * @param parentGenerationId Chat generation of the orchestrator; used for cancel fan-out.
	 * @param profile            Profile display name, or {@code custom} for orchestrator-defined agents.
	 * @param task               The task the sub-agent was given.
	 */
	public AgentRun create(String parentGenerationId, String profile, String task) {
		String parent = parentGenerationId == null ? "" : parentGenerationId.strip();
		AgentRun run = new AgentRun(newId(), parent, profile, task.strip());
		runs.put(run.id(), run);
		if (!parent.isBlank()) {
			childrenByParent.computeIfAbsent(parent, key -> ConcurrentHashMap.newKeySet()).add(run.id());
		}
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

	// Requests cancellation; the runner honors the flag between rounds and inside callbacks.
	public boolean cancel(String id) {
		AgentRun run = get(id);
		if (run == null || run.isFinished()) {
			return false;
		}
		run.cancelled().set(true);
		return true;
	}

	// Cancels every live child run of the given orchestrator generation.
	public void cancelByParent(String parentGenerationId) {
		if (parentGenerationId == null) {
			return;
		}
		Set<String> children = childrenByParent.get(parentGenerationId);
		if (children == null) {
			return;
		}
		for (String id : children) {
			AgentRun run = runs.get(id);
			if (run != null && !run.isFinished()) {
				run.cancelled().set(true);
			}
		}
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
