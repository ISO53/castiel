package io.github.iso53.castiel;

import io.github.iso53.castiel.cli.CastielCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import picocli.CommandLine;

@SpringBootApplication
public class CastielApplication {

	private static final Logger log = LoggerFactory.getLogger(CastielApplication.class);

	private static final String UI_DISABLED_PATH_PATTERN = "/unavailable/**";

	public static void main(String[] args) {
		// Parse castiel's own flags before Spring starts
		CastielCommand cli = new CastielCommand();
		CommandLine commandLine = new CommandLine(cli);
		int exitCode = commandLine.execute(args);
		boolean proceedToBoot = exitCode == 0
			&& !commandLine.isUsageHelpRequested()
			&& !commandLine.isVersionHelpRequested();
		if (!proceedToBoot) {
			System.exit(exitCode);
		}

		SpringApplication application = new SpringApplication(CastielApplication.class);
		if (cli.debug) {
			application.setAdditionalProfiles("debug");
		}

		// Spring gets no args: castiel owns the command line.
		Environment env = application.run().getEnvironment();
		String pathPattern = env.getProperty("spring.webflux.static-path-pattern", "/**");
		if (!UI_DISABLED_PATH_PATTERN.equals(pathPattern)) {
			log.info("UI accessible at http://localhost:{}", env.getProperty("server.port", "8081"));
		}
	}
}
