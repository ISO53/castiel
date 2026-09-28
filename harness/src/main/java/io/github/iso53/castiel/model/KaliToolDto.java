package io.github.iso53.castiel.model;

import java.util.List;

/**
 * Kali tool DTO enriched with installation status on the local system.
 */
public record KaliToolDto(
	String id,
	String name,
	String summary,
	String description,
	List<String> categories,
	List<String> commands,
	String homepage,
	String docsUrl,
	boolean installed
) {}
