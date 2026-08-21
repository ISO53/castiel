package io.github.iso53.castiel.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.iso53.castiel.model.AiRequest;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ProviderConfig;
import io.github.iso53.castiel.model.UserSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Streams chat completions through LangChain4j using configured providers.
 */
@Service
public class HarnessService {

	private final LlmClientFactory llmClientFactory;
	private final UserSettingsService userSettingsService;

    @Value("${harness.default-model-name:gpt-3.5-turbo}")
    private String defaultModelName;

	public HarnessService(LlmClientFactory llmClientFactory, UserSettingsService userSettingsService) {
		this.llmClientFactory = llmClientFactory;
		this.userSettingsService = userSettingsService;
	}

	/**
	 * Streams tokens from the resolved LangChain4j {@link StreamingChatModel}.
	 *
	 * @param request Prompt, optional system instruction, and optional per-request provider override.
	 */
	public Flux<String> stream(AiRequest request) {
		StreamingChatModel model = resolveModel(request);

		List<ChatMessage> messages = new ArrayList<>();
		if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
			messages.add(SystemMessage.from(request.systemPrompt()));
		}
		messages.add(UserMessage.from(request.userMessage()));

		return Flux.create(sink -> {
			AtomicBoolean isThinking = new AtomicBoolean(false);

			model.chat(messages, new StreamingChatResponseHandler() {
				@Override
				public void onPartialThinking(PartialThinking partialThinking) {
					if (partialThinking != null && partialThinking.text() != null) {
						if (isThinking.compareAndSet(false, true)) {
							sink.next("<think>\n");
						}
						sink.next(partialThinking.text());
					}
				}

				@Override
				public void onPartialResponse(String partialResponse) {
					if (partialResponse != null) {
						if (isThinking.compareAndSet(true, false)) {
							sink.next("\n</think>\n\n");
						}
						sink.next(partialResponse);
					}
				}

				@Override
				public void onCompleteResponse(ChatResponse completeResponse) {
					if (isThinking.compareAndSet(true, false)) {
						sink.next("\n</think>\n\n");
					}
					sink.complete();
				}

				@Override
				public void onError(Throwable error) {
					sink.error(error);
				}
			});
		});
	}

	private StreamingChatModel resolveModel(AiRequest request) {
		ProviderConfig override = request.provider();
		if (override != null) {
			return llmClientFactory.streamingChatModel(override);
		}

		UserSettings settings = userSettingsService.get();
		String activeId = settings.activeProviderId();
		if (activeId == null || activeId.isBlank()) {
			if (settings.providers().size() == 1) {
				activeId = settings.providers().keySet().iterator().next();
			} else {
				throw new IllegalStateException(
					"No active LLM provider configured. Connect a provider in Settings."
				);
			}
		}

		LlmProviderConfig config = settings.requireProvider(activeId);
		return llmClientFactory.streamingChatModel(config, defaultModelName);
	}
}
