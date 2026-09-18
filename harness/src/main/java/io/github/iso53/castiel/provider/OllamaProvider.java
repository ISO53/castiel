package io.github.iso53.castiel.provider;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModelCard;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ollama backend. Ollama exposes thinking as a boolean {@code think} parameter, so the
 * advertised levels per model are {@code on}/{@code off} (detected from the model card's
 * {@code thinking} capability) and nothing otherwise. {@code null} (model default) is
 * handled by omitting the parameter entirely, letting hybrid models behave as trained.
 */
public class OllamaProvider implements LlmProvider {

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
	/** Model metadata ({@code POST /api/show} via {@link OllamaModels}) may load from disk on first call. */
	private static final Duration SHOW_TIMEOUT = Duration.ofSeconds(10);
	/** {@code /api/show} is slow on first hit (can load GGUFs from disk); cache cards per model. */
	private final Map<String, OllamaModelCard> cardCache = new ConcurrentHashMap<>();

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
			OllamaModelCard card = modelCard(ollama, name);
			models.add(new ModelInfo(name, contextLength(card), thinkingLevels(card)));
		}
		return models;
	}

	@Override
	public StreamingChatModel chatModel(String modelName, GenerationOptions options) {
		OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder = OllamaStreamingChatModel.builder()
			.baseUrl(rootUrl())
			.modelName(modelName)
			// Honor the user-configured runtime context window; otherwise Ollama
			// silently truncates history at its own default (~2-4k tokens).
			.numCtx(config.contextWindow())
			.returnThinking(true)
			.timeout(REQUEST_TIMEOUT)
			.logRequests(false)
			.logResponses(false);
		if (options.reasoningEffort() != null) {
			builder.think(!options.isReasoningOff());
		}
		return builder.build();
	}

	/**
	 * Thinking is controllable only for models whose card advertises the {@code thinking}
	 * capability (DeepSeek R1, Qwen3, gpt-oss, ...).
	 */
	private static List<String> thinkingLevels(OllamaModelCard card) {
		List<String> capabilities = card == null ? null : card.getCapabilities();
		return capabilities != null && capabilities.contains("thinking") ? List.of("on", "off") : List.of();
	}

	/** Cached {@code /api/show} lookup; best-effort, returns {@code null} when unavailable. */
	private OllamaModelCard modelCard(OllamaModels ollama, String modelName) {
		return cardCache.computeIfAbsent(modelName, name -> {
			try {
				return ollama.modelCard(name).content();
			} catch (RuntimeException ex) {
				return null;
			}
		});
	}

	/**
	 * Resolves the model's true trained context length from its model card. The card's
	 * {@code model_info} map carries an architecture-specific key (e.g.
	 * {@code llama.context_length}). Best-effort: returns {@code null} when unavailable,
	 * so that model listing keeps working.
	 */
	private static Integer contextLength(OllamaModelCard card) {
		if (card == null) {
			return null;
		}
		Map<String, Object> modelInfo = card.getModelInfo();
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
