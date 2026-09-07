package io.github.iso53.castiel.model;

import java.util.List;

/**
 * Streaming chat request with a full conversation thread.
 *
 * @param providerId      Settings provider id (e.g. {@code llama.cpp}).
 * @param modelName       Model id from the provider catalog.
 * @param messages        Turns not yet persisted. When {@code chatId} is set this is only the
 *                        new turn (usually the user's message); the harness loads the rest of
 *                        the thread from the persisted session. Without {@code chatId} this
 *                        must be the full thread in chronological order
 *                        ({@code user}/{@code assistant}).
 * @param reasoningEffort Optional reasoning level ({@code off}/{@code low}/{@code medium}/{@code high});
 *                        {@code null} uses the model default.
 * @param chatId          Optional persisted chat session id. Enables harness-initiated
 *                        wake-up generations ({@code bg_wait}); {@code null} behaves as before.
 */
public record ChatStreamRequest(
	String providerId,
	String modelName,
	List<ChatTurn> messages,
	String reasoningEffort,
	String chatId
) {}
