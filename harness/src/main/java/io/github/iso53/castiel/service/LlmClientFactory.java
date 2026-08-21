package io.github.iso53.castiel.service;

import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.iso53.castiel.model.HealthStatus;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ProviderConfig;
import io.github.iso53.castiel.model.ProviderType;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Builds LangChain4j clients from persisted provider config.
 *
 * <p>UI-specific brands are irrelevant here — only {@link ProviderType} matters.
 */
@Service
public class LlmClientFactory {

	private static final String FALLBACK_API_KEY = "no-key-required";
	private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

	/**
	 * Streaming chat model for a persisted provider entry.
	 *
	 * @param config    Persisted provider settings.
	 * @param modelName Model id (chosen later by chat UI; required by the OpenAI API shape).
	 */
	public StreamingChatModel streamingChatModel(LlmProviderConfig config, String modelName) {
		return switch (config.type()) {
			case OPENAI_COMPATIBLE -> OpenAiStreamingChatModel.builder()
				.baseUrl(openAiBaseUrl(config.apiUrl()))
				.apiKey(resolveApiKey(config.apiKey()))
				.modelName(modelName)
				.returnThinking(true)
				.timeout(Duration.ofSeconds(120))
				.logRequests(false)
				.logResponses(false)
				.build();
		};
	}

	/**
	 * Streaming chat model from a per-request {@link ProviderConfig} override
	 * (already uses an OpenAI-compatible base URL including {@code /v1}).
	 */
	public StreamingChatModel streamingChatModel(ProviderConfig config) {
		return OpenAiStreamingChatModel.builder()
			.baseUrl(config.baseUrl())
			.apiKey(resolveApiKey(config.apiKey()))
			.modelName(config.modelName())
			.returnThinking(true)
			.timeout(Duration.ofSeconds(120))
			.logRequests(false)
			.logResponses(false)
			.build();
	}

	/**
	 * Health-check via LangChain4j model catalog ({@code GET /v1/models} for OpenAI-compatible).
	 */
	public HealthStatus healthCheck(LlmProviderConfig config) {
		try {
			ModelCatalog catalog = catalog(config);
			List<?> models = catalog.listModels();
			int count = models != null ? models.size() : 0;
			return new HealthStatus(true, count > 0 ? "Working" : "Working (no models listed)");
		} catch (Exception ex) {
			String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			return new HealthStatus(false, message);
		}
	}

	private ModelCatalog catalog(LlmProviderConfig config) {
		return switch (config.type()) {
			case OPENAI_COMPATIBLE -> OpenAiModelCatalog.builder()
				.baseUrl(openAiBaseUrl(config.apiUrl()))
				.apiKey(resolveApiKey(config.apiKey()))
				.connectTimeout(HEALTH_TIMEOUT)
				.readTimeout(HEALTH_TIMEOUT)
				.logRequests(false)
				.logResponses(false)
				.build();
		};
	}

	/**
	 * Converts a UI root URL into the OpenAI-compatible base URL LangChain4j expects.
	 */
	static String openAiBaseUrl(String apiUrl) {
		String root = apiUrl;
		while (root.endsWith("/")) {
			root = root.substring(0, root.length() - 1);
		}
		if (root.endsWith("/v1")) {
			return root + "/";
		}
		return root + "/v1/";
	}

	private static String resolveApiKey(String apiKey) {
		return (apiKey == null || apiKey.isBlank()) ? FALLBACK_API_KEY : apiKey.trim();
	}
}
