package io.github.iso53.castiel.model;

import java.util.List;

/**
 * Streaming chat request with a full conversation thread.
 *
 * @param providerId Settings provider id (e.g. {@code llama.cpp}).
 * @param modelName  Model id from the provider catalog.
 * @param messages   Full thread in chronological order ({@code user}/{@code assistant}).
 */
public record ChatStreamRequest(String providerId, String modelName, List<ChatTurn> messages) {
}
