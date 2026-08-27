package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.ChatStreamRequest;
import io.github.iso53.castiel.service.HarnessService;
import io.github.iso53.castiel.service.WakeupBus;
import io.github.iso53.castiel.tool.UserQuestionTool;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST & SSE endpoints for streaming AI model interactions.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final HarnessService harnessService;
	private final UserQuestionTool userQuestionTool;
	private final WakeupBus wakeupBus;

	public ChatController(HarnessService harnessService, UserQuestionTool userQuestionTool, WakeupBus wakeupBus) {
		this.harnessService = harnessService;
		this.userQuestionTool = userQuestionTool;
		this.wakeupBus = wakeupBus;
	}

	/**
	 * Delivers the user's answer to a pending {@code ask_user_question} tool call.
	 * The id is the tool call id the frontend received on the {@code tool_call} SSE event.
	 */
	@PostMapping("/questions/{id}/answer")
	public Mono<Map<String, Boolean>> answerQuestion(@PathVariable("id") String id, @RequestBody AnswerRequest body) {
		boolean delivered = userQuestionTool.completeUserAnswer(id, body.answer());
		if (!delivered) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending question with id: " + id);
		}
		return Mono.just(Map.of("delivered", true));
	}

	/** Request body for answering a pending user question. */
	public record AnswerRequest(String answer) {}

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
	 * Cancels an in-flight chat generation. The id comes from the {@code start} SSE event
	 * emitted at the beginning of a stream. Aborts the provider HTTP stream and prevents
	 * any further tool executions or model rounds for that generation.
	 */
	@PostMapping("/stream/{generationId}/cancel")
	public Mono<Map<String, Boolean>> cancelGeneration(@PathVariable("generationId") String generationId) {
		boolean cancelled = harnessService.cancel(generationId);
		return Mono.just(Map.of("cancelled", cancelled));
	}

	/**
	 * Healthcheck and status verification endpoint.
	 */
	@GetMapping("/health")
	public Mono<Map<String, String>> health() {
		return Mono.just(Map.of("status", "UP", "service", "Castiel Harness AI Streaming"));
	}

	/**
	 * SSE channel carrying harness-initiated generations (bg_wait timers, process exit
	 * notifications) for an open chat. Uses the same event protocol as {@link #streamChat};
	 * tokens/tool events arriving here belong to the same conversation and must be rendered
	 * and saved by the frontend like any user-triggered stream.
	 */
	@GetMapping(value = "/{chatId}/wakeup-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> wakeupStream(@PathVariable("chatId") String chatId) {
		return wakeupBus.subscribe(chatId).onErrorResume(ex ->
				Flux.just(ServerSentEvent.<String>builder()
						.event("error")
						.data(ex.getMessage() == null ? "wake-up stream failed" : ex.getMessage())
						.build()));
	}
}
