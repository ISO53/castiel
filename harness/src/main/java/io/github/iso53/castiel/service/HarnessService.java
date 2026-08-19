package io.github.iso53.castiel.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.iso53.castiel.model.AiRequest;
import io.github.iso53.castiel.model.ProviderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that directly delegates streaming text generation to LangChain4j's
 * {@link OpenAiStreamingChatModel} without custom provider wrapper classes.
 */
@Service
public class HarnessService {

    @Value("${harness.default-base-url:http://localhost:8080/v1}")
    private String defaultBaseUrl;

    @Value("${harness.default-model-name:gpt-3.5-turbo}")
    private String defaultModelName;

    /**
     * Streams tokens directly from LangChain4j's OpenAiStreamingChatModel as a reactive Flux.
     *
     * @param request Prompt, optional system instruction, and provider configuration.
     * @return Hot reactive Flux emitting tokens as received from the model.
     */
    public Flux<String> stream(AiRequest request) {
        ProviderConfig config = resolveConfig(request.provider());

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .returnThinking(true)
                .timeout(Duration.ofSeconds(120))
                .logRequests(false)
                .logResponses(false)
                .build();

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

    private ProviderConfig resolveConfig(ProviderConfig custom) {
        if (custom == null) {
            return new ProviderConfig(defaultBaseUrl, defaultModelName, ProviderConfig.DEFAULT_API_KEY);
        }
        String baseUrl = (custom.baseUrl() != null && !custom.baseUrl().isBlank()) ? custom.baseUrl() : defaultBaseUrl;
        String modelName = (custom.modelName() != null && !custom.modelName().isBlank()) ? custom.modelName() : defaultModelName;
        String apiKey = (custom.apiKey() != null && !custom.apiKey().isBlank()) ? custom.apiKey() : ProviderConfig.DEFAULT_API_KEY;
        return new ProviderConfig(baseUrl, modelName, apiKey);
    }
}
