package io.github.iso53.castiel.model;

/**
 * One provider/model pair used by a sub-agent role.
 *
 * @param providerId Settings provider id (e.g. {@code openrouter}).
 * @param modelName  Model id from that provider's catalog.
 */
public record AgentModelRef(String providerId, String modelName) {

	public static final AgentModelRef EMPTY = new AgentModelRef("", "");

	public AgentModelRef {
		providerId = providerId == null ? "" : providerId.strip();
		modelName = modelName == null ? "" : modelName.strip();
	}

	public boolean isBlank() {
		return providerId.isBlank() || modelName.isBlank();
	}
}
