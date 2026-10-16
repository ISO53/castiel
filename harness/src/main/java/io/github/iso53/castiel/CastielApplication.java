package io.github.iso53.castiel;

import io.github.iso53.castiel.cli.CastielCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CastielApplication {

	public static void main(String[] args) {
		CastielCommand.ParseResult parsed = CastielCommand.parse(args);
		if (!parsed.boot()) {
			System.exit(parsed.exitCode());
		}
		// Flags map themselves to Spring properties; main stays a thin orchestrator.
		parsed.cli().springOverrides().forEach(System::setProperty);
		SpringApplication.run(CastielApplication.class);
	}
}
