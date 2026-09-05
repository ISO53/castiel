package io.github.iso53.castiel.provider;

import dev.langchain4j.model.chat.StreamingChatModel;
import io.github.iso53.castiel.model.HealthStatus;
import io.github.iso53.castiel.model.ModelInfo;

import java.util.List;

/**
 * One configured LLM backend. Implementations hide everything provider-specific:
 * how models are listed, how a streaming model is built, and how UI-level
 * {@link GenerationOptions} map onto native request parameters.
 *
 * <p>Implementations are lightweight views over an immutable
 * {@link io.github.iso53.castiel.model.LlmProviderConfig} and are created per use — no state.
 */
public interface LlmProvider {
	/**
	 * Models advertised by the backend, each with its context window when known.
	 */
	List<ModelInfo> listModels();

	/**
	 * Streaming chat model ready to serve requests for the given model name, with the
	 * given generation options already applied. Called per request, so implementations
	 * may bake options into the builder instead of per-request parameters.
	 */
	StreamingChatModel chatModel(String modelName, GenerationOptions options);

	/**
	 * Reachability check backed by {@link #listModels()}.
	 */
	default HealthStatus healthCheck() {
		try {
			List<ModelInfo> models = listModels();
			return new HealthStatus(true, models.isEmpty() ? "Working (no models listed)" : "Working");
		} catch (Exception ex) {
			String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			return new HealthStatus(false, message);
		}
	}
}
