package io.github.iso53.castiel.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * castiel command line interface. Parsed in {@code main()} before Spring starts so
 * castiel flags never leak into the Spring property layer.
 *
 * <p>Unknown options and stray positional arguments fail fast: picocli prints the
 * error plus usage ("see --help") and the app exits with code 2.</p>
 */
@Command(
	name = "castiel", //
	mixinStandardHelpOptions = true, // adds -h/--help and -V/--version
	versionProvider = AppVersionProvider.class, //
	description = "AI powered pentesting harness."
)
public final class CastielCommand implements Callable<Integer> {

	/** Enables debug logging for castiel and connected libraries. */
	@Option(names = "--debug", description = "Enable debug logging for castiel.")
	public boolean debug;

	@Override
	public Integer call() {
		// Normal boot: Spring takes over after this returns 0.
		return 0;
	}
}
