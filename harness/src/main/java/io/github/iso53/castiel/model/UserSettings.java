package io.github.iso53.castiel.model;

import io.github.iso53.castiel.config.McpServerConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full user settings document stored under the OS config directory.
 *
 * @param providers         Provider id → config (ids are UI-defined, e.g. {@code llama.cpp}).
 * @param activeProviderId  Id of the provider used for chat when none is overridden per request.
 * @param mcpServers        MCP server id → config for the servers the agent can call tools on.
 */
public record UserSettings(
	Map<String, LlmProviderConfig> providers,
	String activeProviderId,
	Map<String, McpServerConfig> mcpServers
) {

	public UserSettings {
		if (providers == null) {
			providers = Map.of();
		} else {
			providers = Map.copyOf(providers);
		}
		if (mcpServers == null) {
			mcpServers = Map.of();
		} else {
			mcpServers = Map.copyOf(mcpServers);
		}
	}

	public static UserSettings empty() {
		return new UserSettings(Map.of(), null, Map.of());
	}

	/**
	 * Returns a copy with {@code providerId} upserted, optionally marking it active.
	 */
	public UserSettings withProvider(String providerId, LlmProviderConfig config, boolean activate) {
		Map<String, LlmProviderConfig> next = new LinkedHashMap<>(providers);
		next.put(providerId, config);
		String active = activate ? providerId : activeProviderId;
		return new UserSettings(next, active, mcpServers);
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

	/** Returns a copy with the MCP server entry upserted under its id. */
	public UserSettings withMcpServer(McpServerConfig config) {
		Map<String, McpServerConfig> next = new LinkedHashMap<>(mcpServers);
		next.put(config.id(), config);
		return new UserSettings(providers, activeProviderId, next);
	}

	/** Returns a copy without the MCP server entry with the given id. */
	public UserSettings withoutMcpServer(String id) {
		Map<String, McpServerConfig> next = new LinkedHashMap<>(mcpServers);
		next.remove(id);
		return new UserSettings(providers, activeProviderId, next);
	}
}

