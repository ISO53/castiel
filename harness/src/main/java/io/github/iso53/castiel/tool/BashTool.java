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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes shell commands in the workspace on behalf of the model. */
@Service
public class BashTool implements ToolProvider {

	private static final long DEFAULT_TIMEOUT_MS = 120_000;
	private static final long MAX_TIMEOUT_MS = 600_000;
	private static final int MAX_OUTPUT_CHARS = 50_000;

	// Marker line PowerShell emits before a CLIXML-serialized stream.
	private static final String CLIXML_MARKER = "#< CLIXML";
	// Real error text lives in S elements marked as Error; progress/host records are noise.
	private static final Pattern CLIXML_ERROR = Pattern.compile("<S S=\"Error\">(.*?)</S>", Pattern.DOTALL);
	// CLIXML escapes control characters as _xHHHH_ sequences.
	private static final Pattern CLIXML_HEX_ESCAPE = Pattern.compile("_x([0-9A-Fa-f]{4})_");
	// Error-record trailer lines that only repeat what the message already says.
	private static final Pattern ERROR_BOILERPLATE = Pattern.compile(
		"(?m)^\\s*\\+ (CategoryInfo|FullyQualifiedErrorId)\\b.*$"
	);
	private static final Pattern EXCESS_BLANKS = Pattern.compile("\n{3,}");

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
			"Prefer web_fetch tool for fetching websites, never use bash for web requests.",
			"The command is killed when the timeout elapses.",
			"For commands that run longer than about a minute or need interactive stdin",
			"(nmap scans, metasploit console), use the bg_* background tools instead.",
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
		// Encoded on Windows so embedded double quotes survive the quoting chain.
		ProcessBuilder builder = windows
			? io.github.iso53.castiel.tool.process.ProcessManager.shellCommand(command)
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
					capOutput(cleanStdout(stdout.toString()), cleanStderr(stderr.toString()))
				);
			}

			return (
				"exit code: " +
				process.exitValue() +
				"\n" +
				capOutput(cleanStdout(stdout.toString()), cleanStderr(stderr.toString()))
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

	// Normalizes stdout: LF line endings, collapsed blank runs, no trailing whitespace.
	static String cleanStdout(String stdout) {
		return normalize(stdout, false);
	}


	// Cleans stderr.
	static String cleanStderr(String stderr) {
		String cleaned = stderr.contains(CLIXML_MARKER) ? decodeClixml(stderr) : stderr;
		return normalize(cleaned, true);
	}

	// Concatenates the decoded text of every CLIXML error record, dropping all other records.
	private static String decodeClixml(String stderr) {
		String payload = stderr.substring(stderr.indexOf(CLIXML_MARKER) + CLIXML_MARKER.length());
		StringBuilder out = new StringBuilder(payload.length());
		Matcher errors = CLIXML_ERROR.matcher(payload);
		while (errors.find()) {
			out.append(unescapeClixml(errors.group(1)));
		}
		return out.toString();
	}

	// Resolves _xHHHH_ control-character escapes and the XML entities CLIXML uses.
	private static String unescapeClixml(String text) {
		StringBuilder out = new StringBuilder(text.length());
		Matcher hex = CLIXML_HEX_ESCAPE.matcher(text);
		int copied = 0;
		while (hex.find()) {
			out.append(text, copied, hex.start());
			try {
				out.append((char) Integer.parseInt(hex.group(1), 16));
			} catch (NumberFormatException ignored) {
				out.append(hex.group());
			}
			copied = hex.end();
		}
		out.append(text, copied, text.length());
		return out
			.toString()
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&quot;", "\"")
			.replace("&apos;", "'")
			.replace("&amp;", "&");
	}

	private static String normalize(String text, boolean dropErrorBoilerplate) {
		if (text.isEmpty()) {
			return text;
		}
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		if (dropErrorBoilerplate) {
			normalized = ERROR_BOILERPLATE.matcher(normalized).replaceAll("");
		}
		normalized = EXCESS_BLANKS.matcher(normalized).replaceAll("\n\n");
		return normalized.stripTrailing();
	}
}
