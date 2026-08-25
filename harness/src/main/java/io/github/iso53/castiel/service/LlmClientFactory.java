package io.github.iso53.castiel.service;

import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ProviderType;
import io.github.iso53.castiel.provider.LlmProvider;
import io.github.iso53.castiel.provider.OllamaProvider;
import io.github.iso53.castiel.provider.OpenAiCompatibleProvider;
import org.springframework.stereotype.Service;

/**
 * Creates {@link LlmProvider} instances from persisted provider config. The single
 * switch over {@link ProviderType} lives here — everything provider-specific is
 * behind the {@link LlmProvider} implementations.
 */
@Service
public class LlmClientFactory {

	/**
	 * Provider view over a persisted provider entry.
	 */
	public LlmProvider create(LlmProviderConfig config) {
		return switch (config.type()) {
			case OPENAI_COMPATIBLE -> new OpenAiCompatibleProvider(config);
			case OLLAMA -> new OllamaProvider(config);
		};
	}
}
