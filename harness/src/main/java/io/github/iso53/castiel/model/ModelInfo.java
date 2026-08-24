package io.github.iso53.castiel.model;

/**
 * A model advertised by a provider catalog.
 *
 * @param name          Model identifier.
 * @param contextWindow Maximum input tokens advertised by the provider, or {@code null} when unknown.
 */
public record ModelInfo(String name, Integer contextWindow) {

	public ModelInfo(String name) {
		this(name, null);
	}
}
