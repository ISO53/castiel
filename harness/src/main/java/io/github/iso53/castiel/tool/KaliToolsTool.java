package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.model.KaliToolDto;
import io.github.iso53.castiel.service.KaliToolCatalog;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lets the orchestrator and sub-agents search the Kali Linux security tools catalog by keyword,
 * task description, or category to discover native tools available on the host.
 */
@Service
public class KaliToolsTool implements ToolProvider {

	public static final String NAME = "search_kali_tools";

	private final KaliToolCatalog catalog;

	public KaliToolsTool(KaliToolCatalog catalog) {
		this.catalog = catalog;
	}

	@Tool(
		name = NAME,
		value = {
			"Searches the catalog of Kali Linux penetration testing and security tools by keywords,",
			"concepts, or category. Use this BEFORE writing custom bash scripts, one-off python",
			"scripts, or generic curl loops to see if a dedicated Kali tool already exists for the job.",
			"Returns tool names, primary command binaries, descriptions, installation status on the",
			"current host, and documentation URLs.",
		}
	)
	public String search(
		@P(
			"Keywords or task description, e.g. 'subdomain enumeration', 'smb relay', 'wifi handshake', 'jwt cracking'"
		) String query,
		@P(
			"Optional category filter, e.g. 'information-gathering', 'web-applications', 'passwords', 'vulnerability'"
		) String category,
		@P("Optional maximum results to return between 1 and 15 (default is 6)") Integer limit
	) {
		if ((query == null || query.isBlank()) && (category == null || category.isBlank())) {
			return "Error: query or category must be specified";
		}
		int max = limit == null ? 6 : Math.clamp(limit, 1, 15);
		List<KaliToolDto> results = catalog.search(query, category, null, max);

		if (results.isEmpty()) {
			return (
				"No Kali tools found matching '" +
				(query != null ? query : "") +
				"' in category '" +
				(category != null ? category : "all") +
				"'."
			);
		}

		StringBuilder out = new StringBuilder();
		out.append("Found ").append(results.size()).append(" tool(s):\n\n");
		for (KaliToolDto tool : results) {
			out.append("- ").append(tool.name());
			if (!tool.commands().isEmpty()) {
				out.append(" (command: ").append(String.join(", ", tool.commands())).append(")");
			}
			out.append(" [")
				.append(tool.installed() ? "INSTALLED ON HOST" : "NOT INSTALLED")
				.append("]\n");
			if (!tool.categories().isEmpty()) {
				out.append("  Categories: ").append(String.join(", ", tool.categories())).append("\n");
			}
			out.append("  Summary: ").append(tool.summary()).append("\n");
			if (tool.docsUrl() != null && !tool.docsUrl().isBlank()) {
				out.append("  Docs: ").append(tool.docsUrl()).append("\n");
			}
			out.append("\n");
		}
		return out.toString().stripTrailing();
	}
}
