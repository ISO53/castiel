package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.ChatStreamRequest;
import io.github.iso53.castiel.service.HarnessService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST & SSE endpoints for streaming AI model interactions.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final HarnessService harnessService;

	public ChatController(HarnessService harnessService) {
		this.harnessService = harnessService;
	}

	/**
	 * Streams AI completion tokens and tool call events as Server-Sent Events (SSE)
	 * for a full chat thread. The event protocol is documented on {@link HarnessService#stream}.
	 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatStreamRequest request) {
		return harnessService.stream(request).onErrorResume(ex ->
			Flux.just(
				ServerSentEvent.<String>builder()
					.event("error")
					.data(ex.getMessage() != null ? ex.getMessage() : "Unknown provider error")
					.build()
			)
		);
	}

	/**
	 * Healthcheck and status verification endpoint.
	 */
	@GetMapping("/health")
	public Mono<Map<String, String>> health() {
		return Mono.just(Map.of("status", "UP", "service", "Castiel Harness AI Streaming"));
	}
}
