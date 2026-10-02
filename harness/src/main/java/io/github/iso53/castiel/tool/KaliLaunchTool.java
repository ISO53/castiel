package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.agent.AgentRunManager;
import io.github.iso53.castiel.agent.KaliLaunchRegistry;
import io.github.iso53.castiel.model.KaliTool;
import io.github.iso53.castiel.model.KaliToolDto;
import io.github.iso53.castiel.service.HarnessService;
import io.github.iso53.castiel.service.KaliToolCatalog;
import io.github.iso53.castiel.tool.process.ManagedProcess;
import io.github.iso53.castiel.tool.process.ProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Launches one requested Kali tool as a background process on behalf of a dispatch sub-agent.
 *
 * <p>The sub-agent picks the flags but never the binary: the command line is built here from
 * the catalog entry, so a misspelled tool can not become a process that dies on startup. Every
 * launch is recorded in the {@link KaliLaunchRegistry} against the tool id the user selected,
 * which is what lets the harness state afterwards whether every requested tool really started.
 *
 * <p>Arguments are validated to contain no shell operators, so one launch can never smuggle in
 * a second command.
 */
@Service
public class KaliLaunchTool implements ToolProvider {

	public static final String NAME = "kali_launch";

	private static final Logger log = LoggerFactory.getLogger(KaliLaunchTool.class);

	// Marks the purpose line of every dispatch-started process, like the user-started prefix.
	private static final String PURPOSE_PREFIX = "[kali] ";

	// How long to wait after start to notice a process that dies right away.
	private static final long LIVENESS_PROBE_MS = 1_500;

	// Characters that would let an argument chain or substitute another command.
	private static final Pattern SHELL_OPERATORS = Pattern.compile("[;&|$`<>\\n\\r]");

	private final KaliToolCatalog catalog;
	private final ProcessManager processes;
	private final KaliLaunchRegistry registry;
	private final AgentRunManager runs;

	public KaliLaunchTool(
		KaliToolCatalog catalog,
		ProcessManager processes,
		KaliLaunchRegistry registry,
		AgentRunManager runs
	) {
		this.catalog = catalog;
		this.processes = processes;
		this.registry = registry;
		this.runs = runs;
	}

	@Tool(
		name = NAME,
		value = {
			"Launches one of the requested Kali tools as a background process. The binary is chosen",
			"for you from the catalog: pass only the flags and arguments, never the tool name.",
			"This is the ONLY way to start a scan. Use the bash tool instead to look things up",
			"(wordlist locations, tool help, resolving a host); bash is for quick checks only and",
			"is killed after a few seconds.",
			"The process is not monitored by you: the user sees it in the Processes view and you",
			"cannot read its output. Do not poll, do not wait, do not report results back.",
			"Call this once per requested tool. When the last one is handled your work is done.",
		}
	)
	public String launch(
		@P("The tool id exactly as listed in the task, e.g. 'nikto'") String toolId,
		@P("Command-line flags and arguments only, without the tool name") String arguments,
		@P("What this process scans, which target, and how long you expect it to run") String purpose
	) {
		String runId = HarnessService.currentGenerationId();
		KaliLaunchRegistry.Plan plan = registry.plan(runId);
		if (plan == null) {
			log.warn("kali_launch was called by run {}, which has no launch plan", runId);
			return "Error: this run has no Kali launch plan; nothing to dispatch.";
		}

		String id = toolId == null ? "" : toolId.strip().toLowerCase(Locale.ROOT);
		if (!plan.allows(id)) {
			log.warn("Run {} asked to launch '{}', which is outside its plan {}", runId, id, plan.requested());
			return "Error: '" + toolId + "' is not one of the requested tools. Requested: " + String.join(", ", plan.requested());
		}

		KaliLaunchRegistry.Entry existing = plan.entries().get(id);
		if (existing != null) {
			log.debug("Run {} asked for '{}' again; already {}", runId, id, existing.outcome());
			return "Tool '" + id + "' is already handled (" + existing.outcome() + ": " + existing.detail() + "). Do not launch it again.";
		}

		Optional<KaliTool> found = catalog.findById(id);
		if (found.isEmpty()) {
			log.warn("Run {} requested '{}', which is not in the catalog", runId, id);
			plan.settle(id, KaliLaunchRegistry.Outcome.SKIPPED, "not in the tool catalog");
			stopWhenSettled(runId);
			return "Skipped '" + id + "': not in the tool catalog.";
		}

		KaliTool tool = found.get();
		// Refusing a missing binary is the cheapest way to stop an instant "command not found".
		KaliToolDto details = tool.withInstalled(catalog.isInstalled(tool));
		if (!details.installed()) {
			log.info("Run {} skipping '{}': not installed on this host", runId, id);
			plan.settle(id, KaliLaunchRegistry.Outcome.SKIPPED, "not installed on this host");
			stopWhenSettled(runId);
			return "Skipped '" + id + "': not installed on this host. Do not try to launch it.";
		}
		if (tool.commands().isEmpty()) {
			log.warn("Run {} cannot launch '{}': the catalog lists no command binary", runId, id);
			plan.settle(id, KaliLaunchRegistry.Outcome.SKIPPED, "no known command binary");
			stopWhenSettled(runId);
			return "Skipped '" + id + "': the catalog lists no command binary for it.";
		}

		String flags = arguments == null ? "" : arguments.strip();
		if (SHELL_OPERATORS.matcher(flags).find()) {
			log.warn("Run {} passed rejected arguments for '{}': {}", runId, id, flags);
			return "Error: arguments may not contain ; & | $ ` < > or newlines. Pass plain flags only, one tool per call.";
		}

		// The binary comes from the catalog, so the tool name can never be misspelled here.
		String binary = primaryCommand(tool);
		String command = binary + (flags.isEmpty() ? "" : " " + flags);
		log.info("Run {} launching '{}' as: {}", runId, id, command);
		ManagedProcess entry;
		try {
			entry = processes.start(command, PURPOSE_PREFIX + (purpose == null || purpose.isBlank() ? id : purpose.strip()), runId);
		} catch (IOException ex) {
			log.warn("Run {} could not start '{}': {}", runId, id, ex.getMessage());
			plan.settle(id, KaliLaunchRegistry.Outcome.FAILED, "could not start: " + ex.getMessage());
			stopWhenSettled(runId);
			return "Failed to start '" + id + "': " + ex.getMessage();
		}

		String failure = probeForImmediateExit(entry);
		if (failure != null) {
			// Left unsettled on purpose so the sub-agent may correct its flags and retry.
			log.warn("Run {} process for '{}' died immediately: {}", runId, id, failure);
			return "The process for '" + id + "' exited immediately.\n" + failure;
		}

		plan.settle(id, KaliLaunchRegistry.Outcome.LAUNCHED, entry.id());
		log.info("Run {} started '{}' as {} (pid {})", runId, id, entry.id(), entry.osPid());
		stopWhenSettled(runId);
		return "Started " + id + " as " + entry.id() + " (pid " + entry.osPid() + ").";
	}

	/**
	 * Waits briefly for a process that may already be dead. Returns a short explanation when it
	 * exited right away, or null when it is still running. A clean exit is not a failure: a tool
	 * asked only for a version can finish instantly.
	 */
	private String probeForImmediateExit(ManagedProcess entry) {
		try {
			Thread.sleep(LIVENESS_PROBE_MS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		}
		Integer code = entry.exitCodeIfDead();
		// Null means it is still running; zero means it did its job, however briefly.
		if (code == null || code == 0) {
			return null;
		}
		// Leave a marker so whoever reads the output later sees why it ended.
		entry.output().append("\n[harness] process exited immediately with code " + code + "\n");
		return "Exit code: " + code + "\n--- first output ---\n" + firstOutput(entry);
	}

	/**
	 * The binary to run for a tool. A package lists every binary it ships, so the command named
	 * after the tool itself wins: the {@code nmap} package also ships {@code ncat} and
	 * {@code nping}, and running those with nmap's flags fails instantly.
	 */
	private static String primaryCommand(KaliTool tool) {
		String id = tool.id().toLowerCase(Locale.ROOT);
		return tool
			.commands()
			.stream()
			.map(command -> Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT))
			.filter(id::equals)
			.findFirst()
			.orElseGet(() -> Path.of(tool.commands().getFirst()).getFileName().toString());
	}

	// First chunk of a process' output, read through the buffer's own cursor API.
	private static String firstOutput(ManagedProcess entry) {
		String text = entry.output().read(0, 800).text();
		return text.isBlank() ? "(no output)" : text.strip();
	}

	// Ends the run once every requested tool has been launched or explicitly skipped.
	private void stopWhenSettled(String runId) {
		if (!registry.isSettled(runId)) {
			return;
		}
		AgentRunManager.AgentRun run = runs.get(runId);
		if (run == null) {
			log.warn("Plan for run {} settled but the run is no longer registered", runId);
			return;
		}
		log.info("Run {} settled every requested tool; asking it to stop", runId);
		run.requestStop();
	}
}
