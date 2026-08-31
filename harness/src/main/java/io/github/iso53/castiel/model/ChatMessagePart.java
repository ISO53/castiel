package io.github.iso53.castiel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A single segment within a chat message (text, reasoning/thinking, tool call, or questionnaire).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessagePart(
	String type,
	String text,
	String id,
	String name,
	String arguments,
	String result,
	Boolean open,
	String question,
	List<String> options,
	Boolean multiSelect,
	Boolean answered
) {
	// Factories for the part shapes the harness persists.

	public static ChatMessagePart text(String text) {
		return new ChatMessagePart("text", text, null, null, null, null, null, null, null, null, null);
	}

	public static ChatMessagePart thinking(String text) {
		return new ChatMessagePart("thinking", text, null, null, null, null, null, null, null, null, null);
	}

	public static ChatMessagePart tool(String id, String name, String arguments) {
		return new ChatMessagePart("tool", null, id, name, arguments, null, null, null, null, null, null);
	}

	// Returns a copy of this tool part with its result filled in.
	public ChatMessagePart withResult(String result) {
		return new ChatMessagePart(type, text, id, name, arguments, result, open, question, options, multiSelect, answered);
	}
}
