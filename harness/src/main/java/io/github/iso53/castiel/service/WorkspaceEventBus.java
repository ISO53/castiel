package io.github.iso53.castiel.service;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Fan-out of workspace file-change pings for the UI's SSE feed. The payload is
 * the changed file's name, or "*" when the writer is unknown (shell commands).
 */
@Service
public class WorkspaceEventBus {

	private final Sinks.Many<String> changes = Sinks.many().replay().latest();

	/** Live change feed for the UI: one ping per detected file change. */
	public Flux<ServerSentEvent<String>> events() {
		return changes.asFlux().map(tick -> ServerSentEvent.builder(tick).build());
	}

	/** Notifies UI subscribers that a file changed; a dropped ping is harmless. */
	public void publish(String fileName) {
		changes.tryEmitNext(fileName);
	}
}