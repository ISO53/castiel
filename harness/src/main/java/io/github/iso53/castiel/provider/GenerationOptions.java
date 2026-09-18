package io.github.iso53.castiel.provider;

import java.util.Locale;
import java.util.Objects;

/**
 * Provider-agnostic generation choices made per chat request in the UI. Each
 * {@link LlmProvider} translates these into its native request parameters.
 * Levels that a provider cannot express degrade gracefully there.
 *
 * <p>The valid level tokens are provider- and model-specific; they are advertised
 * per model by {@link LlmProvider#listModels()} (see
 * {@link io.github.iso53.castiel.model.ModelInfo#thinkingLevels()}). The only
 * universally meaningful values are {@code null} (model default, no parameter sent)
 * and {@code off} (suppress thinking where the provider can express it).
 *
 * @param reasoningEffort a level token as advertised by the provider (e.g. {@code on},
 *                        {@code off}, {@code low}, {@code medium}, {@code high},
 *                        {@code minimal}) or {@code null} for the model default.
 */
public record GenerationOptions(String reasoningEffort) {
	public GenerationOptions {
		reasoningEffort =
			reasoningEffort == null || reasoningEffort.isBlank()
				? null
				: reasoningEffort.trim().toLowerCase(Locale.ROOT);
	}

	public static GenerationOptions none() {
		return new GenerationOptions(null);
	}

	public boolean isReasoningOff() {
		return Objects.equals(reasoningEffort, "off");
	}
}
