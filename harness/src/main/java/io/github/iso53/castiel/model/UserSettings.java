package io.github.iso53.castiel.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full user settings document stored under the OS config directory.
 *
 * @param providers         Provider id → config (ids are UI-defined, e.g. {@code llama.cpp}).
 * @param activeProviderId  Id of the provider used for chat when none is overridden per request.
 */
public record UserSettings(Map<String, LlmProviderConfig> providers, String activeProviderId) {

	public UserSettings {
		if (providers == null) {
			providers = Map.of();
		} else {
			providers = Map.copyOf(providers);
		}
	}

	public static UserSettings empty() {
		return new UserSettings(Map.of(), null);
	}

	/**
	 * Returns a copy with {@code providerId} upserted, optionally marking it active.
	 */
	public UserSettings withProvider(String providerId, LlmProviderConfig config, boolean activate) {
		Map<String, LlmProviderConfig> next = new LinkedHashMap<>(providers);
		next.put(providerId, config);
		String active = activate ? providerId : activeProviderId;
		return new UserSettings(next, active);
	}

	public UserSettings withProvider(String providerId, LlmProviderConfig config) {
		return withProvider(providerId, config, false);
	}

	public LlmProviderConfig requireProvider(String providerId) {
		LlmProviderConfig config = providers.get(providerId);
		if (config == null) {
			throw new IllegalArgumentException("Unknown provider: " + providerId);
		}
		return config;
	}
}
