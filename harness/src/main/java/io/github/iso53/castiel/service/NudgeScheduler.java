package io.github.iso53.castiel.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Delivers "wake up and look at your background processes" signals back to the agent.
 *
 * <p>The model arms a timer with {@code bg_wait(seconds)} and ends its turn; when the
 * timer fires, the registered handler starts a harness-initiated generation for that
 * chat over the frontend's wake-up SSE channel. A second signal source exists: whenever
 * any tracked process exits, pending timers fire immediately so the agent learns of
 * completions sooner than its own interval would have told it.
 */
@Service
public class NudgeScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(NudgeScheduler.class);

	/** Minimum allowed wait; shorter values cost a model round for near-zero information. */
	public static final int MIN_WAIT_SECONDS = 15;

	/** Maximum allowed wait per scheduling call. */
	public static final int MAX_WAIT_SECONDS = 300;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "nudge-scheduler");
		thread.setDaemon(true);
		return thread;
	});

	/** chatId -> the pending wake-up for that chat (zero or one; later calls replace earlier). */
	private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

	private volatile Consumer<String> wakeHandler;

	/** Wires the handler; called once by {@code HarnessService} after construction. */
	public void setWakeHandler(Consumer<String> handler) {
		this.wakeHandler = handler;
	}

	/**
	 * Arms (or replaces) the wake-up timer for a chat. Returns the effective delay.
	 */
	public int schedule(String chatId, int requestedSeconds) {
		int seconds = Math.clamp(requestedSeconds, MIN_WAIT_SECONDS, MAX_WAIT_SECONDS);
		ScheduledFuture<?> previous = pending.remove(chatId);
		if (previous != null) {
			previous.cancel(false);
		}
		ScheduledFuture<?> future = executor.schedule(
			() -> fire(chatId, "the requested check-in time elapsed"),
			seconds,
			java.util.concurrent.TimeUnit.SECONDS
		);
		pending.put(chatId, future);
		return seconds;
	}

	/** Fires every pending wake-up immediately. Called by the process manager when a tracked process exits. */
	public void notifyProcessEvent() {
		for (String chatId : List.copyOf(pending.keySet())) {
			fire(chatId, "a background process changed state");
		}
	}

	private void fire(String chatId, String reason) {
		ScheduledFuture<?> armed = pending.remove(chatId);
		if (armed == null || !armed.cancel(false)) {
			return; // Already dispatched or cancelled.
		}
		Consumer<String> handler = wakeHandler;
		if (handler == null) {
			LOG.warn("Wake-up due for chat {} but no handler is wired", chatId);
			return;
		}
		// Run the generation off the scheduler thread so timing calls stay snappy.
		Thread.startVirtualThread(() -> handler.accept(chatId + "\u0000" + reason));
	}
}
