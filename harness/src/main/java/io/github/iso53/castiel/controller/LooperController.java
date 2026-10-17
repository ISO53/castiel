package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.service.LooperService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lock, unlock, and status endpoints for the looper loop on one chat.
 */
@RestController
@RequestMapping("/api/chat/{chatId}/loop")
public class LooperController {

	private final LooperService looper;

	public LooperController(LooperService looper) {
		this.looper = looper;
	}

	/** Locks the chat into the loop; the loop picks up at the next generation end. */
	@PostMapping
	public Map<String, Object> lock(@PathVariable("chatId") String chatId) {
		try {
			return looper.lock(chatId);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
		}
	}

	/** Unlocks the chat: cancels live looper work and breaks the loop. */
	@DeleteMapping
	public Map<String, Object> unlock(@PathVariable("chatId") String chatId) {
		return looper.unlock(chatId);
	}

	/** Current loop state for this chat: locked, phase in the machine, last failure. */
	@GetMapping
	public Map<String, Object> status(@PathVariable("chatId") String chatId) {
		return looper.status(chatId);
	}
}
