package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.LinkedHashMap;
import java.util.Map;

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

	private volatile UserSettings settings = new UserSettings(null, null, null, null);

	@PostConstruct
	void loadOnStartup() {
		settings = readFromDisk();
	}

	public UserSettings get() {
		return settings;
	}

	public synchronized UserSettings save(UserSettings next) {
		settings = next != null ? next : new UserSettings(null, null, null, null);
		writeToDisk(settings);
		return settings;
	}

	// Upserts one provider entry (marking it the UI default) and writes settings to disk.
	public synchronized UserSettings upsertProvider(String providerId, LlmProviderConfig config) {
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("providerId is required");
		}
		Map<String, LlmProviderConfig> next = new LinkedHashMap<>(settings.providers());
		next.put(providerId.trim(), config);
		settings = new UserSettings(next, providerId.trim(), settings.workerModel(), settings.defaultMaxRounds());
		writeToDisk(settings);
		return settings;
	}

	private UserSettings readFromDisk() {
		Path file = AppPaths.settingsFile();
		if (!Files.isRegularFile(file)) {
			return new UserSettings(null, null, null, null);
		}
		try {
			UserSettings loaded = objectMapper.readValue(file.toFile(), UserSettings.class);
			return loaded != null ? loaded : new UserSettings(null, null, null, null);
		} catch (IOException ex) {
			log.warn("Could not read settings from {}; using defaults", file, ex);
			return new UserSettings(null, null, null, null);
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
