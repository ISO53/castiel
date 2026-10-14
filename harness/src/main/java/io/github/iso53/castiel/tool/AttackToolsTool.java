package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.model.AttackTactic;
import io.github.iso53.castiel.model.AttackTechnique;
import io.github.iso53.castiel.service.AttackCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * MITRE ATT&CK lookups over the bundled Enterprise catalog: keyword search to enumerate
 * attack options, and per-technique detail with real-world adversary procedures. Payloads
 * are deliberately lean — models already know what the techniques are; they need the menu,
 * the exact IDs and the playbooks.
 */
@Service
public class AttackToolsTool implements ToolProvider {

	public static final String SEARCH_NAME = "search_attack";
	public static final String DETAIL_NAME = "attack_detail";

	private final AttackCatalog catalog;

	public AttackToolsTool(AttackCatalog catalog) {
		this.catalog = catalog;
	}

	@Tool(
		name = SEARCH_NAME,
		value = {
			"Searches the bundled MITRE ATT&CK Enterprise catalog of adversary techniques by keyword,",
			"tactic, or platform. Use it to enumerate attack options before committing to an attack",
			"plan, and to get exact technique IDs when mapping findings. Returns one short line per",
			"technique; call attack_detail for real-world procedures and associated tooling.",
		}
	)
	public String search(
		@P(
			"Keywords or concept, e.g. 'smb lateral movement', 'credential dumping lsass', 'persistence scheduled task'"
		) String query,
		@P("Optional tactic filter, e.g. 'Lateral Movement', 'reconnaissance', 'privilege-escalation'") String tactic,
		@P("Optional platform filter, e.g. 'Windows', 'Linux', 'IaaS'") String platform,
		@P("Optional maximum results to return between 1 and 15 (default is 8)") Integer limit
	) {
		if (query == null || query.isBlank()) {
			return "Error: query is required";
		}
		if (tactic != null && !tactic.isBlank() && catalog.resolveTactic(tactic).isEmpty()) {
			String valid = catalog.tactics().stream().map(AttackTactic::name).reduce((a, b) -> a + ", " + b).orElse("");
			return "Unknown tactic '" + tactic.strip() + "'. Valid tactics: " + valid;
		}
		if (platform != null && !platform.isBlank() && !catalog.isKnownPlatform(platform)) {
			return "Unknown platform '" + platform.strip() + "'. Valid platforms: " + String.join(", ", catalog.platforms());
		}
		int max = limit == null ? 8 : Math.clamp(limit, 1, 15);
		List<AttackTechnique> results = catalog.search(query, tactic, platform, max);

		if (results.isEmpty()) {
			return "No ATT&CK techniques found matching '" + query.strip() + "'.";
		}

		StringBuilder out = new StringBuilder("Found ").append(results.size()).append(" technique(s):\n\n");
		for (AttackTechnique technique : results) {
			out.append("- ").append(technique.id()).append(" ").append(technique.name());
			if (technique.parent() != null) {
				// Mark the hierarchy level: the parent has no concrete procedure of its own;
				// the sub-technique is usually the actionable one.
				out.append(" (sub of ").append(technique.parent()).append(")");
			}
			out.append(" [").append(displayTactics(technique)).append("]");
			if (!technique.platforms().isEmpty()) {
				out.append(" (").append(String.join(", ", technique.platforms())).append(")");
			}
			out.append("\n  ").append(technique.summary());
			out.append("\n  call attack_detail(\"").append(technique.id()).append("\") for procedures.\n\n");
		}
		return out.toString().stripTrailing();
	}

	@Tool(
		name = DETAIL_NAME,
		value = {
			"Returns one MITRE ATT&CK technique's playbook: real-world procedure examples attributed to",
			"known adversary groups and malware, the tools commonly associated with it, and a reference",
			"link. Technique IDs must be taken from search_attack results, never invented.",
		}
	)
	public String detail(@P("ATT&CK technique id, e.g. 'T1021.002'") String techniqueId) {
		Optional<AttackTechnique> found = catalog.findById(techniqueId);
		if (found.isEmpty()) {
			return "No ATT&CK technique with id '" + (techniqueId == null ? "" : techniqueId.strip()) + "'.";
		}

		AttackTechnique technique = found.get();
		StringBuilder out = new StringBuilder();
		out.append(technique.id()).append(" — ").append(technique.name()).append("\n");
		out.append("Tactics: ").append(displayTactics(technique));
		if (!technique.platforms().isEmpty()) {
			out.append(" | Platforms: ").append(String.join(", ", technique.platforms()));
		}
		out.append("\n");
		if (!technique.permissionsRequired().isEmpty()) {
			out.append("Permissions required: ")
				.append(String.join(", ", technique.permissionsRequired()))
				.append("\n");
		}
		if (technique.remoteSupport() != null) {
			out.append("Can be executed remotely: ")
				.append(technique.remoteSupport() ? "yes" : "no")
				.append("\n");
		}
		if (!technique.summary().isBlank()) {
			out.append("\n").append(technique.summary()).append("\n");
		}
		if (!technique.procedures().isEmpty()) {
			out.append("\nReal-world procedures:\n");
			for (AttackTechnique.Procedure procedure : technique.procedures()) {
				out.append("- ").append(procedure.source()).append(": ").append(procedure.text()).append("\n");
			}
		}
		if (!technique.software().isEmpty()) {
			out.append("\nAssociated software: ").append(String.join(", ", technique.software())).append("\n");
		}
		if (!technique.detection().isBlank()) {
			out.append("\nDetection note: ").append(technique.detection()).append("\n");
		}
		if (technique.url() != null && !technique.url().isBlank()) {
			out.append("Reference: ").append(technique.url()).append("\n");
		}
		return out.toString().stripTrailing();
	}

	private String displayTactics(AttackTechnique technique) {
		return technique
			.tactics()
			.stream()
			.map(catalog::tacticDisplayName)
			.reduce((a, b) -> a + ", " + b)
			.orElse("");
	}
}
