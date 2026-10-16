package io.github.iso53.castiel.cli;

import io.github.iso53.castiel.update.UpdateCli;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * <p>Spring itself receives no args: castiel owns the command line and hands
 * overrides over as system properties, which outrank application.yaml.</p>
 *
 * <p>Unknown options and stray positional arguments fail fast: picocli prints the
 * error plus usage ("see --help") and the app exits with code 2.</p>
 */
@Command(
	name = "castiel",
	mixinStandardHelpOptions = true, // adds -h/--help and -V/--version
	versionProvider = AppVersionProvider.class,
	description = "AI powered pentesting harness."
)
public final class CastielCommand implements Callable<Integer> {

	/** Enables debug logging for castiel and connected libraries. */
	@Option(names = "--debug", description = "Enable debug logging for castiel.")
	private boolean debug;

	/** Runs the update check flow instead of a normal boot. */
	@Option(names = "--check-update", description = "Compare this build with the latest GitHub release and install a newer one.")
	private boolean checkUpdate;

	/** Overrides the HTTP port from application.yaml (default 8081). */
	@Option(names = { "--port", "-p" }, paramLabel = "PORT", description = "HTTP port to listen on (default: 8081).", converter = CastielCommand.PortConverter.class)
	private Integer port;

	/** Whether Spring should boot after parsing, plus the exit code otherwise. */
	public record ParseResult(boolean boot, int exitCode, CastielCommand cli) {}

	/**
	 * Parses the command line and decides whether a normal boot should follow.
	 * Help/version output and the update flow are terminal: they print their own
	 * output and the JVM exits with the returned code instead of booting.
	 */
	public static ParseResult parse(String[] args) {
		CastielCommand cli = new CastielCommand();
		CommandLine commandLine = new CommandLine(cli);
		int exitCode = commandLine.execute(args);
		boolean boot = exitCode == 0
			&& !cli.checkUpdate
			&& !commandLine.isUsageHelpRequested()
			&& !commandLine.isVersionHelpRequested();
		return new ParseResult(boot, exitCode, cli);
	}

	/**
	 * Maps castiel flags onto Spring property overrides. System properties
	 * outrank application.yaml, so entries here win over the shipped defaults.
	 */
	public Map<String, String> springOverrides() {
		Map<String, String> overrides = new HashMap<>();
		if (debug) {
			overrides.put("spring.profiles.active", "debug");
		}
		if (port != null) {
			overrides.put("server.port", String.valueOf(port));
		}
		return overrides;
	}

	@Override
	public Integer call() {
		if (checkUpdate) {
			// A dedicated flow that owns the terminal; a normal boot never follows it.
			return UpdateCli.run();
		}
		// Normal boot: Spring takes over after this returns 0.
		return 0;
	}

	/** Parses the port value and rejects anything outside 1-65535 at parse time. */
	private static final class PortConverter implements CommandLine.ITypeConverter<Integer> {
		@Override
		public Integer convert(String value) {
			try {
				int port = Integer.parseInt(value);
				if (port >= 1 && port <= 65535) {
					return port;
				}
			} catch (NumberFormatException ignored) {
				// fall through to the shared error below
			}
			throw new CommandLine.TypeConversionException("PORT must be an integer between 1 and 65535");
		}
	}
}
