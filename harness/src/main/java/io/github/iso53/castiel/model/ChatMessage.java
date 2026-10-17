package io.github.iso53.castiel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A message in a persisted chat session.
 *
 * @param origin Optional sender tag for user messages; {@code "looper"} marks a
 *               directive the looper agent wrote. UI-only: the model never sees it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(
	String id,
	String role,
	List<ChatMessagePart> parts,
	String origin
) {
	public ChatMessage {
		parts = parts == null ? List.of() : List.copyOf(parts);
	}
}
