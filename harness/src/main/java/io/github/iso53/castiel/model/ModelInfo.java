package io.github.iso53.castiel.model;

import java.util.List;

/**
 * A model advertised by a provider catalog.
 *
 * @param name           Model identifier.
 * @param contextWindow  Maximum input tokens advertised by the provider, or {@code null} when unknown.
 * @param thinkingLevels Reasoning levels the model can be controlled with (e.g. {@code on/off} for
 *                       Ollama, {@code low/medium/high} for OpenAI-style {@code reasoning_effort}),
 *                       ordered from cheapest to deepest. Empty means thinking cannot be controlled
 *                       for this model.
 */
public record ModelInfo(String name, Integer contextWindow, List<String> thinkingLevels) {

	public ModelInfo(String name, Integer contextWindow) {
		this(name, contextWindow, List.of());
	}

	public ModelInfo(String name) {
		this(name, null);
	}
}
