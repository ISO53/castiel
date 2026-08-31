package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.ChatSession;
import io.github.iso53.castiel.model.ChatSummary;
import io.github.iso53.castiel.service.ChatPersistenceService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints for managing persisted chat sessions in the active workspace.
 */
@RestController
@RequestMapping("/api/chats")
public class ChatHistoryController {

	private final ChatPersistenceService chatPersistenceService;

	public ChatHistoryController(ChatPersistenceService chatPersistenceService) {
		this.chatPersistenceService = chatPersistenceService;
	}

	/**
	 * Lists all saved chat summaries in the current workspace.
	 */
	@GetMapping
	public List<ChatSummary> listChats() {
		return chatPersistenceService.listChats();
	}

	/**
	 * Retrieves the full content and message history of a saved chat session.
	 */
	@GetMapping("/{id}")
	public ChatSession getChat(@PathVariable("id") String id) {
		try {
			return chatPersistenceService.loadChat(id);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}

	/**
	 * Deletes a chat session from the current workspace.
	 */
	@DeleteMapping("/{id}")
	public Map<String, Boolean> deleteChat(@PathVariable("id") String id) {
		try {
			boolean deleted = chatPersistenceService.deleteChat(id);
			return Map.of("deleted", deleted);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
		}
	}
}
