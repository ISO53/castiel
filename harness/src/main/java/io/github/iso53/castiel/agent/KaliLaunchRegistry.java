package io.github.iso53.castiel.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks which requested Kali tools a dispatch sub-agent has actually launched or skipped.
 *
 * <p>The registry is the only trustworthy answer to "did every requested tool start?". A
 * count of {@code bg_start} calls is not enough, because two launches of the same tool and
 * none of another sum to the same number. Each tool id the user selected is therefore settled
 * exactly once, either by a launch or by an explicit skip with a reason.
 *
 * <p>Plans are registered by the controller before the run starts and are keyed by run id.
 * The launch tool consults the plan to authorize a tool id, and the runner consults
 * {@link #isSettled} to decide when the sub-agent may stop.
 */
@Component
public class KaliLaunchRegistry {

	// How a requested tool left the plan: running, skipped on purpose, or failed to start.
	public enum Outcome {
		LAUNCHED,
		SKIPPED,
		FAILED
	}

	// What the sub-agent did with one requested tool.
	public record Entry(String toolId, Outcome outcome, String detail) {}

	// One dispatch run's requested tools and how each has been settled.
	public static final class Plan {

		private final List<String> requested;
		private final Map<String, Entry> entries = new ConcurrentHashMap<>();

		Plan(List<String> requested) {
			this.requested = List.copyOf(requested);
		}

		public List<String> requested() {
			return requested;
		}

		public Map<String, Entry> entries() {
			return Map.copyOf(entries);
		}

		// True when this tool id is part of the plan, so the model cannot launch extras.
		public boolean allows(String toolId) {
			return toolId != null && requested.contains(toolId.toLowerCase(Locale.ROOT));
		}

		// Records the first outcome per tool; later calls for the same tool are ignored.
		public boolean settle(String toolId, Outcome outcome, String detail) {
			return entries.putIfAbsent(toolId, new Entry(toolId, outcome, detail)) == null;
		}

		// True when nothing is left unaccounted for.
		public boolean isSettled() {
			return entries.size() >= requested.size();
		}

		// Requested tools with no outcome yet.
		public List<String> outstanding() {
			return requested.stream().filter(id -> !entries.containsKey(id)).toList();
		}
	}

	// runId -> its dispatch plan.
	private final Map<String, Plan> plans = new ConcurrentHashMap<>();

	/** Registers the tools a run was asked to launch. */
	public void register(String runId, List<String> toolIds) {
		plans.put(runId, new Plan(toolIds.stream().map(id -> id.toLowerCase(Locale.ROOT)).toList()));
	}

	/** The plan for a run, or null when the run is not a Kali dispatch. */
	public Plan plan(String runId) {
		return runId == null ? null : plans.get(runId);
	}

	/** True when every requested tool of the run has a recorded outcome. */
	public boolean isSettled(String runId) {
		Plan plan = plan(runId);
		return plan != null && plan.isSettled();
	}

	/** Drops a run's plan once its verdict has been logged. */
	public void clear(String runId) {
		plans.remove(runId);
	}
}