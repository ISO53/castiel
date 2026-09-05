package io.github.iso53.castiel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Full representation of a saved chat session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatSession(
	String id,
	String title,
	String providerId,
	String modelName,
	String reasoningEffort,
	String createdAt,
	String updatedAt,
	List<ChatMessage> messages
) {
	public ChatSession {
		title = title == null ? "New Chat" : title;
		createdAt = createdAt == null ? Instant.now().toString() : createdAt;
		updatedAt = updatedAt == null ? Instant.now().toString() : updatedAt;
		messages = messages == null ? List.of() : List.copyOf(messages);
	}
}
