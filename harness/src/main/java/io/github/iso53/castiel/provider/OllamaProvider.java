package io.github.iso53.castiel.provider;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ollama backend. Reasoning effort maps onto Ollama's boolean {@code think} parameter —
 * {@code off} disables thinking, every other level enables it (Ollama has no levels).
 */
public class OllamaProvider implements LlmProvider {

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
	/** Model metadata ({@code POST /api/show} via {@link OllamaModels}) may load from disk on first call. */
	private static final Duration SHOW_TIMEOUT = Duration.ofSeconds(10);

	private final LlmProviderConfig config;

	public OllamaProvider(LlmProviderConfig config) {
		this.config = config;
	}

	@Override
	public List<ModelInfo> listModels() {
		OllamaModels ollama = ollamaModels();
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
			models.add(new ModelInfo(name, contextLength(ollama, name)));
		}
		return models;
	}

	@Override
	public StreamingChatModel chatModel(String modelName, GenerationOptions options) {
		return OllamaStreamingChatModel.builder()
			.baseUrl(rootUrl())
			.modelName(modelName)
			// Honor the user-configured runtime context window; otherwise Ollama
			// silently truncates history at its own default (~2-4k tokens).
			.numCtx(config.contextWindow())
			.think(reasoningEnabled(options))
			.returnThinking(true)
			.timeout(REQUEST_TIMEOUT)
			.logRequests(false)
			.logResponses(false)
			.build();
	}

	private static boolean reasoningEnabled(GenerationOptions options) {
		return options.reasoningEffort() != null && !options.isReasoningOff();
	}

	/**
	 * Resolves the model's true trained context length from its model card. The card's
	 * {@code model_info} map carries an architecture-specific key (e.g.
	 * {@code llama.context_length}). Best-effort: returns {@code null} when unavailable,
	 * so that model listing keeps working.
	 */
	private Integer contextLength(OllamaModels ollama, String modelName) {
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

	private OllamaModels ollamaModels() {
		return OllamaModels.builder()
			.baseUrl(rootUrl())
			.timeout(SHOW_TIMEOUT)
			.logRequests(false)
			.logResponses(false)
			.build();
	}

	private String rootUrl() {
		String root = config.apiUrl();
		while (root.endsWith("/")) {
			root = root.substring(0, root.length() - 1);
		}
		return root;
	}
}
