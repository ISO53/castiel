package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.model.EngagementPhase;
import io.github.iso53.castiel.service.EngagementService;
import io.github.iso53.castiel.service.WorkspaceSession;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tells the model about its runtime environment and the state of the engagement:
 * host OS, workspace location, the current pentest phase, and which engagement
 * documents exist. Replaces the old system-prompt environment blurb.
 */
@Service
public class WorkspaceInfoTool implements ToolProvider {

	/** Same set the workspace scaffolding creates; lets the model notice gaps. */
	private static final List<String> ENGAGEMENT_DOCUMENTS = List.of(
		"engagement.json",
		"network.json",
		"web.json",
		"vulnerabilities.json",
		"evidence.json",
		"tasks.json"
	);

	private final WorkspaceSession workspace;
	private final EngagementService engagement;

	WorkspaceInfoTool(WorkspaceSession workspace, EngagementService engagement) {
		this.workspace = workspace;
		this.engagement = engagement;
	}

	@Tool(
		name = "workspace_info",
		value = {
			"Describes the runtime environment and the state of the current engagement:",
			"operating system and architecture, the workspace root directory, the current",
			"pentest phase with what it allows, and which engagement documents exist.",
			"Call this once at the start of a session and whenever you lose track of context.",
		}
	)
	public String workspaceInfo() {
		StringBuilder info = new StringBuilder();

		info.append("## Environment\n");
		info.append("Operating System: ").append(System.getProperty("os.name", "unknown OS")).append('\n');
		info.append("Architecture: ").append(System.getProperty("os.arch", "unknown architecture")).append('\n');
		info.append("File Separator: ").append(System.getProperty("file.separator", "unknown")).append('\n');

		Path root = workspace.root().orElse(null);
		if (root == null) {
			info.append("\n## Workspace\nNo workspace is open. Ask the user to open or create one.");
			return info.toString();
		}

		info.append("\n## Workspace\n");
		info.append("Root: ").append(root).append('\n');

		info.append("\n## Engagement\n");
		EngagementPhase phase = engagement.currentPhase();
		info.append("Phase ").append(phase.phase()).append(" of ").append(phase.totalPhases())
			.append(": ").append(phase.name()).append('\n');
		info.append(phase.summary()).append('\n');

		info.append("\n## Engagement documents\n");
		for (String document : ENGAGEMENT_DOCUMENTS) {
			info.append("- ").append(document);
			if (!Files.isRegularFile(root.resolve(document))) {
				info.append(" (missing)");
			}
			info.append('\n');
		}
		return info.toString().stripTrailing();
	}
}