package io.github.iso53.castiel.model;

/**
 * Result of probing an LLM provider.
 *
 * @param ok      Whether the provider responded successfully.
 * @param message Short status text for the UI (e.g. {@code Working}).
 */
public record HealthStatus(boolean ok, String message) {
}
