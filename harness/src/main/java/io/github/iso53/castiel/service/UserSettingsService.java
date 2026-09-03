package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.iso53.castiel.config.McpServerConfig;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.UserSettings;
import io.github.iso53.castiel.util.AppPaths;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and persists {@link UserSettings} as JSON under the OS config directory.
 */
@Service
public class UserSettingsService {

	private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

	// Tolerates unknown fields so settings written by other versions still load.
	private final ObjectMapper objectMapper = new ObjectMapper()
		.enable(SerializationFeature.INDENT_OUTPUT)
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private volatile UserSettings settings = UserSettings.empty();

	@PostConstruct
	void loadOnStartup() {
		settings = readFromDisk();
	}

	public UserSettings get() {
		return settings;
	}

	public synchronized UserSettings save(UserSettings next) {
		settings = next != null ? next : UserSettings.empty();
		writeToDisk(settings);
		return settings;
	}

	/**
	 * Upserts one provider entry and writes settings to disk.
	 */
	public synchronized UserSettings upsertProvider(String providerId, LlmProviderConfig config) {
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("providerId is required");
		}
		settings = settings.withProvider(providerId.trim(), config, true);
		writeToDisk(settings);
		return settings;
	}

	/**
	 * Upserts one MCP server entry and writes settings to disk.
	 */
	public synchronized UserSettings upsertMcpServer(McpServerConfig config) {
		if (config == null || config.id() == null || config.id().isBlank()) {
			throw new IllegalArgumentException("MCP server id is required");
		}
		settings = settings.withMcpServer(config);
		writeToDisk(settings);
		return settings;
	}

	/**
	 * Removes the MCP server entry with the given id and writes settings to disk.
	 */
	public synchronized UserSettings removeMcpServer(String id) {
		settings = settings.withoutMcpServer(id);
		writeToDisk(settings);
		return settings;
	}

	private UserSettings readFromDisk() {
		Path file = AppPaths.settingsFile();
		if (!Files.isRegularFile(file)) {
			return UserSettings.empty();
		}
		try {
			UserSettings loaded = objectMapper.readValue(file.toFile(), UserSettings.class);
			return loaded != null ? loaded : UserSettings.empty();
		} catch (IOException ex) {
			log.warn("Could not read settings from {}; using defaults", file, ex);
			return UserSettings.empty();
		}
	}

	private void writeToDisk(UserSettings value) {
		Path file = AppPaths.settingsFile();
		try {
			Files.createDirectories(file.getParent());
			objectMapper.writeValue(file.toFile(), value);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not write settings to " + file, ex);
		}
	}
}
