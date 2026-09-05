package io.github.iso53.castiel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.Locale;
import java.util.Map;

@SpringBootApplication
public class CastielApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(CastielApplication.class);
		// The packaged JAR serves the embedded UI and prints where to reach it.
		// A dev run (mvn spring-boot:run) is API only: the UI lives on the Vite
		// dev server, so no UI line is printed and the embedded UI is off.
		application.setDefaultProperties(runningFromJar()
			? Map.of(
				"app.ui-footer", "\n  UI accessible at http://localhost:${server.port}",
				"logging.level.root", "OFF",
				"logging.level.org.springframework.boot.diagnostics", "ERROR")
			: Map.of(
				"app.ui-footer", "",
				"spring.web.resources.add-mappings", "false",
				"spring.webflux.static-path-pattern", "/unavailable/**"));
		application.run(args);
	}

	/** True when the app runs from the packaged JAR instead of compiled classes. */
	private static boolean runningFromJar() {
		String[] entries = System.getProperty("java.class.path", "").split(File.pathSeparator);
		return entries.length == 1 && entries[0].toLowerCase(Locale.ROOT).endsWith(".jar");
	}
}

