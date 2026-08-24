package io.github.iso53.castiel.service;

import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.iso53.castiel.model.HealthStatus;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;
import io.github.iso53.castiel.model.ProviderType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds LangChain4j clients from persisted provider config.
 *
 * <p>UI-specific brands are irrelevant here - only {@link ProviderType} matters.
 */
@Service
public class LlmClientFactory {

	private static final String FALLBACK_API_KEY = "no-key-required";
	private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
	/** Model metadata (/api/show via OllamaModels#modelCard) may load from disk on first call. */
	private static final Duration SHOW_TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Streaming chat model for a persisted provider entry.
	 *
	 * @param config    Persisted provider settings.
	 * @param modelName Model id from the provider catalog.
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
			case OLLAMA -> OllamaStreamingChatModel.builder()
				.baseUrl(rootUrl(config.apiUrl()))
				.modelName(modelName)
				// Honor the user-configured runtime context window; otherwise Ollama
				// silently truncates history at its own default (~2-4k tokens).
				.numCtx(config.contextWindow())
				.returnThinking(true)
				.timeout(Duration.ofSeconds(120))
				.logRequests(false)
				.logResponses(false)
				.build();
		};
	}

	/**
	 * Lists models advertised by the provider: via LangChain4j {@link ModelCatalog}
	 * ({@code GET /v1/models}) for OpenAI-compatible servers, or via LangChain4j's
	 * {@link OllamaModels} helper ({@code GET /api/tags} + {@code POST /api/show})
	 * for Ollama, which has no catalog implementation yet. For Ollama, each model's
	 * true context length is additionally resolved from its model card (best-effort).
	 */
	public List<ModelInfo> listModels(LlmProviderConfig config) {
		return switch (config.type()) {
			case OPENAI_COMPATIBLE -> openAiModelNames(config);
			case OLLAMA -> ollamaModelInfos(config);
		};
	}

	/**
	 * Health-check via the provider's model listing endpoint.
	 */
	public HealthStatus healthCheck(LlmProviderConfig config) {
		try {
			List<ModelInfo> models = listModels(config);
			return new HealthStatus(true, models.isEmpty() ? "Working (no models listed)" : "Working");
		} catch (Exception ex) {
			String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			return new HealthStatus(false, message);
		}
	}

	private List<ModelInfo> openAiModelNames(LlmProviderConfig config) {
		ModelCatalog catalog = OpenAiModelCatalog.builder()
			.baseUrl(openAiBaseUrl(config.apiUrl()))
			.apiKey(resolveApiKey(config.apiKey()))
			.connectTimeout(HEALTH_TIMEOUT)
			.readTimeout(HEALTH_TIMEOUT)
			.logRequests(false)
			.logResponses(false)
			.build();

		List<ModelDescription> models = catalog.listModels();
		if (models == null || models.isEmpty()) {
			return List.of();
		}
		return models
			.stream()
			.filter(description -> description.name() != null && !description.name().isBlank())
			.map(description -> new ModelInfo(description.name(), description.maxInputTokens()))
			.toList();
	}

	private List<ModelInfo> ollamaModelInfos(LlmProviderConfig config) {
		OllamaModels ollama = ollamaModels(config.apiUrl());
		List<OllamaModel> available;
		try {
			available = ollama.availableModels().content();
		} catch (RuntimeException ex) {
			throw new IllegalStateException("Could not reach Ollama at " + config.apiUrl(), ex);
		}
		if (available == null) {
			return List.of();
		}
		List<ModelInfo> models = new ArrayList<>();
		for (OllamaModel model : available) {
			String name = model.getName();
			if (name == null || name.isBlank()) {
				continue;
			}
			models.add(new ModelInfo(name, ollamaContextLength(ollama, name)));
		}
		return models;
	}

	/**
	 * Resolves the model's true trained context length from its Ollama model card
	 * (served by {@code POST /api/show}). The card's {@code model_info} map carries
	 * an architecture-specific key (e.g. {@code llama.context_length}). Best-effort:
	 * returns {@code null} when unavailable, so that model listing keeps working.
	 */
	private Integer ollamaContextLength(OllamaModels ollama, String modelName) {
		Map<String, Object> modelInfo;
		try {
			modelInfo = ollama.modelCard(modelName).content().getModelInfo();
		} catch (RuntimeException ex) {
			return null;
		}
		if (modelInfo == null) {
			return null;
		}
		for (Map.Entry<String, Object> field : modelInfo.entrySet()) {
			if (field.getKey().endsWith(".context_length") && field.getValue() instanceof Number number) {
				return number.intValue();
			}
		}
		return null;
	}

	private OllamaModels ollamaModels(String apiUrl) {
		return OllamaModels.builder()
			.baseUrl(rootUrl(apiUrl))
			.timeout(SHOW_TIMEOUT)
			.logRequests(false)
			.logResponses(false)
			.build();
	}

	/**
	 * Converts a UI root URL into the OpenAI-compatible base URL LangChain4j expects.
	 */
	static String openAiBaseUrl(String apiUrl) {
		String root = rootUrl(apiUrl);
		if (root.endsWith("/v1")) {
			return root + "/";
		}
		return root + "/v1/";
	}

	private static String rootUrl(String url) {
		String root = url;
		while (root.endsWith("/")) {
			root = root.substring(0, root.length() - 1);
		}
		return root;
	}

	private static String resolveApiKey(String apiKey) {
		return apiKey == null || apiKey.isBlank() ? FALLBACK_API_KEY : apiKey.trim();
	}
}
