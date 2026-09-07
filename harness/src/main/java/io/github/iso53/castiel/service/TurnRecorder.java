package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.github.iso53.castiel.model.ChatMessagePart;
import io.github.iso53.castiel.model.ChatSession;
import io.github.iso53.castiel.tool.UserQuestionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists the messages of one session-aware generation to the chat file as they
 * become fully formed: the user turn that started it up front, then the assistant
 * message (streamed text/thinking plus tool calls and their results) updated round
 * by round. Persistence failures are logged and swallowed so they never break a
 * running stream.
 */
final class TurnRecorder {

	private static final Logger log = LoggerFactory.getLogger(TurnRecorder.class);

	private static final ObjectMapper JSON = new ObjectMapper();

	private final String chatId;
	private final ChatPersistenceService chatPersistence;
	private ChatSession session;
	private final String assistantId = UUID.randomUUID().toString();
	private final List<ChatMessagePart> parts = new ArrayList<>();
	private StringBuilder openPart;
	private String openPartType;
	private boolean assistantSaved;

	TurnRecorder(String chatId, ChatPersistenceService chatPersistence) {
		this.chatId = chatId;
		this.chatPersistence = chatPersistence;
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

	/** Persists the assistant message so far. Also used on cancel to keep partial output. */
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
				JsonNode args = JSON.readTree(UserQuestionTool.normalizeArguments(request.name(), request.arguments()));
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
				// Malformed arguments. Fall back to a plain tool part below.
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
