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
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.github.iso53.castiel.agent.AgentGuardrails;
import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.mcp.McpManager;
import io.github.iso53.castiel.model.*;
import io.github.iso53.castiel.provider.GenerationOptions;
import io.github.iso53.castiel.provider.LlmProvider;
import io.github.iso53.castiel.tool.*;
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
import java.nio.file.Files;
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

	/** Inject a workspace-sync checkpoint reminder into the model's context every N tool rounds. */
	private static final int CHECKPOINT_INTERVAL_ROUNDS = 12;

	private static final String CHECKPOINT_REMINDER = """
		[harness checkpoint] You have made many tool calls without finishing. Before continuing, \
		synchronize any confirmed findings into the workspace documents now (network.json, web.json, \
		vulnerabilities.json, evidence.json, tasks.json. Read first, merge, never overwrite existing \
		entries). Then carry on with your remaining work.""";

	/** Arguments preset substituted for tool calls whose streamed arguments arrived truncated. */
	private static final String FAULTY_TOOL_CALL_ARGUMENTS = "{}";

	/** Result fed back for faulty tool calls so the model re-issues them with complete arguments. */
	private static final String FAULTY_TOOL_CALL_RESULT =
		"[harness: this tool call was discarded. Its arguments arrived truncated (not valid JSON), "
			+ "so it was not executed. Re-issue it with complete arguments.]";

	/** Maximum characters kept from a single MCP tool result; larger outputs spill to disk. */
	private static final int MCP_OUTPUT_CHAR_CAP = 16_384;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final LlmClientFactory llmClientFactory;
	private final UserSettingsService userSettingsService;
	private final UserQuestionTool userQuestionTool;
	private final ChatPersistenceService chatPersistence;
	private final McpManager mcpManager;
	private final WorkspaceEventBus workspaceEvents;
	private final WorkspaceSession workspaceSession;
	private final AgentRunManager agentRunManager;
	private final List<ToolSpecification> toolSpecifications;
	private final Map<String, ToolExecutor> toolExecutors;

	/**
	 * Id of the chat generation whose tool executions are running on this thread, or null.
	 * Sub-agent tool execution reads it to link spawned runs to the orchestrator's generation
	 * so a generation cancel can fan out to its sub-agents.
	 */
	private static final ThreadLocal<String> CURRENT_GENERATION = new ThreadLocal<>();

	/**
	 * Filtered toolset handed to a sub-agent: only the specifications and executors whose
	 * names are allowed. MCP tools whose names pass the filter get a delegating executor.
	 */
	public record AgentToolset(List<ToolSpecification> specifications, Map<String, ToolExecutor> executors) {}
	private final ConcurrentMap<String, GenerationState> generations = new ConcurrentHashMap<>();

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

	public HarnessService(
		LlmClientFactory llmClientFactory,
		UserSettingsService userSettingsService,
		ChatPersistenceService chatPersistence,
		McpManager mcpManager,
		WorkspaceEventBus workspaceEvents,
		WorkspaceSession workspaceSession,
		AgentRunManager agentRunManager,
		List<ToolProvider> toolProviders
	) {
		this.llmClientFactory = llmClientFactory;
		this.userSettingsService = userSettingsService;
		this.chatPersistence = chatPersistence;
		this.mcpManager = mcpManager;
		this.workspaceEvents = workspaceEvents;
		this.workspaceSession = workspaceSession;
		this.agentRunManager = agentRunManager;

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
					executors.put(specification.name(), decorate(provider, new DefaultToolExecutor(provider, method)));
				}
			}
		}
		if (questionTool == null) {
			throw new IllegalStateException(UserQuestionTool.class.getSimpleName() + " bean is missing");
		}
		this.userQuestionTool = questionTool;
		this.toolExecutors = executors;
	}

	/** Wraps file-mutating tools so their execution pings the UI's workspace feed. */
	private ToolExecutor decorate(ToolProvider provider, ToolExecutor delegate) {
		if (!(provider instanceof FileWriteTool || provider instanceof FileEditTool
				|| provider instanceof BashTool)) {
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

	/**
	 * Streams tokens (and tool call events) as Server-Sent Events for a full chat thread.
	 *
	 * <p>Event protocol:
	 * <ul>
	 *   <li>{@code start} - JSON {@code {id}} identifying this generation (use with {@link #cancel})</li>
	 *   <li>default event - raw assistant token; thinking is wrapped in {@code <think>} tags</li>
	 *   <li>{@code tool_call} - JSON {@code {id, name, arguments}} when the model calls a tool</li>
	 *   <li>{@code tool_result} - JSON {@code {id, name, result}} once the tool has run</li>
	 *   <li>{@code usage} - JSON token usage totals accumulated across all model rounds so far</li>
	 *   <li>{@code warning} - plain text non-fatal notice (e.g. output truncated by the token limit)</li>
	 *   <li>{@code error} - plain text error message</li>
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
		GenerationOptions options = new GenerationOptions(request.reasoningEffort());
		log.debug("Generation requested: provider={}, model={}, chatId={}",
			request.providerId(), modelName, request.chatId() == null || request.chatId().isBlank() ? "(stateless)" : request.chatId());

		boolean sessionAware = request.chatId() != null && !request.chatId().isBlank();
		String chatId = sessionAware ? request.chatId().trim() : null;
		// Session-aware requests carry only the new turn(s); the durable thread is loaded
		// from persistence by chat id so request bodies stay small regardless of history size.
		List<ChatMessage> messages = buildMessages(resolveTurns(request, sessionAware ? chatId : null));
		TurnRecorder recorder = null;
		if (sessionAware) {
			// The last turn is the message the user just sent. Record it before streaming.
			recorder = new TurnRecorder(chatId, chatPersistence);
			ChatTurn lastTurn = request.messages().getLast();
			if ("user".equalsIgnoreCase(lastTurn.role())) {
				recorder.startUserTurn(lastTurn.content(), request.providerId().trim(), modelName, request.reasoningEffort());
			}
		}

		return openGeneration(provider, modelName, options, messages, recorder);
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
			streamRound(generationId, provider, modelName, options, messages, sink, state, 0);
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
		log.info("Generation {} cancelled", generationId);
		// Any sub-agents spawned by this generation stop with it.
		agentRunManager.cancelByParent(generationId);
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
		String generationId,
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
			log.warn("Generation {} exceeded the {} tool call round limit", generationId, MAX_TOOL_ROUNDS);
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
									UserQuestionTool.normalizeArguments(request.name(), request.arguments())
								)
							)
						)
					);
				}

				@Override
				public void onCompleteResponse(ChatResponse response) {
					emitUsage(state, response, sink);
					reportFinishReason(generationId, round, response, sink);

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
					log.debug("Generation {} round {} → {} tool call(s)",
						generationId, round, message.toolExecutionRequests().size());

					// Replace malformed tool calls with valid presets to prevent 400 errors.
					List<ToolExecutionRequest> original = message.toolExecutionRequests();
					List<ToolExecutionRequest> requests = new ArrayList<>();
					for (ToolExecutionRequest request : original) {
						if (isParsableJsonObject(request.arguments())) {
							requests.add(request);
						} else {
							log.warn("Generation {} round {}: {} tool call had truncated arguments: {}",
								generationId, round, request.name(), request.arguments());
							requests.add(request.toBuilder().arguments(FAULTY_TOOL_CALL_ARGUMENTS).build());
						}
					}
					List<ChatMessage> nextMessages = new ArrayList<>(messages);
					nextMessages.add(new AiMessage(message.text(), requests));
					CURRENT_GENERATION.set(generationId);
					try {
						// Execute first, persist second. Only if it has valid arguments and a result.
						for (int index = 0; index < requests.size(); index++) {
							ToolExecutionRequest request = withId(requests.get(index), index);
							String result = isParsableJsonObject(original.get(index).arguments())
								? executeTool(request, index)
								: FAULTY_TOOL_CALL_RESULT;
							if (recorder != null) recorder.addToolCall(request);
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
					} finally {
						CURRENT_GENERATION.remove();
					}
					// Periodic checkpoint: nudge the model to sync findings into the workspace
					// instead of stockpiling observations across a long tool-call stretch.
					if ((round + 1) % CHECKPOINT_INTERVAL_ROUNDS == 0) {
						nextMessages.add(SystemMessage.from(CHECKPOINT_REMINDER));
					}
					streamRound(generationId, provider, modelName, options, nextMessages, sink, state, round + 1);
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
					log.warn("Generation {} failed", generationId, error);
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
	 * Builds a filtered toolset for a sub-agent: the local tools and MCP tools whose names
	 * are allowed, with forbidden tools (user questions, nesting, edit_file) always removed.
	 * File-mutating executors keep their workspace-notify decorations.
	 */
	public AgentToolset toolsFor(Set<String> allowedNames) {
		Set<String> allowed = allowedNames == null ? Set.of() : Set.copyOf(allowedNames);
		Set<String> seen = new HashSet<>();
		List<ToolSpecification> specifications = new ArrayList<>();
		Map<String, ToolExecutor> executors = new LinkedHashMap<>();
		for (ToolSpecification specification : toolSpecifications) {
			String name = specification.name();
			if (AgentGuardrails.FORBIDDEN_TOOLS.contains(name) || !allowed.contains(name) || !seen.add(name)) {
				continue;
			}
			ToolExecutor executor = toolExecutors.get(name);
			if (executor == null) {
				continue;
			}
			specifications.add(specification);
			executors.put(name, executor);
		}
		for (ToolSpecification specification : mcpManager.toolSpecifications()) {
			String name = specification.name();
			if (!allowed.contains(name) || !seen.add(name)) {
				continue;
			}
			specifications.add(specification);
			executors.put(name, (request, memoryId) -> executeMcpTool(request));
		}
		return new AgentToolset(List.copyOf(specifications), Map.copyOf(executors));
	}

	/** Executes one MCP tool with the same output cap the orchestrator loop applies. */
	public String executeMcpTool(ToolExecutionRequest request) {
		return capMcpOutput(request.name(), mcpManager.executeTool(request));
	}

	/** Current chat generation id for tool execution on this thread; used by sub-agent tools. */
	public static String currentGenerationId() {
		return CURRENT_GENERATION.get();
	}

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
		log.debug("Tool call: {} args={}", request.name(), request.arguments());
		if (UserQuestionTool.NAME.equals(request.name())) {
			// Interactive tool: block this round until the frontend answers via
			// POST /api/chat/questions/{toolCallId}/answer, then feed the answer back to the model.
			return userQuestionTool.awaitUserAnswer(toolCallId(request, index));
		}


		ToolExecutor executor = toolExecutors.get(request.name());
		if (executor == null) {
			// Not a local tool; the MCP manager answers unknown tools the same way.
			// MCP results are unbounded upstream, so cap them before they hit the context.
			return capMcpOutput(request.name(), mcpManager.executeTool(request));
		}
		try {
			return executor.execute(request, null);
		} catch (Exception ex) {
			// The error is fed back to the model (which may recover); log at DEBUG
			// so --debug shows what actually failed tool-side.
			log.debug("Tool {} failed with arguments: {}", request.name(), request.arguments(), ex);
			return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
		}
	}

	/**
	 * Logs the provider's finish reason for the round and warns the UI when the output was
	 * truncated: {@code length} means the output token budget ran out mid-generation, which
	 * can amputate an answer mid-sentence or a tool call's arguments mid-JSON.
	 */
	private void reportFinishReason(String generationId, int round, ChatResponse response,
		FluxSink<ServerSentEvent<String>> sink) {
		FinishReason finishReason = response.metadata() == null ? null : response.metadata().finishReason();
		if (finishReason == null) {
			return;
		}
		log.debug("Generation {} round {} finishReason={}", generationId, round, finishReason);
		if (finishReason == FinishReason.LENGTH) {
			try {
				sink.next(event("warning",
					"Output was cut off by the model's output token limit; the answer or tool call may be incomplete."));
			} catch (RuntimeException ex) {
				// Warnings are best-effort; never fail the stream over one.
				log.debug("Could not deliver truncation warning for generation {}", generationId, ex);
			}
		}
	}

	private static boolean isParsableJsonObject(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			return JSON.readTree(value).isObject();
		} catch (IOException | RuntimeException ex) {
			return false;
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

	/**
	 * Builds the full turn list for a generation. Session-aware requests carry only the new
	 * turn(s) from the frontend; the durable thread is loaded from persistence by chat id.
	 * Incoming turns already present at the tail of persistence (a retry after a failed
	 * generation) are skipped so the model never sees a duplicated user message.
	 */
	private List<ChatTurn> resolveTurns(ChatStreamRequest request, String chatId) {
		if (chatId == null) {
			return request.messages();
		}
		List<ChatTurn> turns;
		try {
			turns = new ArrayList<>(toTurns(chatPersistence.loadChat(chatId).messages()));
		} catch (IllegalArgumentException ex) {
			turns = new ArrayList<>(); // New chat: the session file does not exist yet.
		} catch (Exception ex) {
			log.warn("Could not load chat history for {}; continuing with only the new turns", chatId, ex);
			turns = new ArrayList<>();
		}
		for (ChatTurn incoming : request.messages()) {
			if (!turns.isEmpty() && turns.getLast().equals(incoming)) {
				continue;
			}
			turns.add(incoming);
		}
		return turns;
	}


	private List<ChatMessage> buildMessages(List<ChatTurn> turns) {
		List<ChatMessage> messages = new ArrayList<>();
		String systemPrompt = readSystemPrompt();
		if (!systemPrompt.isBlank()) {
			messages.add(SystemMessage.from(systemPrompt));
		}

		for (ChatTurn turn : HistoryCompactor.compactTurns(turns)) {
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
	 * tool execution requests followed by one {@link ToolExecutionResultMessage} per call.
	 * The shape expected by every LangChain4j provider.
	 */
	private static void appendAssistantTurn(List<ChatMessage> messages, ChatTurn turn) {
		List<ToolExecutionRequest> requests = turn
			.toolCalls()
			.stream()
			.map(call ->
				ToolExecutionRequest
					.builder()
					.id(call.id())
					.name(call.name())
					// Repair malformed ask_user_question options so replayed history is valid.
					.arguments(UserQuestionTool.normalizeArguments(call.name(), call.arguments()))
					.build()
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
			ToolCallPayload call = turn.toolCalls().get(index);
			// Null result would break provider replay after an interrupted turn. Fill it with a placeholder.
			String result = call.result() == null ? TurnRecorder.INTERRUPTED_RESULT : call.result();
			messages.add(ToolExecutionResultMessage.from(requests.get(index), result));
		}
	}

	// ---- MCP output capping ----------------------------------------------------------------

	/** Caps oversized MCP results; the full output is saved into the workspace for re-reading. */
	private String capMcpOutput(String toolName, String result) {
		if (result == null || result.length() <= MCP_OUTPUT_CHAR_CAP) {
			return result;
		}
		String savedTo = saveMcpOutput(toolName, result);
		return result.substring(0, MCP_OUTPUT_CHAR_CAP)
			+ "\n\n[harness: result truncated at " + MCP_OUTPUT_CHAR_CAP + " of " + result.length()
			+ " characters. Full output saved to " + savedTo
			+ " — use read_file with offset/limit to inspect it.]";
	}

	private String saveMcpOutput(String toolName, String content) {
		String safeName = toolName == null ? "tool" : toolName.replaceAll("[^A-Za-z0-9._-]", "_");
		try {
			Path dir = workspaceSession.root()
				.map(root -> root.resolve("evidence").resolve("tool-output"))
				.orElse(null);
			if (dir == null) {
				return "(no workspace open — the remainder is not retained)";
			}
			Files.createDirectories(dir);
			Path target = dir.resolve("mcp-" + safeName + "-" + System.currentTimeMillis() + ".txt");
			Files.writeString(target, content, StandardCharsets.UTF_8);
			workspaceSession.remember(target);
			return "evidence/tool-output/" + target.getFileName();
		} catch (Exception ex) {
			log.warn("Could not save oversized MCP output from {}: {}", toolName, ex.getMessage());
			return "(could not save: " + ex.getMessage() + ")";
		}
	}

	private static String readSystemPrompt() {
		try {
			return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8).trim();
		} catch (IOException | RuntimeException ex) {
			log.error("Could not read system prompt from {}; the agent will run without it", SYSTEM_PROMPT_PATH, ex);
			return "";
		}
	}
}
