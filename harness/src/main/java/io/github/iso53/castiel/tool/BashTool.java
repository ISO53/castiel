package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.WorkspaceSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Executes shell commands in the workspace on behalf of the model. */
@Service
public class BashTool implements ToolProvider {

	private static final long DEFAULT_TIMEOUT_MS = 120_000;
	private static final long MAX_TIMEOUT_MS = 600_000;
	private static final int MAX_OUTPUT_CHARS = 50_000;

	private final WorkspaceSession workspace;

	BashTool(WorkspaceSession workspace) {
		this.workspace = workspace;
	}

	@Tool(
		name = "bash",
		value = {
			"Executes a shell command in the current workspace directory and returns its output.",
			"Uses PowerShell on Windows and bash elsewhere.",
			"Prefer read_file/write_file/edit_file for reading and writing files.",
			"The command is killed when the timeout elapses.",
		}
	)
	public String runCommand(
		@P("The command to execute") String command,
		@P("Optional timeout in milliseconds between 1000 and 600000; default is 120000") Integer timeoutMs
	) {
		if (command == null || command.isBlank()) {
			return "Error: command is required";
		}
		long timeout =
			timeoutMs == null ? DEFAULT_TIMEOUT_MS : Math.clamp(timeoutMs.longValue(), 1_000L, MAX_TIMEOUT_MS);

		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		ProcessBuilder builder = windows
			? new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", command)
			: new ProcessBuilder("bash", "-c", command);
		builder.directory(workspace.root().map(Path::toFile).orElse(null));

		try {
			Process process = builder.start();
			StringBuilder stdout = new StringBuilder();
			StringBuilder stderr = new StringBuilder();
			Thread errorReader = Thread.ofVirtual().start(() -> drain(process.getErrorStream(), stderr));
			drain(process.getInputStream(), stdout);
			errorReader.join(5_000);

			if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				return (
					"Error: command timed out after " +
					timeout +
					" ms\n--- output so far ---\n" +
					capOutput(stdout.toString(), stderr.toString())
				);
			}

			return (
				"exit code: " +
				process.exitValue() +
				"\n" +
				capOutput(stdout.toString(), stderr.toString())
			).stripTrailing();
		} catch (IOException ex) {
			return "Error: could not start the command: " + ex.getMessage();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return "Error: interrupted while running the command";
		}
	}

	private static void drain(InputStream input, StringBuilder into) {
		try (input) {
			byte[] chunk = input.readNBytes(4_096);
			while (chunk.length > 0) {
				into.append(new String(chunk));
				chunk = input.readNBytes(4_096);
			}
		} catch (IOException ex) {
			into.append("\n[error reading output: ").append(ex.getMessage()).append(']');
		}
	}

	private static String capOutput(String stdout, String stderr) {
		StringBuilder out = new StringBuilder();
		if (!stdout.isBlank()) {
			out.append("--- stdout ---\n").append(stdout).append('\n');
		}
		if (!stderr.isBlank()) {
			out.append("--- stderr ---\n").append(stderr).append('\n');
		}
		String combined = out.toString();
		return combined.length() <= MAX_OUTPUT_CHARS
			? combined
			: combined.substring(0, MAX_OUTPUT_CHARS) +
					"\n... [output truncated at " +
					MAX_OUTPUT_CHARS +
					" characters]";
	}
}
