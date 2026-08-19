package io.github.iso53.castiel.model;

/**
 * Represents an incoming text generation request.
 *
 * @param systemPrompt Optional system instruction guiding model behavior.
 * @param userMessage  The primary text prompt or question from the user.
 * @param provider     Optional provider overrides for this specific request.
 */
public record AiRequest(String systemPrompt, String userMessage, ProviderConfig provider) {

    public AiRequest {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be null or blank");
        }
    }

    public AiRequest(String systemPrompt, String userMessage) {
        this(systemPrompt, userMessage, null);
    }
}
