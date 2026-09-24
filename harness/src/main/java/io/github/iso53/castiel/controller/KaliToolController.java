package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.agent.SubAgentRunner;
import io.github.iso53.castiel.model.KaliToolDto;
import io.github.iso53.castiel.service.KaliToolCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for discovering Kali Linux tools and launching them via a sub-agent.
 */
@RestController
@RequestMapping("/api/tools/kali")
public class KaliToolController {

	private final KaliToolCatalog catalog;
	private final AgentRunManager runs;
	private final SubAgentRunner subAgentRunner;

	public KaliToolController(KaliToolCatalog catalog, AgentRunManager runs, SubAgentRunner subAgentRunner) {
		this.catalog = catalog;
		this.runs = runs;
		this.subAgentRunner = subAgentRunner;
	}

	@GetMapping
	public Mono<List<KaliToolDto>> search(
		@RequestParam(name = "query", required = false) String query,
		@RequestParam(name = "category", required = false) String category,
		@RequestParam(name = "installedOnly", required = false) Boolean installedOnly,
		@RequestParam(name = "limit", required = false) Integer limit
	) {
		return Mono.just(catalog.search(query, category, installedOnly, limit));
	}

	@GetMapping("/categories")
	public Mono<List<String>> categories() {
		return Mono.just(catalog.categories());
	}

	@PostMapping("/refresh")
	public Mono<Map<String, Object>> refresh() {
		catalog.refreshInstalled();
		return Mono.just(Map.of("status", "ok", "installedCount", catalog.allTools().stream().filter(KaliToolDto::installed).count()));
	}

	public record LaunchToolsRequest(List<String> toolIds, String userNotes) {}

	@PostMapping("/launch")
	public Mono<Map<String, Object>> launch(@RequestBody LaunchToolsRequest request) {
		if (request == null || request.toolIds() == null || request.toolIds().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolIds must not be empty");
		}

		List<String> tools = request.toolIds().stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
		if (tools.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolIds must contain valid tool names");
		}

		String userNotes = (request.userNotes() != null && !request.userNotes().isBlank())
				? request.userNotes().strip()
				: "None provided. Use standard safe defaults conforming to target scope.";

		String taskSummary = "Launch Kali tools: " + String.join(", ", tools);
		if (taskSummary.length() > 120) {
			taskSummary = taskSummary.substring(0, 117) + "...";
		}

		String systemPrompt = """
			You are an automated Kali tool dispatch agent.
			Your purpose is to inspect the active engagement target and scope (from engagement.json or workspace files), \
			evaluate user notes, and launch each requested Kali tool as a managed background process using `bg_start`.
			Do NOT write custom python or shell scripts. Always launch the native Kali tool directly via `bg_start`.
			Provide a clear `purpose` argument for each tool stating what the command does, the target, and expected duration.
			Once all requested tools have been launched with `bg_start`, summarize the launched processes and complete.""";

		String task = """
			User requested to launch the following Kali tool(s): %s.
			User instructions/parameters: "%s"

			Execution steps:
			1. Read engagement.json (or workspace files) if needed to verify target host/domain/IP and current engagement boundaries.
			2. Formulate the precise command line for each requested tool adhering to target scope and user notes.
			3. Launch each tool using `bg_start` with an informative purpose description.
			4. Conclude with a clear list of the launched background processes.""".formatted(String.join(", ", tools), userNotes);

		SubAgentRunner.RunSpec spec = new SubAgentRunner.RunSpec(
			"kali-launcher",
			systemPrompt,
			Set.of("bg_start", "read_file", "workspace_search", "search_kali_tools"),
			task,
			12
		);

		AgentRunManager.AgentRun run = runs.create(null, "kali-launcher", taskSummary);

		Thread.ofVirtual()
			.name("kali-subagent-" + run.id())
			.start(() -> subAgentRunner.execute(run, spec));

		return Mono.just(Map.of(
			"runId", run.id(),
			"status", "started",
			"tools", tools
		));
	}
}
