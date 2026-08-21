package io.github.iso53.castiel.model;

/**
 * Response after saving a provider entry and running a health check.
 *
 * @param settings Updated persisted settings.
 * @param health   Health-check result.
 */
public record ProviderConnectResponse(UserSettings settings, HealthStatus health) {
}
