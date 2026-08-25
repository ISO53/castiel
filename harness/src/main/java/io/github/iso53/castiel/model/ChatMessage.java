package io.github.iso53.castiel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A message in a persisted chat session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(
	String id,
	String role,
	List<ChatMessagePart> parts
) {
	public ChatMessage {
		parts = parts == null ? List.of() : List.copyOf(parts);
	}
}
