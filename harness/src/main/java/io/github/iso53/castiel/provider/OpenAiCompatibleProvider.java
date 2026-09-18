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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Any server speaking the OpenAI REST dialect ({@code /v1/models}, {@code /v1/chat/completions}):
 * OpenAI itself, llama.cpp, vLLM, LM Studio, OpenRouter, and friends. Reasoning levels are
 * detected per model at listing time (see {@link #thinkingLevels}) and mapped onto the
 * {@code reasoning_effort} request parameter.
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
		return new ModelInfo(
			name,
			firstNumber(entry, "context_length", "max_model_len", "max_input_tokens"),
			thinkingLevels(name, entry)
		);
	}

	/**
	 * Reasoning levels controllable for this model, detected at listing time. Publishing
	 * catalogs (OpenRouter) are authoritative; bare catalogs fall back to well-known
	 * OpenAI ids; everything else gets none. Claiming levels a server ignores is worse
	 * than hiding the knob.
	 */
	private static List<String> thinkingLevels(String name, Map<?, ?> entry) {
		if (!(entry.get("supported_parameters") instanceof List<?> parameters)) {
			return openAiIdLevels(name);
		}
		if (!parameters.contains("reasoning_effort")) {
			// Cannot be controlled via the one parameter this transport can send.
			return List.of();
		}
		if (!(entry.get("reasoning") instanceof Map<?, ?> reasoning) || !reasoning.containsKey("supported_efforts")) {
			// Omitted supported_efforts: no effort selection (OpenRouter docs).
			return List.of();
		}
		if (reasoning.get("supported_efforts") == null) {
			// Explicit null: every gateway effort value is accepted.
			return mandatoryAware(reasoning, ALL_EFFORT_LEVELS);
		}
		if (!(reasoning.get("supported_efforts") instanceof List<?> efforts)) {
			return List.of();
		}
		return mandatoryAware(
			reasoning,
			orderLevels(
				efforts
					.stream()
					.map(level -> (String) level)
					.toList()
			)
		);
	}

	// Every gateway effort value, cheapest to deepest; accepted when supported_efforts is null.
	private static final List<String> ALL_EFFORT_LEVELS = List.of(
		"none",
		"minimal",
		"low",
		"medium",
		"high",
		"xhigh",
		"max"
	);

	/** Drops {@code none} for models that reject disabling reasoning ({@code mandatory: true}). */
	private static List<String> mandatoryAware(Map<?, ?> reasoning, List<String> levels) {
		return Boolean.TRUE.equals(reasoning.get("mandatory"))
			? levels
					.stream()
					.filter(level -> !"none".equals(level))
					.toList()
			: levels;
	}

	/**
	 * Levels for well-known OpenAI ids on bare catalogs. Only bare ids qualify;
	 * provider-prefixed ids on proxy catalogs (Cline, ...) must not inherit OpenAI's
	 * documented defaults.
	 */
	private static List<String> openAiIdLevels(String name) {
		if (name.contains("/")) {
			return List.of();
		}
		String id = name.toLowerCase(Locale.ROOT);
		if (id.startsWith("gpt-5")) {
			return List.of("minimal", "low", "medium", "high");
		}
		if (Pattern.compile("^o[134]([.-].*)?$").matcher(id).find()) {
			return List.of("low", "medium", "high");
		}
		return List.of();
	}

	/** Canonical display/request order for effort levels, cheapest to deepest. */
	private static List<String> orderLevels(List<String> levels) {
		List<String> canonical = List.of("none", "minimal", "low", "medium", "high", "xhigh", "max");
		return levels
			.stream()
			.sorted(
				Comparator.comparingInt(level -> {
					int index = canonical.indexOf(level);
					return index < 0 ? canonical.size() : index;
				})
			)
			.toList();
	}

	/**
	 * Fetches {@code GET /models} directly instead of going through LangChain4j's catalog:
	 * aggregators like OpenRouter return richer fields (e.g. {@code context_length}) than
	 * the standard OpenAI shape.
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
