package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.AiRequest;
import io.github.iso53.castiel.service.HarnessService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
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
	 * Streams AI completion tokens as Server-Sent Events (SSE).
	 *
	 * @param request AI generation prompt, system instruction, and provider configuration.
	 * @return A stream of ServerSentEvents carrying generated token chunks.
	 */
	@PostMapping(
		value = "/stream",
		produces = MediaType.TEXT_EVENT_STREAM_VALUE
	)
	public Flux<ServerSentEvent<String>> streamChat(
		@RequestBody AiRequest request
	) {
		return harnessService
			.stream(request)
			.map(token -> ServerSentEvent.<String>builder().data(token).build())
			.onErrorResume(ex ->
				Flux.just(
					ServerSentEvent.<String>builder()
						.event("error")
						.data(
							ex.getMessage() != null
								? ex.getMessage()
								: "Unknown provider error"
						)
						.build()
				)
			);
	}

	/**
	 * Healthcheck and status verification endpoint.
	 */
	@GetMapping("/health")
	public Mono<Map<String, String>> health() {
		return Mono.just(
			Map.of("status", "UP", "service", "Castiel Harness AI Streaming")
		);
	}
}
