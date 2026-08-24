package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.github.iso53.castiel.model.ChatStreamRequest;
import io.github.iso53.castiel.model.ChatTurn;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.tool.ToolProvider;
import io.github.iso53.castiel.tool.UserQuestionTool;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
	private static final int MAX_TOOL_ROUNDS = 64;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final LlmClientFactory llmClientFactory;
	private final UserSettingsService userSettingsService;
	private final UserQuestionTool userQuestionTool;
	private final List<ToolSpecification> toolSpecifications;
	private final Map<String, ToolExecutor> toolExecutors;
	private final ConcurrentMap<String, GenerationState> generations = new ConcurrentHashMap<>();

	/**
	 * Bookkeeping for one in-flight chat stream: the LangChain4j {@link StreamingHandle}
	 * used to cancel it and the token usage accumulated across all its model rounds.
	 */
	private static final class GenerationState {

		final AtomicBoolean cancelled = new AtomicBoolean(false);
		volatile StreamingHandle handle;
		volatile TokenUsage totalUsage;
	}

	public HarnessService(
		LlmClientFactory llmClientFactory,
		UserSettingsService userSettingsService,
		List<ToolProvider> toolProviders
	) {
		this.llmClientFactory = llmClientFactory;
		this.userSettingsService = userSettingsService;

		UserQuestionTool questionTool = null;
		this.toolSpecifications = new ArrayList<>();
		Map<String, ToolExecutor> executors = new LinkedHashMap<>();
		for (ToolProvider provider : toolProviders) {
			if (provider instanceof UserQuestionTool userTool) {
				questionTool = userTool;
			}
			for (Method method : provider.getClass().getMethods()) {
				if (method.isAnnotationPresent(Tool.class)) {
					ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
					this.toolSpecifications.add(specification);
					executors.put(specification.name(), new DefaultToolExecutor(provider, method));
				}
			}
		}
		if (questionTool == null) {
			throw new IllegalStateException(UserQuestionTool.class.getSimpleName() + " bean is missing");
		}
		this.userQuestionTool = questionTool;
		this.toolExecutors = executors;
	}

	/**
	 * Streams tokens (and tool call events) as Server-Sent Events for a full chat thread.
	 *
	 * <p>Event protocol:
	 * <ul>
	 *   <li>{@code start} — JSON {@code {id}} identifying this generation (use with {@link #cancel})</li>
	 *   <li>default event — raw assistant token; thinking is wrapped in {@code <think>} tags</li>
	 *   <li>{@code tool_call} — JSON {@code {id, name, arguments}} when the model calls a tool</li>
	 *   <li>{@code tool_result} — JSON {@code {id, name, result}} once the tool has run</li>
	 *   <li>{@code usage} — JSON token usage totals accumulated across all model rounds so far</li>
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

		String generationId = UUID.randomUUID().toString();
		GenerationState state = new GenerationState();
		generations.put(generationId, state);

		return Flux.create(sink -> {
			sink.onDispose(() -> generations.remove(generationId));
			sink.next(event("start", json(Map.of("id", generationId))));
			streamRound(model, messages, sink, state, 0);
		});
	}

	/**
	 * Cancels an in-flight generation via its LangChain4j {@link StreamingHandle}. The provider
	 * HTTP stream is aborted and no further tool executions or model rounds are started.
	 *
	 * @return {@code true} if a generation with the given id was still running.
	 */
	public boolean cancel(String generationId) {
		if (generationId == null || generationId.isBlank()) {
			return false;
		}
		GenerationState state = generations.remove(generationId);
		if (state == null) {
			return false;
		}
		state.cancelled.set(true);
		StreamingHandle handle = state.handle;
		if (handle != null) {
			handle.cancel();
		}
		return true;
	}

	/**
	 * One round-trip to the model. When the completed response asks for tools, executes them,
	 * appends the results to the thread, and starts another round. Every callback checks the
	 * generation's cancelled flag first, so a cancel request stops the stream immediately.
	 */
	private void streamRound(
		StreamingChatModel model,
		List<ChatMessage> messages,
		FluxSink<ServerSentEvent<String>> sink,
		GenerationState state,
		int round
	) {
		if (state.cancelled.get()) {
			sink.complete();
			return;
		}
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
				public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
					state.handle = context.streamingHandle();
					if (partialThinking != null && partialThinking.text() != null) {
						if (inThinking.compareAndSet(false, true)) {
							sink.next(data("<think>\n"));
						}
						sink.next(data(partialThinking.text()));
					}
				}

				@Override
				public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
					state.handle = context.streamingHandle();
					if (partialResponse != null && partialResponse.text() != null) {
						if (inThinking.compareAndSet(true, false)) {
							sink.next(data("\n</think>\n\n"));
						}
						sink.next(data(partialResponse.text()));
					}
				}

				@Override
				public void onCompleteToolCall(CompleteToolCall completeToolCall) {
					if (state.cancelled.get()) {
						return;
					}
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
					emitUsage(state, response, sink);

					if (inThinking.compareAndSet(true, false)) {
						sink.next(data("\n</think>\n\n"));
					}

					AiMessage message = response.aiMessage();

					// Stop here on cancel: no tool executions, no further model rounds.
					if (state.cancelled.get() || !message.hasToolExecutionRequests()) {
						sink.complete();
						return;
					}

					List<ChatMessage> nextMessages = new ArrayList<>(messages);
					nextMessages.add(message);
					for (int index = 0; index < message.toolExecutionRequests().size(); index++) {
						ToolExecutionRequest request = withId(message.toolExecutionRequests().get(index), index);
						String result = executeTool(request, index);
						sink.next(
							event(
								"tool_result",
								json(Map.of("id", toolCallId(request, index), "name", request.name(), "result", result))
							)
						);
						nextMessages.add(ToolExecutionResultMessage.from(request, result));
					}
					streamRound(model, nextMessages, sink, state, round + 1);
				}

				@Override
				public void onError(Throwable error) {
					if (state.cancelled.get()) {
						// Expected fallout of handle.cancel(): swallow instead of surfacing an error.
						sink.complete();
						return;
					}
					sink.error(error);
				}
			}
		);
	}

	/** Accumulates this round's token usage and reports running totals to the frontend. */
	private void emitUsage(GenerationState state, ChatResponse response, FluxSink<ServerSentEvent<String>> sink) {
		TokenUsage used = response.metadata() == null ? null : response.metadata().tokenUsage();
		if (used == null) {
			return;
		}
		TokenUsage total = TokenUsage.sum(state.totalUsage, used);
		state.totalUsage = total;

		Map<String, Object> payload = new LinkedHashMap<>();
		putIfNotNull(payload, "inputTokens", total.inputTokenCount());
		putIfNotNull(payload, "outputTokens", total.outputTokenCount());
		putIfNotNull(payload, "totalTokens", total.totalTokenCount());
		if (total instanceof OpenAiTokenUsage openAiUsage) {
			if (openAiUsage.inputTokensDetails() != null) {
				putIfNotNull(payload, "cachedInputTokens", openAiUsage.inputTokensDetails().cachedTokens());
			}
			if (openAiUsage.outputTokensDetails() != null) {
				putIfNotNull(payload, "reasoningTokens", openAiUsage.outputTokensDetails().reasoningTokens());
			}
		}
		try {
			sink.next(event("usage", JSON.writeValueAsString(payload)));
		} catch (IOException ignored) {
			// Usage reporting is best-effort; never fail the stream over it.
		}
	}

	private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	private String executeTool(ToolExecutionRequest request, int index) {
		if (UserQuestionTool.NAME.equals(request.name())) {
			// Interactive tool: block this round until the frontend answers via
			// POST /api/chat/questions/{toolCallId}/answer, then feed the answer back to the model.
			return userQuestionTool.awaitUserAnswer(toolCallId(request, index));
		}

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
		String systemPrompt = systemPrompt();
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

	/**
	 * The system prompt from the classpath plus runtime facts the model cannot infer
	 * (host OS, which shell the bash tool uses), so it stops guessing about its platform.
	 * Add more build-specific facts here as needed.
	 */
	private static String systemPrompt() {
		String prompt = readSystemPrompt();
		if (prompt.isBlank()) {
			return "";
		}
		return prompt.stripTrailing() + "\n\n## Environment\n" + environmentContext();
	}

	private static String environmentContext() {
		// Some info about the OS that will help the agent understand its environment
		String info = "Here is some information about the users OS that will be useful for you.\n";
		List<String> OSFacts = List.of(
			"Operating System: " + System.getProperty("os.name", "unknown OS"),
			"File Separator: " + System.getProperty("file.separator", "unknown file separator"),
			"Architecture: " + System.getProperty("os.arch", "unknown architecture")
		);
		return info + String.join("\n", OSFacts);
	}
}
