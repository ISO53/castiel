package io.github.iso53.castiel.provider;

import java.util.Locale;
import java.util.Objects;

/**
 * Provider-agnostic generation choices made per chat request in the UI. Each
 * {@link LlmProvider} translates these into its native request parameters —
 * levels that a provider cannot express degrade gracefully there.
 *
 * @param reasoningEffort {@code "off"}, {@code "low"}, {@code "medium"}, {@code "high"} or
 *                        {@code null} for the provider default.
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
