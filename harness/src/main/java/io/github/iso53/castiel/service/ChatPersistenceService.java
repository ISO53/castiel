package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.iso53.castiel.model.ChatSession;
import io.github.iso53.castiel.model.ChatSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persists and loads chat sessions as JSON files in the active workspace under {@code .chats/}.
 */
@Service
public class ChatPersistenceService {

	private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);
	private static final String CHATS_FOLDER = ".chats";

	private final WorkspaceSession workspaceSession;
	private final ObjectMapper objectMapper;

	public ChatPersistenceService(WorkspaceSession workspaceSession) {
		this.workspaceSession = workspaceSession;
		this.objectMapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT);
	}

	/**
	 * Lists all saved chat summaries in the current workspace, sorted newest first.
	 */
	public List<ChatSummary> listChats() {
		Optional<Path> dirOpt = chatsDir();
		if (dirOpt.isEmpty()) {
			return List.of();
		}
		Path dir = dirOpt.get();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}

		List<ChatSummary> summaries = new ArrayList<>();
		try (Stream<Path> stream = Files.list(dir)) {
			stream
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.forEach(path -> {
					try {
						ChatSession session = objectMapper.readValue(path.toFile(), ChatSession.class);
						if (session != null && session.id() != null) {
							summaries.add(ChatSummary.from(session));
						}
					} catch (Exception ex) {
						log.warn("Failed to read chat session from {}: {}", path, ex.getMessage());
					}
				});
		} catch (IOException ex) {
			log.error("Could not list chats directory: {}", dir, ex);
			return List.of();
		}

		summaries.sort(
			Comparator.comparing(
				ChatSummary::updatedAt,
				Comparator.nullsLast(Comparator.reverseOrder())
			)
		);
		return summaries;
	}

	/**
	 * Loads a full chat session by id from the active workspace.
	 */
	public ChatSession loadChat(String id) {
		Path target = resolveChatFile(id);
		if (!Files.isRegularFile(target)) {
			throw new IllegalArgumentException("Chat not found: " + id);
		}
		try {
			return objectMapper.readValue(target.toFile(), ChatSession.class);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not read chat session: " + id, ex);
		}
	}

	/**
	 * Saves or updates a chat session in the active workspace under {@code .chats/<id>.json}.
	 */
	public synchronized ChatSession saveChat(ChatSession session) {
		if (session == null || session.id() == null || session.id().isBlank()) {
			throw new IllegalArgumentException("Chat id is required");
		}
		Path dir = requireChatsDir();
		try {
			Files.createDirectories(dir);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not create .chats directory in workspace: " + dir, ex);
		}

		Path target = resolveChatFile(session.id());

		// Preserve original createdAt if file already exists and session lacks it
		String createdAt = session.createdAt();
		if (Files.isRegularFile(target) && (createdAt == null || createdAt.isBlank())) {
			try {
				ChatSession existing = objectMapper.readValue(target.toFile(), ChatSession.class);
				if (existing != null && existing.createdAt() != null) {
					createdAt = existing.createdAt();
				}
			} catch (Exception ignored) {}
		}
		if (createdAt == null || createdAt.isBlank()) {
			createdAt = Instant.now().toString();
		}

		String updatedAt = Instant.now().toString();
		ChatSession toSave = new ChatSession(
			session.id().trim(),
			session.title() != null && !session.title().isBlank() ? session.title().trim() : "New Chat",
			session.providerId(),
			session.modelName(),
			session.reasoningEffort(),
			createdAt,
			updatedAt,
			session.messages() != null ? session.messages() : List.of()
		);

		try {
			objectMapper.writeValue(target.toFile(), toSave);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not save chat session to " + target, ex);
		}

		return toSave;
	}

	/**
	 * Deletes a chat session file by id.
	 */
	public synchronized boolean deleteChat(String id) {
		Path target = resolveChatFile(id);
		try {
			return Files.deleteIfExists(target);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not delete chat file: " + target, ex);
		}
	}

	private Optional<Path> chatsDir() {
		return workspaceSession.root().map(root -> root.resolve(CHATS_FOLDER).normalize());
	}

	private Path requireChatsDir() {
		return chatsDir().orElseThrow(() ->
			new IllegalArgumentException("No workspace is currently open")
		);
	}

	private Path resolveChatFile(String id) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Chat id is required");
		}
		String cleanId = id.trim();
		if (cleanId.contains("/") || cleanId.contains("\\") || cleanId.contains("..") || cleanId.contains(":")) {
			throw new IllegalArgumentException("Invalid chat id: " + cleanId);
		}
		Path dir = requireChatsDir();
		return dir.resolve(cleanId + ".json").normalize();
	}
}
