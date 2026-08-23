package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.iso53.castiel.model.HealthStatus;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;
import io.github.iso53.castiel.model.ProviderType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds LangChain4j clients from persisted provider config.
 *
 * <p>UI-specific brands are irrelevant here - only {@link ProviderType} matters.
 */
@Service
public class LlmClientFactory {

	private static final String FALLBACK_API_KEY = "no-key-required";
	private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(HEALTH_TIMEOUT).build();
	private static final ObjectMapper JSON = new ObjectMapper();

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
				.returnThinking(true)
				.timeout(Duration.ofSeconds(120))
				.logRequests(false)
				.logResponses(false)
				.build();
		};
	}

	/**
	 * Lists models advertised by the provider: via LangChain4j {@link ModelCatalog}
	 * ({@code GET /v1/models}) for OpenAI-compatible servers, or via {@code GET /api/tags}
	 * for Ollama (no catalog support yet in LangChain4j).
	 */
	public List<ModelInfo> listModels(LlmProviderConfig config) {
		List<String> names = switch (config.type()) {
			case OPENAI_COMPATIBLE -> openAiModelNames(config);
			case OLLAMA -> ollamaModelNames(config);
		};
		return names
			.stream()
			.filter(name -> name != null && !name.isBlank())
			.map(ModelInfo::new)
			.toList();
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

	@SuppressWarnings("unchecked")
	private List<String> openAiModelNames(LlmProviderConfig config) {
		ModelCatalog catalog = OpenAiModelCatalog.builder()
			.baseUrl(openAiBaseUrl(config.apiUrl()))
			.apiKey(resolveApiKey(config.apiKey()))
			.connectTimeout(HEALTH_TIMEOUT)
			.readTimeout(HEALTH_TIMEOUT)
			.logRequests(false)
			.logResponses(false)
			.build();

		List<ModelDescription> models = (List<ModelDescription>) (List<?>) catalog.listModels();
		if (models == null || models.isEmpty()) {
			return List.of();
		}
		return models.stream().map(ModelDescription::name).toList();
	}

	private List<String> ollamaModelNames(LlmProviderConfig config) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(rootUrl(config.apiUrl()) + "/api/tags"))
			.timeout(HEALTH_TIMEOUT)
			.GET()
			.build();
		HttpResponse<String> response;
		try {
			response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException ex) {
			throw new IllegalStateException("Could not reach Ollama at " + config.apiUrl(), ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while calling Ollama at " + config.apiUrl(), ex);
		}
		if (response.statusCode() != 200) {
			throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
		}

		JsonNode tags;
		try {
			tags = JSON.readTree(response.body());
		} catch (IOException ex) {
			throw new IllegalStateException("Could not parse Ollama model list", ex);
		}
		List<String> names = new ArrayList<>();
		for (JsonNode model : tags.path("models")) {
			names.add(model.path("name").asText(null));
		}
		return names;
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
