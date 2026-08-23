package io.github.iso53.castiel.model;

import java.util.List;

/**
 * One turn in a chat thread sent from the frontend.
 *
 * @param role      {@code user}, {@code assistant} or {@code system}.
 * @param content   Message text.
 * @param toolCalls For assistant turns: tool calls made in this turn together with their
 *                  results, so the harness can rebuild valid tool-call history.
 */
public record ChatTurn(String role, String content, List<ToolCallPayload> toolCalls) {
	public ChatTurn {
		content = content == null ? "" : content;
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}
}
