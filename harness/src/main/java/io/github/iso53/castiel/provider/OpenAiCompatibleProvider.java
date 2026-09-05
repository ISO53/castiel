package io.github.iso53.castiel.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Any server speaking the OpenAI REST dialect ({@code /v1/models}, {@code /v1/chat/completions}):
 * OpenAI itself, LM Studio, llama-server, vLLM, and friends. Generation options map onto
 * the standard {@code reasoning_effort} request parameter; servers that do not implement
 * it simply ignore the field.
 */
public class OpenAiCompatibleProvider implements LlmProvider {

	private static final String FALLBACK_API_KEY = "no-key-required";
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
	private static final Duration CATALOG_TIMEOUT = Duration.ofSeconds(5);
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(CATALOG_TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final ObjectMapper JSON = new ObjectMapper();

	private final LlmProviderConfig config;

	public OpenAiCompatibleProvider(LlmProviderConfig config) {
		this.config = config;
	}

	@Override
	public List<ModelInfo> listModels() {
		Map<String, Object> payload = fetchModels();
		Object data = payload.get("data");
		if (!(data instanceof List<?> entries)) {
			return List.of();
		}
		return entries
			.stream()
			.filter(entry -> entry instanceof Map)
			.map(entry -> toModelInfo((Map<?, ?>) entry))
			.filter(Objects::nonNull)
			.toList();
	}

	private ModelInfo toModelInfo(Map<?, ?> entry) {
		String name = String.valueOf(entry.get("id"));
		if (name == null || name.isBlank() || "null".equals(name)) {
			return null;
		}
		return new ModelInfo(name, firstNumber(entry, "context_length", "max_model_len", "max_input_tokens"));
	}

	/**
	 * Fetches and parses {@code GET /models} directly instead of going through
	 * LangChain4j's catalog: aggregators like OpenRouter return richer fields than the
	 * standard OpenAI shape (e.g. {@code context_length}), which we surface as the model's
	 * context window. Falls back through known alternative field names.
	 */
	private Map<String, Object> fetchModels() {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "models"))
			.timeout(CATALOG_TIMEOUT)
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + apiKey())
			.GET()
			.build();
		HttpResponse<String> response;
		try {
			response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException ex) {
			throw new IllegalStateException("Could not reach " + config.apiUrl(), ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while listing models", ex);
		}
		if (response.statusCode() != 200) {
			throw new IllegalStateException(
				"Model listing failed with HTTP " +
					response.statusCode() +
					(response.body().isBlank() ? "" : ": " + response.body())
			);
		}
		try {
			return JSON.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
		} catch (IOException ex) {
			throw new IllegalStateException("Could not parse the model list", ex);
		}
	}

	/** First present numeric value among the given keys, or {@code null}. */
	private static Integer firstNumber(Map<?, ?> map, String... keys) {
		for (String key : keys) {
			Object value = map.get(key);
			if (value instanceof Number number && number.doubleValue() > 0) {
				return number.intValue();
			}
		}
		return null;
	}

	@Override
	public StreamingChatModel chatModel(String modelName, GenerationOptions options) {
		OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
			.baseUrl(baseUrl())
			.apiKey(apiKey())
			.modelName(modelName)
			.returnThinking(true)
			.timeout(REQUEST_TIMEOUT)
			.logRequests(false)
			.logResponses(false);

		if (options.reasoningEffort() != null && !options.isReasoningOff()) {
			builder.defaultRequestParameters(
				OpenAiChatRequestParameters.builder().reasoningEffort(options.reasoningEffort()).build()
			);
		}
		return builder.build();
	}

	/**
	 * Converts a UI root URL into the base URL LangChain4j expects.
	 */
	private String baseUrl() {
		String root = rootUrl();
		if (root.endsWith("/v1")) {
			return root + "/";
		}
		return root + "/v1/";
	}

	private String rootUrl() {
		String root = config.apiUrl();
		while (root.endsWith("/")) {
			root = root.substring(0, root.length() - 1);
		}
		return root;
	}

	private String apiKey() {
		return config.apiKey() == null || config.apiKey().isBlank() ? FALLBACK_API_KEY : config.apiKey().trim();
	}
}
