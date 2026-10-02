package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.UserSettings;
import io.github.iso53.castiel.tool.ToolCatalog;
import io.github.iso53.castiel.util.AppPaths;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

	// Upserts one provider entry (marking it the UI default) and writes settings to disk.
	public synchronized UserSettings upsertProvider(String providerId, LlmProviderConfig config) {
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("providerId is required");
		}
		Map<String, LlmProviderConfig> next = new LinkedHashMap<>(settings.providers());
		next.put(providerId.trim(), config);
		settings = settings.withProviders(next, providerId.trim());
		writeToDisk(settings);
		return settings;
	}

	// Replaces an existing provider entry without changing the active provider (used by background token refreshes).
	public synchronized UserSettings replaceProvider(String providerId, LlmProviderConfig config) {
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("providerId is required");
		}
		Map<String, LlmProviderConfig> next = new LinkedHashMap<>(settings.providers());
		next.put(providerId.trim(), config);
		settings = settings.withProviders(next, settings.activeProviderId());
		writeToDisk(settings);
		return settings;
	}

	// Removes a provider entry entirely and writes settings to disk.
	public synchronized UserSettings removeProvider(String providerId) {
		if (providerId == null || providerId.isBlank()) {
			throw new IllegalArgumentException("providerId is required");
		}
		Map<String, LlmProviderConfig> next = new LinkedHashMap<>(settings.providers());
		next.remove(providerId.trim());
		// Do not leave the UI default pointing at a removed provider.
		String activeId = providerId.trim().equals(settings.activeProviderId()) ? null : settings.activeProviderId();
		settings = settings.withProviders(next, activeId);
		writeToDisk(settings);
		return settings;
	}

	/**
	 * Replaces the disabled tool groups. Unknown ids are dropped rather than stored, so a
	 * catalogue rename leaves the file clean instead of accumulating dead entries.
	 */
	public synchronized UserSettings setDisabledToolGroups(Set<String> groupIds, ToolCatalog catalog) {
		Set<String> accepted = groupIds == null ? Set.of() : groupIds.stream().filter(catalog::isKnownToggleable).collect(Collectors.toUnmodifiableSet());
		settings = settings.withDisabledGroups(accepted);
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
