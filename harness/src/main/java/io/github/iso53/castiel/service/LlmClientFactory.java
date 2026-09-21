package io.github.iso53.castiel.service;

import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ProviderType;
import io.github.iso53.castiel.provider.LlmProvider;
import io.github.iso53.castiel.provider.OllamaProvider;
import io.github.iso53.castiel.provider.OpenAiCompatibleProvider;
import org.springframework.stereotype.Service;

/**
 * Creates {@link LlmProvider} instances from persisted provider config. The single
 * switch over {@link ProviderType} lives here. Everything provider-specific is
 * behind the {@link LlmProvider} implementations.
 */
@Service
public class LlmClientFactory {

	private final ClineOAuthService clineOAuthService;

	public LlmClientFactory(ClineOAuthService clineOAuthService) {
		this.clineOAuthService = clineOAuthService;
	}

	/**
	 * Provider view over a persisted provider entry.
	 */
	public LlmProvider create(LlmProviderConfig config) {
		// Account-backed providers may need a token refresh before the provider is used.
		config = clineOAuthService.refreshIfExpiring(config);
		return switch (config.type()) {
			case OPENAI_COMPATIBLE -> new OpenAiCompatibleProvider(config);
			case OLLAMA -> new OllamaProvider(config);
		};
	}
}
