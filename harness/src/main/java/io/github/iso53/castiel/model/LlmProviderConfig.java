package io.github.iso53.castiel.model;

/**
 * Persisted configuration for a single LLM provider entry.
 *
 * @param type           LangChain4j client family to use.
 * @param apiUrl         Provider root URL as entered in the UI (e.g. {@code http://localhost:8080}).
 *                       For {@link ProviderType#OPENAI_COMPATIBLE} the harness appends {@code /v1}.
 * @param apiKey         API key; for OAuth-backed providers this is the current access token
 *                       rendered as a key (e.g. {@code workos:<token>}) and is refreshed in place.
 * @param contextWindow  Optional context window hint in tokens.
 * @param auth           OAuth credentials when the provider is account-backed (e.g. Cline sign-in),
 *                       or {@code null} for plain API-key providers.
 */
public record LlmProviderConfig(
	ProviderType type,
	String apiUrl,
	String apiKey,
	Integer contextWindow,
	ProviderOAuthCredentials auth
) {
	public static final int DEFAULT_CONTEXT_WINDOW = 8192;

	public LlmProviderConfig {
		if (type == null) {
			type = ProviderType.OPENAI_COMPATIBLE;
		}
		if (apiUrl == null || apiUrl.isBlank()) {
			throw new IllegalArgumentException("apiUrl is required");
		}
		apiUrl = stripTrailingSlash(apiUrl.trim());
		if (apiKey == null) {
			apiKey = "";
		}
		if (contextWindow == null || contextWindow <= 0) {
			contextWindow = DEFAULT_CONTEXT_WINDOW;
		}
	}

	private static String stripTrailingSlash(String url) {
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}
}
