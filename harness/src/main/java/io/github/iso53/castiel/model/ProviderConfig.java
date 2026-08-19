package io.github.iso53.castiel.model;

/**
 * Configuration parameters for an AI Provider endpoint.
 *
 * @param baseUrl   Base URL of the provider API (e.g. http://localhost:8080/v1).
 * @param modelName Target model identifier.
 * @param apiKey    Optional API key (can be dummy or blank for local servers).
 */
public record ProviderConfig(String baseUrl, String modelName, String apiKey) {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080/v1";
    public static final String DEFAULT_MODEL_NAME = "gpt-3.5-turbo";
    public static final String DEFAULT_API_KEY = "no-key-required";

    public ProviderConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = DEFAULT_MODEL_NAME;
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = DEFAULT_API_KEY;
        }
    }

    public static ProviderConfig ofDefault() {
        return new ProviderConfig(DEFAULT_BASE_URL, DEFAULT_MODEL_NAME, DEFAULT_API_KEY);
    }
}
