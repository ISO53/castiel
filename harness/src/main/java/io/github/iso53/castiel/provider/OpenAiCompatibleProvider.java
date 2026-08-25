package io.github.iso53.castiel.provider;

import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;
import java.time.Duration;
import java.util.List;

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

	private final LlmProviderConfig config;

	public OpenAiCompatibleProvider(LlmProviderConfig config) {
		this.config = config;
	}

	@Override
	public List<ModelInfo> listModels() {
		ModelCatalog catalog = OpenAiModelCatalog.builder()
			.baseUrl(baseUrl())
			.apiKey(apiKey())
			.connectTimeout(CATALOG_TIMEOUT)
			.readTimeout(CATALOG_TIMEOUT)
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
