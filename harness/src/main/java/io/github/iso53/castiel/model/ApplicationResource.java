package io.github.iso53.castiel.model;

/**
 * Classpath resources bundled with the harness.
 */
public enum ApplicationResource {

	SYSTEM_PROMPT("prompts/SYSTEM_PROMPT.md");

	private final String path;

	ApplicationResource(String path) {
		this.path = path;
	}

	public String path() {
		return path;
	}
}
