package io.github.iso53.castiel.model;

import java.util.List;

/**
 * Metadata for a Kali Linux security tool cataloged from kali.org.
 */
public record KaliTool(
	String id,
	String name,
	String summary,
	String description,
	List<String> categories,
	List<String> commands,
	String homepage,
	String docsUrl
) {
	public KaliTool {
		categories = categories == null ? List.of() : List.copyOf(categories);
		commands = commands == null ? List.of() : List.copyOf(commands);
	}

	public KaliToolDto withInstalled(boolean installed) {
		return new KaliToolDto(id, name, summary, description, categories, commands, homepage, docsUrl, installed);
	}
}
