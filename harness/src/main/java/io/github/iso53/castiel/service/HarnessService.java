package io.github.iso53.castiel.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.iso53.castiel.model.ApplicationResource;
import io.github.iso53.castiel.model.ChatStreamRequest;
import io.github.iso53.castiel.model.ChatTurn;
import io.github.iso53.castiel.model.LlmProviderConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Streams chat completions through LangChain4j using configured providers.
 */
@Service
public class HarnessService {

	private final LlmClientFactory llmClientFactory;
	private final UserSettingsService userSettingsService;
	private final ResourceLoader resourceLoader;

	public HarnessService(
		LlmClientFactory llmClientFactory,
		UserSettingsService userSettingsService,
		ResourceLoader resourceLoader
	) {
		this.llmClientFactory = llmClientFactory;
		this.userSettingsService = userSettingsService;
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Streams tokens for a full chat thread.
	 *
	 * <p>Prepends {@link ApplicationResource#SYSTEM_PROMPT}, then the conversation turns from the client.
	 */
	public Flux<String> stream(ChatStreamRequest request) {
		if (request == null) {
			return Flux.error(new IllegalArgumentException("Request body is required"));
		}
		if (request.providerId() == null || request.providerId().isBlank()) {
			return Flux.error(new IllegalArgumentException("providerId is required"));
		}
		if (request.modelName() == null || request.modelName().isBlank()) {
			return Flux.error(new IllegalArgumentException("modelName is required"));
		}
		if (request.messages() == null || request.messages().isEmpty()) {
			return Flux.error(new IllegalArgumentException("messages must not be empty"));
		}

		LlmProviderConfig config;
		try {
			config = userSettingsService.get().requireProvider(request.providerId().trim());
		} catch (IllegalArgumentException ex) {
			return Flux.error(ex);
		}

		StreamingChatModel model = llmClientFactory.streamingChatModel(config, request.modelName().trim());
		List<ChatMessage> messages = buildMessages(request.messages());

		return Flux.create(sink -> {
			AtomicBoolean isThinking = new AtomicBoolean(false);

			model.chat(
				messages,
				new StreamingChatResponseHandler() {
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
				}
			);
		});
	}

	private List<ChatMessage> buildMessages(List<ChatTurn> turns) {
		List<ChatMessage> messages = new ArrayList<>();
		String systemPrompt = resourceLoader.readText(ApplicationResource.SYSTEM_PROMPT);
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			messages.add(SystemMessage.from(systemPrompt));
		}

		for (ChatTurn turn : turns) {
			if (turn == null || turn.content() == null || turn.content().isBlank()) {
				continue;
			}
			String role = turn.role() == null ? "" : turn.role().trim().toLowerCase();
			switch (role) {
				case "user" -> messages.add(UserMessage.from(turn.content()));
				case "assistant" -> messages.add(AiMessage.from(turn.content()));
				case "system" -> messages.add(SystemMessage.from(turn.content()));
				default -> throw new IllegalArgumentException("Unsupported message role: " + turn.role());
			}
		}

		if (messages.stream().noneMatch(m -> m instanceof UserMessage)) {
			throw new IllegalArgumentException("Thread must include at least one user message");
		}
		return messages;
	}
}
