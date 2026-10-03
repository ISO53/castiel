package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.agent.KaliLaunchRegistry;
import io.github.iso53.castiel.agent.SubAgentRunner;
import io.github.iso53.castiel.model.KaliTool;
import io.github.iso53.castiel.model.KaliToolDto;
import io.github.iso53.castiel.service.KaliToolCatalog;
import io.github.iso53.castiel.service.UserSettingsService;
import io.github.iso53.castiel.tool.KaliLaunchTool;
import io.github.iso53.castiel.tool.ToolCatalog;
import io.github.iso53.castiel.util.Text;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * REST controller for discovering Kali Linux tools and launching them via a sub-agent.
 */
@RestController
@RequestMapping("/api/tools/kali")
public class KaliToolController {

	private static final Logger log = LoggerFactory.getLogger(KaliToolController.class);

	private final KaliToolCatalog catalog;
	private final AgentRunManager runs;
	private final SubAgentRunner subAgentRunner;
	private final KaliLaunchRegistry registry;
	private final UserSettingsService userSettingsService;
	private final ToolCatalog toolCatalog;

	public KaliToolController(
		KaliToolCatalog catalog,
		AgentRunManager runs,
		SubAgentRunner subAgentRunner,
		KaliLaunchRegistry registry,
		UserSettingsService userSettingsService,
		ToolCatalog toolCatalog
	) {
		this.catalog = catalog;
		this.runs = runs;
		this.subAgentRunner = subAgentRunner;
		this.registry = registry;
		this.userSettingsService = userSettingsService;
		this.toolCatalog = toolCatalog;
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
		return Mono.just(
			Map.of("status", "ok", "installedCount", catalog.allTools().stream().filter(KaliToolDto::installed).count())
		);
	}

	public record LaunchToolsRequest(List<String> toolIds, String userNotes) {}

	@PostMapping("/launch")
	public Mono<Map<String, Object>> launch(@RequestBody LaunchToolsRequest request) {
		if (request == null || request.toolIds() == null || request.toolIds().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolIds must not be empty");
		}

		List<String> tools = request
			.toolIds()
			.stream()
			.map(String::strip)
			.filter(s -> !s.isEmpty())
			.toList();
		if (tools.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolIds must contain valid tool names");
		}

		// Without kali_launch the dispatcher sub-agent has no way to settle its plan, so the
		// run would sit until the round budget expired. Refuse the dispatch instead.
		if (!toolCatalog.isGroupEnabled("kali_launch", userSettingsService.get().disabledToolGroups())) {
			throw new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Kali launch is switched off in Settings > Tools. Enable it to dispatch scans."
			);
		}

		String userNotes =
			request.userNotes() != null && !request.userNotes().isBlank()
				? request.userNotes().strip()
				: "None provided. Use standard safe defaults conforming to target scope.";

		String taskSummary = "Launch Kali tools: " + String.join(", ", tools);
		if (taskSummary.length() > 120) {
			taskSummary = taskSummary.substring(0, 117) + "...";
		}

		String systemPrompt = """
		You dispatch the Kali tools the user picked. Your whole job is to start them correctly \
		and then stop.

		Rules:
		- Launch each requested tool exactly once with `kali_launch`. Never write your own \
		script and never run a scan through `bash`; the binary is supplied for you.
		- `bash` is only for quick lookups that finish in seconds: finding a wordlist, reading a \
		tool's help, resolving a host. Anything longer belongs in `kali_launch`.
		- The runtime context below already states the operating system, the workspace, the \
		engagement phase and the target scope. Do not probe for them.
		- If a tool is reported as not installed, leave it: it is already handled. Do not retry it.
		- If a launch fails immediately, read the error, fix the flags, and try that tool once more.
		- Give every launch a `purpose` naming the target and the expected runtime.
		- You cannot read process output and you do not report back. When the last tool is \
		handled you are done; do not write a summary.""";

		String task =
			"""
			Launch these Kali tools: %s
			User instructions/parameters: "%s"

			Steps:
			1. Pick the right flags for each tool from its info below and the user's notes.
			2. Call `kali_launch` once per tool.
			3. Stop. Do not summarize; nobody reads a report from you.""".formatted(
				String.join(", ", tools),
				userNotes
			) + describeTools(tools);

		// Round budget is shared with every other sub-agent kind; the dispatcher is
		// not special-cased, so the user's one setting governs all of them.
		SubAgentRunner.RunSpec spec = new SubAgentRunner.RunSpec(
			"kali-launcher",
			userSettingsService.get().kaliModel(),
			systemPrompt,
			// No bg_start: kali_launch is the only way to start a scan, so there is no raw
			// command line for the model to bend into a general-purpose shell.
			Set.of(KaliLaunchTool.NAME, "bash", "read_file", "search_kali_tools"),
			task,
			userSettingsService.get().defaultMaxRounds()
		);

		AgentRunManager.AgentRun run = runs.create("kali-launcher", taskSummary);
		// Register what was requested so coverage can be verified once the run is over.
		registry.register(run.id(), tools);
		log.info("Dispatch {} requested for tools {}", run.id(), tools);

		Thread.ofVirtual()
			.name("kali-subagent-" + run.id())
			.start(() -> {
				// The verdict must run even if the runner fails hard, or the plan leaks and the
				// run never reports which tools it managed to start.
				try {
					subAgentRunner.execute(run, spec);
				} finally {
					reportVerdict(run);
				}
			});

		return Mono.just(Map.of("runId", run.id(), "status", "started", "tools", tools));
	}

	// One line per requested tool with what the harness already knows, so nothing needs probing.
	private String describeTools(List<String> toolIds) {
		StringBuilder out = new StringBuilder("\n\nTool info:\n");
		for (String id : toolIds) {
			Optional<KaliTool> found = catalog.findById(id);
			if (found.isEmpty()) {
				out.append("- ").append(id).append(": not in the catalog\n");
				continue;
			}
			KaliToolDto details = found.get().withInstalled(catalog.isInstalled(found.get()));
			out.append("- ")
				.append(details.id())
				.append(": ")
				.append(details.installed() ? "installed" : "NOT INSTALLED")
				.append(", run it as `")
				.append(details.commands().isEmpty() ? "unknown" : details.commands().getFirst())
				.append("`")
				.append('\n');
			if (details.summary() != null && !details.summary().isBlank()) {
				out.append("  ").append(Text.truncate(details.summary(), 200, "...")).append('\n');
			}
		}
		return out.toString();
	}

	// Logs what the dispatch actually managed to start, then drops the run from the registry.
	private void reportVerdict(AgentRunManager.AgentRun run) {
		KaliLaunchRegistry.Plan plan = registry.plan(run.id());
		if (plan == null) {
			log.warn("Dispatch {} finished but its plan is already gone", run.id());
		} else {
			String settled = plan
				.entries()
				.values()
				.stream()
				.map(entry -> entry.toolId() + "=" + entry.outcome())
				.sorted()
				.toList()
				.toString();
			if (plan.isSettled()) {
				log.info("Dispatch {} settled every tool: {}", run.id(), settled);
			} else {
				String outstanding = String.join(", ", plan.outstanding());
				log.warn(
					"Dispatch {} finished with {} of {} tools never handled: {} | settled: {}",
					run.id(),
					plan.outstanding().size(),
					plan.requested().size(),
					outstanding,
					settled
				);
			}
			registry.clear(run.id());
		}
		// A dispatch is scaffolding: the processes it started are the record worth keeping.
		// Leaving the run tracked would invite the orchestrator to read a transcript nothing needs.
		if (runs.remove(run.id())) {
			log.debug("Dispatch {} removed itself from the agent registry", run.id());
		}
	}
}
