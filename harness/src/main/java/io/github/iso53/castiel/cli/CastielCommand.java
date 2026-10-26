package io.github.iso53.castiel.cli;

import io.github.iso53.castiel.service.AuthStore;
import io.github.iso53.castiel.update.UpdateCli;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

	/** Requires token authentication for every API call. */
	@Option(names = "--auth", description = "Require token authentication for every API call. Recommended whenever castiel is reachable beyond localhost.")
	private boolean auth;

	/** Overrides the bind address from application.yaml (default 127.0.0.1). */
	@Option(names = "--bind", paramLabel = "ADDR", description = "Network address to bind (default: 127.0.0.1). Binding beyond localhost is only allowed together with --auth and is rejected otherwise.")
	private String bind;

	/** Prints the auth token and exits. */
	@Option(names = "--show-token", description = "Print the --auth access token and exit.")
	private boolean showToken;

	/** Replaces the auth token and exits. */
	@Option(names = "--reset-token", description = "Replace the --auth access token and exit. Takes effect on the next start, which also drops open sessions.")
	private boolean resetToken;

	/** Overrides the HTTP port from application.yaml (default 8081). */
	@Option(names = { "--port", "-p" }, paramLabel = "PORT", description = "HTTP port to listen on (default: 8081).", converter = CastielCommand.PortConverter.class)
	private Integer port;

	/**
	 * Also writes logs to the given file: setting the Spring property
	 * {@code logging.file.name} adds a rolling FILE appender next to the
	 * console one, so stdout keeps working as before. Given without a value
	 * (or with a blank one) it falls back to {@link #DEFAULT_LOG_FILE} in
	 * the current working directory.
	 */
	@Option(names = "--log-file", paramLabel = "FILE", arity = "0..1", fallbackValue = CastielCommand.DEFAULT_LOG_FILE, description = "Also write logs to FILE (rotated automatically). Without a value, logs go to " + CastielCommand.DEFAULT_LOG_FILE + " in the current directory.", converter = CastielCommand.LogFileConverter.class)
	private String logFile;

	/** File name used when --log-file is given without (or with a blank) value. */
	private static final String DEFAULT_LOG_FILE = "castiel.log";

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
			&& !cli.showToken
			&& !cli.resetToken
			&& !commandLine.isUsageHelpRequested()
			&& !commandLine.isVersionHelpRequested();

		// Fail fast on the exposed-and-unauthenticated combination: the API can run
		// arbitrary commands, so binding beyond localhost without --auth must not boot.
		if (boot && cli.bind != null && !cli.bind.isBlank() && !cli.auth) {
			boolean loopback;
			try {
				loopback = InetAddress.getByName(cli.bind.trim()).isLoopbackAddress();
			} catch (UnknownHostException ex) {
				// Unresolvable addresses cannot bind either; skip the gate.
				loopback = true;
			}
			if (!loopback) {
				System.err.println(
						"--bind " + cli.bind.trim() + ": binding beyond localhost requires --auth; without it anyone "
						+ "who can reach this address can run commands on this machine. Start with --auth, or keep "
						+ "the default localhost bind.");
				return new ParseResult(false, 2, cli);
			}
		}
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
		if (auth) {
			overrides.put("castiel.auth.enabled", "true");
		}
		if (bind != null && !bind.isBlank()) {
			overrides.put("server.address", bind.trim());
		}
		if (port != null) {
			overrides.put("server.port", String.valueOf(port));
		}
		if (logFile != null) {
			overrides.put("logging.file.name", logFile);
		}
		return overrides;
	}

	@Override
	public Integer call() {
		if (showToken) {
			// Terminal flow: prints the stored token, no Spring boot follows.
			return showToken();
		}
		if (resetToken) {
			// Terminal flow: replaces the token, no Spring boot follows.
			return resetToken();
		}
		if (checkUpdate) {
			// A dedicated flow that owns the terminal; a normal boot never follows it.
			return UpdateCli.run();
		}
		// Normal boot: Spring takes over after this returns 0.
		return 0;
	}

	/** Prints the stored auth token; used when the first-boot log line got lost. */
	private int showToken() {
		String token = AuthStore.load();
		if (token == null) {
			System.out.println("No auth token yet: start castiel once with --auth to initialize it.");
			return 1;
		}
		System.out.println(token);
		return 0;
	}

	/** Replaces the auth token; takes effect on the next start, which also drops open sessions. */
	private int resetToken() {
		String token = AuthStore.reset();
		System.out.println("New auth token: " + token);
		System.out.println("Restart castiel so the new token takes effect; existing sessions are dropped on restart.");
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

	/**
	 * Maps blank log file values to the default file in the current
	 * directory and validates the target at parse time: the parent
	 * directory must exist and be writable, and the target must not be an
	 * unwritable path or a directory. Logback itself fails silently on an
	 * unopenable file (its error never reaches the log), so this pre-flight
	 * check is what makes a typo'd path fail fast with a clear message
	 * instead of quietly losing the file.
	 */
	private static final class LogFileConverter implements CommandLine.ITypeConverter<String> {
		@Override
		public String convert(String value) {
			String trimmed = value == null ? "" : value.trim();
			if (trimmed.isEmpty()) {
				// Blank value means "default file in the current directory".
				trimmed = DEFAULT_LOG_FILE;
			}
			Path file = Paths.get(trimmed).toAbsolutePath().normalize();
			Path parent = file.getParent() == null ? Paths.get(".").toAbsolutePath() : file.getParent();
			if (!Files.isDirectory(parent) || !Files.isWritable(parent)) {
				throw new CommandLine.TypeConversionException("FILE: parent directory " + parent + " does not exist or is not writable");
			}
			if (Files.isDirectory(file)) {
				throw new CommandLine.TypeConversionException("FILE: " + file + " is a directory");
			}
			if (Files.exists(file) && !Files.isWritable(file)) {
				throw new CommandLine.TypeConversionException("FILE: " + file + " is not writable");
			}
			return trimmed;
		}
	}
}
