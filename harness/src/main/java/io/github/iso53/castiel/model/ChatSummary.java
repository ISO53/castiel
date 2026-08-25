package io.github.iso53.castiel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lightweight chat summary metadata for listing chat history in the workspace.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatSummary(
	String id,
	String title,
	String providerId,
	String modelName,
	String reasoningEffort,
	String createdAt,
	String updatedAt,
	int messageCount
) {
	public static ChatSummary from(ChatSession session) {
		return new ChatSummary(
			session.id(),
			session.title(),
			session.providerId(),
			session.modelName(),
			session.reasoningEffort(),
			session.createdAt(),
			session.updatedAt(),
			session.messages() != null ? session.messages().size() : 0
		);
	}
}
