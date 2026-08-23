package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.github.iso53.castiel.model.ChatStreamRequest;
import io.github.iso53.castiel.model.ChatTurn;
import io.github.iso53.castiel.model.LlmProviderConfig;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Streams chat completions through LangChain4j using configured providers.
 *
 * <p>Exposes the harness {@code @Tool}s to the model and runs the tool-calling loop:
 * whenever a streamed response contains tool execution requests, the tools are executed,
 * their results are appended to the conversation, and the model is called again until it
 * answers with plain text.
 */
@Service
public class HarnessService {

	private static final String SYSTEM_PROMPT_PATH = "prompts/SYSTEM_PROMPT.md";
	private static final int MAX_TOOL_ROUNDS = 8;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final LlmClientFactory llmClientFactory;
	private final UserSettingsService userSettingsService;
	private final List<ToolSpecification> toolSpecifications;
	private final Map<String, ToolExecutor> toolExecutors;

	public HarnessService(
		LlmClientFactory llmClientFactory,
		UserSettingsService userSettingsService,
		WorkspaceSession workspaceSession
	) {
		this.llmClientFactory = llmClientFactory;
		this.userSettingsService = userSettingsService;

		this.toolSpecifications = ToolSpecifications.toolSpecificationsFrom(workspaceSession);
		Map<String, ToolExecutor> executors = new LinkedHashMap<>();
		for (Method method : workspaceSession.getClass().getMethods()) {
			if (method.isAnnotationPresent(Tool.class)) {
				String name = ToolSpecifications.toolSpecificationFrom(method).name();
				executors.put(name, new DefaultToolExecutor(workspaceSession, method));
			}
		}
		this.toolExecutors = executors;
	}

	/**
	 * Streams tokens (and tool call events) as Server-Sent Events for a full chat thread.
	 *
	 * <p>Event protocol:
	 * <ul>
	 *   <li>default event — raw assistant token; thinking is wrapped in {@code <think>} tags</li>
	 *   <li>{@code tool_call} — JSON {@code {id, name, arguments}} when the model calls a tool</li>
	 *   <li>{@code tool_result} — JSON {@code {id, name, result}} once the tool has run</li>
	 *   <li>{@code error} — plain text error message</li>
	 * </ul>
	 */
	public Flux<ServerSentEvent<String>> stream(ChatStreamRequest request) {
		if (request == null) {
			return Flux.error(new IllegalArgumentException("Request body is required"));
		}
		if (request.providerId() == null || request.providerId().isBlank()) {
			return Flux.error(new IllegalArgumentException("providerId is required"));
		}
		if (request.modelName() == null || request.modelName().isBlank()) {
			return Flux.error(new IllegalArgumentException("modelName is required"));
		}
		if (request.messages() == null || request.messages().isEmpty()) {
			return Flux.error(new IllegalArgumentException("messages must not be empty"));
		}

		LlmProviderConfig config;
		try {
			config = userSettingsService.get().requireProvider(request.providerId().trim());
		} catch (IllegalArgumentException ex) {
			return Flux.error(ex);
		}

		StreamingChatModel model = llmClientFactory.streamingChatModel(config, request.modelName().trim());
		List<ChatMessage> messages = buildMessages(request.messages());

		return Flux.create(sink -> streamRound(model, messages, sink, 0));
	}

	/**
	 * One round-trip to the model. When the completed response asks for tools, executes them,
	 * appends the results to the thread, and starts another round.
	 */
	private void streamRound(
		StreamingChatModel model,
		List<ChatMessage> messages,
		FluxSink<ServerSentEvent<String>> sink,
		int round
	) {
		if (round >= MAX_TOOL_ROUNDS) {
			sink.next(event("error", "The model exceeded " + MAX_TOOL_ROUNDS + " tool call rounds for one message."));
			sink.complete();
			return;
		}

		AtomicBoolean inThinking = new AtomicBoolean(false);

		model.chat(
			ChatRequest.builder().messages(messages).toolSpecifications(toolSpecifications).build(),
			new StreamingChatResponseHandler() {
				@Override
				public void onPartialThinking(PartialThinking partialThinking) {
					if (partialThinking != null && partialThinking.text() != null) {
						if (inThinking.compareAndSet(false, true)) {
							sink.next(data("<think>\n"));
						}
						sink.next(data(partialThinking.text()));
					}
				}

				@Override
				public void onPartialResponse(String partialResponse) {
					if (partialResponse != null) {
						if (inThinking.compareAndSet(true, false)) {
							sink.next(data("\n</think>\n\n"));
						}
						sink.next(data(partialResponse));
					}
				}

				@Override
				public void onCompleteToolCall(CompleteToolCall completeToolCall) {
					ToolExecutionRequest request = completeToolCall.toolExecutionRequest();
					sink.next(
						event(
							"tool_call",
							json(
								Map.of(
									"id",
									toolCallId(request, completeToolCall.index()),
									"name",
									request.name(),
									"arguments",
									request.arguments() == null ? "" : request.arguments()
								)
							)
						)
					);
				}

				@Override
				public void onCompleteResponse(ChatResponse response) {
					if (inThinking.compareAndSet(true, false)) {
						sink.next(data("\n</think>\n\n"));
					}

					AiMessage message = response.aiMessage();
					if (!message.hasToolExecutionRequests()) {
						sink.complete();
						return;
					}

					List<ChatMessage> nextMessages = new ArrayList<>(messages);
					nextMessages.add(message);
					for (int index = 0; index < message.toolExecutionRequests().size(); index++) {
						ToolExecutionRequest request = withId(message.toolExecutionRequests().get(index), index);
						String result = executeTool(request);
						sink.next(
							event(
								"tool_result",
								json(Map.of("id", toolCallId(request, index), "name", request.name(), "result", result))
							)
						);
						nextMessages.add(ToolExecutionResultMessage.from(request, result));
					}
					streamRound(model, nextMessages, sink, round + 1);
				}

				@Override
				public void onError(Throwable error) {
					sink.error(error);
				}
			}
		);
	}

	private String executeTool(ToolExecutionRequest request) {
		ToolExecutor executor = toolExecutors.get(request.name());
		if (executor == null) {
			return "Error: unknown tool \"" + request.name() + "\"";
		}
		try {
			return executor.execute(request, null);
		} catch (Exception ex) {
			return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
		}
	}

	/** Providers such as Ollama may omit tool call ids; fall back to a stable per-round id. */
	private static String toolCallId(ToolExecutionRequest request, int index) {
		return request.id() != null ? request.id() : "call_" + index;
	}

	private static ToolExecutionRequest withId(ToolExecutionRequest request, int index) {
		return request.id() != null ? request : request.toBuilder().id(toolCallId(request, index)).build();
	}

	private static ServerSentEvent<String> data(String payload) {
		return ServerSentEvent.<String>builder().data(payload).build();
	}

	private static ServerSentEvent<String> event(String name, String payload) {
		return ServerSentEvent.<String>builder().event(name).data(payload).build();
	}

	private static String json(Map<String, String> value) {
		try {
			return JSON.writeValueAsString(value);
		} catch (IOException ex) {
			return "{}";
		}
	}

	private List<ChatMessage> buildMessages(List<ChatTurn> turns) {
		List<ChatMessage> messages = new ArrayList<>();
		String systemPrompt = readSystemPrompt();
		if (!systemPrompt.isBlank()) {
			messages.add(SystemMessage.from(systemPrompt));
		}

		for (ChatTurn turn : turns) {
			if (turn == null) {
				continue;
			}
			String role = turn.role() == null ? "" : turn.role().trim().toLowerCase();
			switch (role) {
				case "user" -> messages.add(UserMessage.from(turn.content()));
				case "assistant" -> appendAssistantTurn(messages, turn);
				case "system" -> messages.add(SystemMessage.from(turn.content()));
				default -> throw new IllegalArgumentException("Unsupported message role: " + turn.role());
			}
		}

		if (messages.stream().noneMatch(m -> m instanceof UserMessage)) {
			throw new IllegalArgumentException("Thread must include at least one user message");
		}
		return messages;
	}

	/**
	 * Replays an assistant turn. Turns carrying tool calls become an {@link AiMessage} with
	 * tool execution requests followed by one {@link ToolExecutionResultMessage} per call —
	 * the shape expected by every LangChain4j provider.
	 */
	private static void appendAssistantTurn(List<ChatMessage> messages, ChatTurn turn) {
		List<ToolExecutionRequest> requests = turn
			.toolCalls()
			.stream()
			.map(call ->
				ToolExecutionRequest.builder().id(call.id()).name(call.name()).arguments(call.arguments()).build()
			)
			.toList();

		if (requests.isEmpty()) {
			if (!turn.content().isBlank()) {
				messages.add(AiMessage.from(turn.content()));
			}
			return;
		}

		String text = turn.content().isBlank() ? null : turn.content();
		messages.add(new AiMessage(text, requests));
		for (int index = 0; index < turn.toolCalls().size(); index++) {
			messages.add(ToolExecutionResultMessage.from(requests.get(index), turn.toolCalls().get(index).result()));
		}
	}

	private static String readSystemPrompt() {
		try {
			return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8).trim();
		} catch (IOException | RuntimeException ex) {
			return "";
		}
	}
}
