package io.github.iso53.castiel.util;

import java.nio.file.Path;

/**
 * Resolves OS-appropriate application data directories for Castiel.
 */
public final class AppPaths {

	private static final String APP_DIR = "castiel";

	private AppPaths() {}

	/**
	 * Config / settings directory:
	 * <ul>
	 *   <li>Windows: {@code %APPDATA%\castiel}</li>
	 *   <li>macOS: {@code ~/Library/Application Support/castiel}</li>
	 *   <li>Linux: {@code $XDG_CONFIG_HOME/castiel} or {@code ~/.config/castiel}</li>
	 * </ul>
	 */
	public static Path configDir() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			if (appData != null && !appData.isBlank()) {
				return Path.of(appData, APP_DIR);
			}
			return Path.of(System.getProperty("user.home"), "AppData", "Roaming", APP_DIR);
		}
		if (os.contains("mac")) {
			return Path.of(System.getProperty("user.home"), "Library", "Application Support", APP_DIR);
		}
		String xdg = System.getenv("XDG_CONFIG_HOME");
		if (xdg != null && !xdg.isBlank()) {
			return Path.of(xdg, APP_DIR);
		}
		return Path.of(System.getProperty("user.home"), ".config", APP_DIR);
	}

	public static Path settingsFile() {
		return configDir().resolve("settings.json");
	}
}
