package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.github.iso53.castiel.mcp.McpManager;
import io.github.iso53.castiel.model.*;
import io.github.iso53.castiel.provider.GenerationOptions;
import io.github.iso53.castiel.provider.LlmProvider;
import io.github.iso53.castiel.tool.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

	private static final Logger log = LoggerFactory.getLogger(HarnessService.class);

	private static final String SYSTEM_PROMPT_PATH = "prompts/SYSTEM_PROMPT.md";
	private static final int MAX_TOOL_ROUNDS = 64;

	/**
	 * Safety valve against runaway autonomy: consecutive harness-initiated generations
	 * without a real user message are capped; a genuine user turn resets the counter.
	 */
	private static final int MAX_CONSECUTIVE_WAKEUPS = 15;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final LlmClientFactory llmClientFactory;
	private final UserSettingsService userSettingsService;
	private final UserQuestionTool userQuestionTool;
	private final BackgroundShellTool backgroundShellTool;
	private final ChatPersistenceService chatPersistence;
	private final NudgeScheduler nudgeScheduler;
	private final WakeupBus wakeupBus;
	private final McpManager mcpManager;
	private final WorkspaceEventBus workspaceEvents;
	private final List<ToolSpecification> toolSpecifications;
	private final Map<String, ToolExecutor> toolExecutors;
	private final ConcurrentMap<String, GenerationState> generations = new ConcurrentHashMap<>();

	/** Chat ids with a generation (user- or harness-initiated) currently streaming. */
	private final ConcurrentMap<String, Boolean> busyChats = new ConcurrentHashMap<>();

	/** Consecutive harness-initiated generations per chat, reset by user turns. */
	private final ConcurrentMap<String, Integer> wakeupCounts = new ConcurrentHashMap<>();

	/**
	 * Bookkeeping for one in-flight chat stream: the LangChain4j {@link StreamingHandle}
	 * used to cancel it and the token usage accumulated across all its model rounds.
	 */
	private static final class GenerationState {

		final AtomicBoolean cancelled = new AtomicBoolean(false);
		volatile StreamingHandle handle;
		volatile TokenUsage totalUsage;
		// Set for session-aware generations; null means the stream is not persisted.
		volatile TurnRecorder recorder;
	}

	/**
	 * Persists the messages of one session-aware generation to the chat file as they
	 * become fully formed: the user turn that started it up front, then the assistant
	 * message (streamed text/thinking plus tool calls and their results) updated round
	 * by round. Persistence failures are logged and swallowed so they never break a
	 * running stream.
	 */
	private final class TurnRecorder {

		private final String chatId;
		private ChatSession session;
		private final String assistantId = UUID.randomUUID().toString();
		private final List<ChatMessagePart> parts = new ArrayList<>();
		private StringBuilder openPart;
		private String openPartType;
		private boolean assistantSaved;

		TurnRecorder(String chatId) {
			this.chatId = chatId;
		}

		/** Loads or creates the session file and appends the user turn that starts this generation. */
		synchronized void startUserTurn(String content, String providerId, String modelName, String reasoningEffort) {
			ChatSession loaded;
			try {
				loaded = chatPersistence.loadChat(chatId);
			} catch (Exception ex) {
				loaded = null;
			}
			List<io.github.iso53.castiel.model.ChatMessage> messages =
				loaded == null ? new ArrayList<>() : new ArrayList<>(loaded.messages());
			messages.add(new io.github.iso53.castiel.model.ChatMessage(
				UUID.randomUUID().toString(), "user", List.of(ChatMessagePart.text(content))));
			String title = loaded != null
				? loaded.title()
				: content.isBlank() ? "New Chat" : content.substring(0, Math.min(content.length(), 80)).trim();
			session = new ChatSession(
				chatId,
				title,
				providerId,
				modelName,
				reasoningEffort,
				loaded != null ? loaded.createdAt() : null,
				null,
				messages
			);
			save();
		}

		/** Accumulates streamed text into the open part, starting a new one whenever the type flips. */
		synchronized void appendChunk(String type, String text) {
			if (text == null || text.isEmpty()) {
				return;
			}
			if (openPart != null && !openPartType.equals(type)) {
				boolean wasThinking = "thinking".equals(openPartType);
				flushOpenPart();
				if (wasThinking && "text".equals(type)) {
					// Mirror the frontend: drop the newlines right after a thinking block.
					text = text.replaceFirst("^[\r\n]+", "");
					if (text.isEmpty()) {
						return;
					}
				}
			}
			if (openPart == null) {
				openPart = new StringBuilder();
				openPartType = type;
			}
			openPart.append(text);
		}

		/** Moves any buffered text into the parts list; called when a round completes. */
		synchronized void flushRoundText() {
			flushOpenPart();
		}

		/** Persists the assistant message so far — also used on cancel to keep partial output. */
		synchronized void flushPending() {
			flushOpenPart();
			saveAssistantMessage();
		}

		synchronized void addToolCall(ToolExecutionRequest request) {
			if (session == null) {
				return;
			}
			parts.add(toolPart(request));
			saveAssistantMessage();
		}

		synchronized void completeToolCall(String id, String result) {
			if (session == null) {
				return;
			}
			for (int index = 0; index < parts.size(); index++) {
				ChatMessagePart part = parts.get(index);
				if ("tool".equals(part.type()) && id.equals(part.id())) {
					parts.set(index, part.withResult(result));
					break;
				}
			}
			saveAssistantMessage();
		}

		private void flushOpenPart() {
			if (openPart == null) {
				return;
			}
			if (!openPart.isEmpty()) {
				parts.add("thinking".equals(openPartType)
						? ChatMessagePart.thinking(openPart.toString())
						: ChatMessagePart.text(openPart.toString()));
			}
			openPart = null;
			openPartType = null;
		}

		// Appends the assistant message to the session on first save, then updates it
		// in place (it is always the last message) as more parts complete.
		private void saveAssistantMessage() {
			if (session == null || parts.isEmpty()) {
				return;
			}
			io.github.iso53.castiel.model.ChatMessage assistant =
				new io.github.iso53.castiel.model.ChatMessage(assistantId, "assistant", List.copyOf(parts));
			List<io.github.iso53.castiel.model.ChatMessage> messages = new ArrayList<>(session.messages());
			if (assistantSaved) {
				messages.set(messages.size() - 1, assistant);
			} else {
				messages.add(assistant);
				assistantSaved = true;
			}
			session = new ChatSession(
				session.id(),
				session.title(),
				session.providerId(),
				session.modelName(),
				session.reasoningEffort(),
				session.createdAt(),
				session.updatedAt(),
				messages
			);
			save();
		}

		// Tool parts carry question details for ask_user_question so history renders
		// like the live UI; everything else is a plain id/name/arguments part.
		private ChatMessagePart toolPart(ToolExecutionRequest request) {
			if (UserQuestionTool.NAME.equals(request.name())) {
				try {
					JsonNode args = JSON.readTree(request.arguments() == null ? "{}" : request.arguments());
					List<String> options = new ArrayList<>();
					if (args.path("options").isArray()) {
						args.path("options").forEach(option -> options.add(option.asText()));
					}
					return new ChatMessagePart(
						"tool",
						null,
						request.id(),
						request.name(),
						request.arguments(),
						null,
						null,
						args.path("question").asText("(no question)"),
						options.isEmpty() ? null : List.copyOf(options),
						args.path("multiSelect").asBoolean(false),
						null
					);
				} catch (IOException ex) {
					// Malformed arguments — fall back to a plain tool part below.
				}
			}
			return ChatMessagePart.tool(request.id(), request.name(), request.arguments());
		}

		private void save() {
			try {
				chatPersistence.saveChat(session);
			} catch (Exception ex) {
				log.warn("Could not persist chat {}: {}", chatId, ex.getMessage());
			}
		}
	}

	public HarnessService(
		LlmClientFactory llmClientFactory,
		UserSettingsService userSettingsService,
		ChatPersistenceService chatPersistence,
		NudgeScheduler nudgeScheduler,
		WakeupBus wakeupBus,
		McpManager mcpManager,
		WorkspaceEventBus workspaceEvents,
		List<ToolProvider> toolProviders
	) {
		this.llmClientFactory = llmClientFactory;
		this.userSettingsService = userSettingsService;
		this.chatPersistence = chatPersistence;
		this.nudgeScheduler = nudgeScheduler;
		this.wakeupBus = wakeupBus;
		this.mcpManager = mcpManager;
		this.workspaceEvents = workspaceEvents;

		UserQuestionTool questionTool = null;
		BackgroundShellTool shellTool = null;
		this.toolSpecifications = new ArrayList<>();
		Map<String, ToolExecutor> executors = new LinkedHashMap<>();
		for (ToolProvider provider : toolProviders) {
			if (provider instanceof UserQuestionTool userTool) {
				questionTool = userTool;
			}
			if (provider instanceof BackgroundShellTool backgroundTool) {
				shellTool = backgroundTool;
			}
			for (Method method : provider.getClass().getMethods()) {
				if (method.isAnnotationPresent(Tool.class)) {
					ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
					this.toolSpecifications.add(specification);
					executors.put(specification.name(), decorate(provider, new DefaultToolExecutor(provider, method)));
				}
			}
		}
		if (questionTool == null) {
			throw new IllegalStateException(UserQuestionTool.class.getSimpleName() + " bean is missing");
		}
		if (shellTool == null) {
			throw new IllegalStateException(BackgroundShellTool.class.getSimpleName() + " bean is missing");
		}
		this.userQuestionTool = questionTool;
		this.backgroundShellTool = shellTool;
		this.toolExecutors = executors;
	}

	/** Wraps file-mutating tools so their execution pings the UI's workspace feed. */
	private ToolExecutor decorate(ToolProvider provider, ToolExecutor delegate) {
		if (!(provider instanceof FileWriteTool || provider instanceof FileEditTool
				|| provider instanceof BashTool || provider instanceof BackgroundShellTool)) {
			return delegate;
		}
		boolean reportsPath = provider instanceof FileWriteTool || provider instanceof FileEditTool;
		return new NotifyingExecutor(delegate, workspaceEvents, reportsPath);
	}

	/** Tool executor that publishes a workspace change ping after every run. */
	private static final class NotifyingExecutor implements ToolExecutor {

		private final ToolExecutor delegate;
		private final WorkspaceEventBus workspaceEvents;
		private final boolean reportsPath;

		NotifyingExecutor(ToolExecutor delegate, WorkspaceEventBus workspaceEvents, boolean reportsPath) {
			this.delegate = delegate;
			this.workspaceEvents = workspaceEvents;
			this.reportsPath = reportsPath;
		}

		@Override
		public String execute(ToolExecutionRequest request, Object memoryId) {
			try {
				return delegate.execute(request, memoryId);
			} finally {
				workspaceEvents.publish(changedFile(request));
			}
		}

		private String changedFile(ToolExecutionRequest request) {
			if (!reportsPath) {
				return "*";
			}
			try {
				JsonNode path = JSON.readTree(request.arguments()).get("path");
				if (path != null && path.isTextual() && !path.asText().isBlank()) {
					Path file = Path.of(path.asText());
					return file.getFileName() != null ? file.getFileName().toString() : "*";
				}
			} catch (Exception ignored) {
				// Unknown path falls back to the wildcard ping.
			}
			return "*";
		}
	}

	/** Wires the nudge scheduler to this service once both beans exist. */
	@PostConstruct
	void wireWakeUps() {
		nudgeScheduler.setWakeHandler(token -> {
			int split = token.indexOf('\u0000');
			triggerWakeup(
				split < 0 ? token : token.substring(0, split),
				split < 0 ? "the requested check-in time elapsed" : token.substring(split + 1)
			);
		});
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

		LlmProvider provider = llmClientFactory.create(config);
		String modelName = request.modelName().trim();
		List<ChatMessage> messages = buildMessages(request.messages());
		GenerationOptions options = new GenerationOptions(request.reasoningEffort());

		boolean sessionAware = request.chatId() != null && !request.chatId().isBlank();
		String chatId = sessionAware ? request.chatId().trim() : null;
		TurnRecorder recorder = null;
		if (sessionAware) {
			wakeupCounts.remove(chatId); // Real user activity resets the autonomy guard.
			if (busyChats.putIfAbsent(chatId, Boolean.TRUE) != null) {
				return Flux.error(new IllegalStateException("Another generation is still streaming for this chat"));
			}
			backgroundShellTool.setActiveChatId(chatId);
			// The last turn is the message the user just sent — record it before streaming.
			recorder = new TurnRecorder(chatId);
			ChatTurn lastTurn = request.messages().getLast();
			if ("user".equalsIgnoreCase(lastTurn.role())) {
				recorder.startUserTurn(lastTurn.content(), request.providerId().trim(), modelName, request.reasoningEffort());
			}
		}

		Flux<ServerSentEvent<String>> generation = openGeneration(provider, modelName, options, messages, recorder);
		if (!sessionAware) {
			return generation;
		}
		return generation.doFinally(signal -> {
			busyChats.remove(chatId);
			backgroundShellTool.setActiveChatId(null);
		});
	}

	/** Spawns a full model generation with its own cancellable id and streaming state. */
	private Flux<ServerSentEvent<String>> openGeneration(
		LlmProvider provider,
		String modelName,
		GenerationOptions options,
		List<ChatMessage> messages,
		TurnRecorder recorder
	) {
		String generationId = UUID.randomUUID().toString();
		GenerationState state = new GenerationState();
		state.recorder = recorder;
		generations.put(generationId, state);

		return Flux.create(sink -> {
			sink.onDispose(() -> generations.remove(generationId));
			sink.next(event("start", json(Map.of("id", generationId))));
			streamRound(provider, modelName, options, messages, sink, state, 0);
		});
	}

	/**
	 * Starts a harness-initiated generation: replays the persisted chat thread, injects a
	 * {@code [harness]} instruction turn explaining why the agent was woken, streams the
	 * result into the chat's wake-up channel, and emits a {@code harness_turn} event so the
	 * frontend can persist that instruction as part of the conversation.
	 *
	 * <p>Ownership of {@code busyChats} is acquired here and released in the generation's
	 * {@code doFinally}; all synchronous failure paths release it before returning.
	 */
	public void triggerWakeup(String chatId, String reason) {
		if (chatId == null || chatId.isBlank()) {
			return;
		}
		if (busyChats.putIfAbsent(chatId, Boolean.TRUE) != null) {
			log.info("Skipping wake-up for {}: a generation is already streaming", chatId);
			return;
		}
		try {
			int used = wakeupCounts.merge(chatId, 1, Integer::sum);
			if (used > MAX_CONSECUTIVE_WAKEUPS) {
				log.warn("Wake-up budget exhausted for {}; waiting for human input", chatId);
				busyChats.remove(chatId);
				return;
			}

			ChatSession session = chatPersistence.loadChat(chatId);
			LlmProviderConfig config = userSettingsService.get().requireProvider(session.providerId());
			List<ChatMessage> messages = buildMessages(toTurns(session.messages()));
			String harnessText =
				"[harness] Automated check: " +
				reason +
				". Review your background processes with bg_list/bg_read and continue working.";
			messages.add(UserMessage.from(harnessText));

			LlmProvider provider = llmClientFactory.create(config);
			backgroundShellTool.setActiveChatId(chatId);

			// The harness instruction turn is a real message of the conversation —
			// persist it before streaming so the assistant side attaches to it.
			TurnRecorder recorder = new TurnRecorder(chatId);
			recorder.startUserTurn(harnessText, session.providerId(), session.modelName(), session.reasoningEffort());

			wakeupBus.emit(chatId, event("harness_turn", json(Map.of("role", "user", "content", harnessText))));

			openGeneration(provider, session.modelName(), new GenerationOptions(session.reasoningEffort()), messages, recorder)
				.doFinally(signal -> {
					// Finalize marker for the frontend regardless of how the turn ended,
					// then release the session slot.
					wakeupBus.emit(chatId, event("harness_done", "{}"));
					busyChats.remove(chatId);
					backgroundShellTool.setActiveChatId(null);
				})
				.subscribe(
					sse -> wakeupBus.emit(chatId, sse),
					error -> log.error("Wake-up generation failed for {}", chatId, error)
				);
			log.info("Wake-up generation started for {} ({})", chatId, reason);
		} catch (Exception ex) {
			busyChats.remove(chatId);
			backgroundShellTool.setActiveChatId(null);
			log.error("Could not start wake-up generation for {}: {}", chatId, ex.getMessage());
		}
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
		if (state.recorder != null) {
			state.recorder.flushPending(); // Keep whatever had already streamed when stopping.
		}
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
		LlmProvider provider,
		String modelName,
		GenerationOptions options,
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
		StreamingChatModel model = provider.chatModel(modelName, options);

		model.chat(
			ChatRequest.builder().messages(messages).toolSpecifications(availableTools()).build(),
			new StreamingChatResponseHandler() {
				@Override
				public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
					state.handle = context.streamingHandle();
					if (partialThinking != null && partialThinking.text() != null) {
						if (inThinking.compareAndSet(false, true)) {
							sink.next(data("<think>\n"));
						}
						sink.next(data(partialThinking.text()));
					if (state.recorder != null) state.recorder.appendChunk("thinking", partialThinking.text());
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
					if (state.recorder != null) state.recorder.appendChunk("text", partialResponse.text());
					}
				}

				@Override
				public void onCompleteToolCall(CompleteToolCall completeToolCall) {
					if (state.cancelled.get()) {
						return;
					}
					ToolExecutionRequest request = completeToolCall.toolExecutionRequest();

					// Skip tool calls that came with null id/name. Fucks up the backend
					if (request.id() == null && request.name() == null) {
						log.debug("Skipping tool call with no id and no name. {}", completeToolCall.index());
						return;
					}
					sink.next(
						event(
							"tool_call",
							json(
								Map.of(
									"id",
									toolCallId(request, completeToolCall.index()),
									"name",
									request.name() == null ? "" : request.name(),
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
					TurnRecorder recorder = state.recorder;
					if (recorder != null) recorder.flushRoundText();

					// Stop here on cancel: no tool executions, no further model rounds.
					if (state.cancelled.get() || !message.hasToolExecutionRequests()) {
						if (recorder != null) recorder.flushPending();
						sink.complete();
						return;
					}

					List<ChatMessage> nextMessages = new ArrayList<>(messages);
					nextMessages.add(message);
					for (int index = 0; index < message.toolExecutionRequests().size(); index++) {
						ToolExecutionRequest request = withId(message.toolExecutionRequests().get(index), index);
						if (recorder != null) recorder.addToolCall(request);
						String result = executeTool(request, index);
						if (recorder != null) recorder.completeToolCall(toolCallId(request, index), result);
						sink.next(
							event(
								"tool_result",
								json(
									Map.of(
										"id",
										toolCallId(request, index),
										"name",
										request.name() == null ? "" : request.name(),
										"result",
										result
									)
								)
							)
						);
						nextMessages.add(ToolExecutionResultMessage.from(request, result));
					}
					streamRound(provider, modelName, options, nextMessages, sink, state, round + 1);
				}

				@Override
				public void onError(Throwable error) {
					if (state.cancelled.get()) {
						// Expected fallout of handle.cancel(): swallow instead of surfacing an error.
						sink.complete();
						return;
					}
					// Keep whatever had streamed before the failure, like the UI does.
					if (state.recorder != null) state.recorder.flushPending();
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

	/**
	 * Local tools first, then MCP tools; MCP tools whose name collides with an
	 * already listed tool are skipped.
	 */
	private List<ToolSpecification> availableTools() {
		Set<String> names = new HashSet<>();
		List<ToolSpecification> combined = new ArrayList<>();
		for (ToolSpecification specification : toolSpecifications) {
			if (names.add(specification.name())) {
				combined.add(specification);
			}
		}
		for (ToolSpecification specification : mcpManager.toolSpecifications()) {
			if (names.add(specification.name())) {
				combined.add(specification);
			}
		}
		return combined;
	}

	private String executeTool(ToolExecutionRequest request, int index) {
		if (UserQuestionTool.NAME.equals(request.name())) {
			// Interactive tool: block this round until the frontend answers via
			// POST /api/chat/questions/{toolCallId}/answer, then feed the answer back to the model.
			return userQuestionTool.awaitUserAnswer(toolCallId(request, index));
		}


		ToolExecutor executor = toolExecutors.get(request.name());
		if (executor == null) {
			// Not a local tool; the MCP manager answers unknown tools the same way.
			return mcpManager.executeTool(request);
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

	/**
	 * Converts persisted messages (parts-based) into the flat turn shape the streaming
	 * pipeline consumes. Text parts are joined; tool parts become tool calls, mirroring
	 * how the frontend reconstructs turns from history.
	 */
	private static List<ChatTurn> toTurns(List<io.github.iso53.castiel.model.ChatMessage> persisted) {
		if (persisted == null || persisted.isEmpty()) {
			return List.of();
		}
		List<ChatTurn> turns = new ArrayList<>(persisted.size());
		for (io.github.iso53.castiel.model.ChatMessage message : persisted) {
			if (message == null) {
				continue;
			}
			StringBuilder content = new StringBuilder();
			List<ToolCallPayload> toolCalls = new ArrayList<>();
			for (ChatMessagePart part : message.parts()) {
				switch (part.type() == null ? "" : part.type()) {
					case "text" -> {
						if (part.text() != null) {
							content.append(part.text());
						}
					}
					case "tool" -> {
						if (part.id() != null && part.name() != null) {
							toolCalls.add(new ToolCallPayload(part.id(), part.name(), part.arguments(), part.result()));
						}
					}
					default -> {
						// thinking/questionnaire parts carry no generation-relevant replay data
					}
				}
			}
			turns.add(new ChatTurn(message.role(), content.toString(), toolCalls));
		}
		return turns;
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
