package io.github.iso53.castiel.service;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * One wake-up event channel per chat session.
 *
 * <p>Harness-initiated generations stream through these channels; the frontend opens
 * one SSE connection per open chat and renders/saves what arrives with its existing
 * pipeline. Channels are best-effort transports: output produced while no tab is
 * listening is discarded by design ("lost when the app is closed"), while registry
 * state survives independently so later turns still see accurate process data.
 */
@Service
public class WakeupBus {

	private static final Logger LOG = LoggerFactory.getLogger(WakeupBus.class);

	private final ConcurrentMap<String, Sinks.Many<ServerSentEvent<String>>> channels = new ConcurrentHashMap<>();

	/**
	 * Opens (or returns) the wake-up stream for a chat: buffered recent events plus a
	 * periodic comment that keeps proxies idle-free and surfaces dead connections.
	 */
	public Flux<ServerSentEvent<String>> subscribe(String chatId) {
		Sinks.Many<ServerSentEvent<String>> sink = channels.computeIfAbsent(chatId, id ->
			Sinks.many().unicast().onBackpressureBuffer(new ArrayBlockingQueue<>(128))
		);
		return sink
			.asFlux()
			.mergeWith(
				Flux.interval(Duration.ofSeconds(15)).map(tick ->
					ServerSentEvent.<String>builder().comment("keep-alive").build()
				)
			)
			.doOnCancel(() -> drop(chatId, sink))
			.doOnTerminate(() -> drop(chatId, sink));
	}

	private void drop(String chatId, Sinks.Many<ServerSentEvent<String>> sink) {
		channels.remove(chatId, sink);
		sink.tryEmitComplete();
	}

	/** Best-effort push; never blocks or throws into the generation loop. */
	public void emit(String chatId, ServerSentEvent<String> event) {
		Sinks.Many<ServerSentEvent<String>> sink = channels.get(chatId);
		if (sink == null) {
			LOG.debug("Dropping wake-up event for {}: no frontend listener", chatId);
			return;
		}
		sink.tryEmitNext(event);
	}
}
