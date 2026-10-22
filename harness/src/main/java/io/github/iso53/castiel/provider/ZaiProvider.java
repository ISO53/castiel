package io.github.iso53.castiel.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.iso53.castiel.model.HealthStatus;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ModelInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Z.ai (Zhipu GLM) cloud backend, spoken through its OpenAI Chat Completions dialect.
 * The base URL already ends in the version segment ({@code /api/paas/v4} for pay-as-you-go,
 * {@code /api/coding/paas/v4} for the GLM Coding Plan subscription) and is stored whole in
 * the config, so no {@code /v1} rewriting happens here.
 *
 * <p>z.ai publishes no model-listing endpoint that doubles as a key check, so the catalog
 * is a fixed list mirroring the public pricing page. GLM reasoning arrives as a separate
 * {@code reasoning_content} stream (not OpenAI {@code reasoning_effort}), which
 * {@code returnThinking(true)} surfaces; the effort knob is not advertised.
 */
public class ZaiProvider implements LlmProvider {

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
	private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(20);
	/** Cheapest verification target: free on both billing modes and routing-safe across plan tiers. */
	private static final String HEALTH_MODEL = "glm-4.7-flash";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.version(HttpClient.Version.HTTP_1_1)
		.connectTimeout(Duration.ofSeconds(5))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final ObjectMapper JSON = new ObjectMapper();

	/** Chat-capable models from z.ai's public pricing page; no context windows are published. */
	private static final List<ModelInfo> CATALOG = List.of(
		new ModelInfo("glm-5.3"),
		new ModelInfo("glm-5.3-flash"),
		new ModelInfo("glm-5.3-flashx"),
		new ModelInfo("glm-5.3-flashx-legal"),
		new ModelInfo("glm-5.2"),
		new ModelInfo("glm-5.1"),
		new ModelInfo("glm-5"),
		new ModelInfo("glm-4.7"),
		new ModelInfo("glm-4.6"),
		new ModelInfo("glm-4.7-flashx"),
		new ModelInfo("glm-4.5"),
		new ModelInfo("glm-4.5-x"),
		new ModelInfo("glm-4.5-air"),
		new ModelInfo("glm-4.5-airx"),
		new ModelInfo("glm-4-32b-0414-128k"),
		new ModelInfo("glm-4.7-flash"),
		new ModelInfo("glm-4.5-flash")
	);

	private final LlmProviderConfig config;

	public ZaiProvider(LlmProviderConfig config) {
		this.config = config;
	}

	@Override
	public List<ModelInfo> listModels() {
		return CATALOG;
	}

	@Override
	public StreamingChatModel chatModel(String modelName, GenerationOptions options) {
		return OpenAiStreamingChatModel.builder()
			.baseUrl(rootUrl() + "/")
			.apiKey(config.apiKey())
			.modelName(modelName)
			.returnThinking(true)
			.timeout(REQUEST_TIMEOUT)
			.logRequests(false)
			.logResponses(false)
			.build();
	}

	/**
	 * The inherited health check trusts {@link #listModels()}, which is static here and
	 * would report healthy with a garbage key. Verify with a real 1-token call against a
	 * free model instead.
	 */
	@Override
	public HealthStatus healthCheck() {
		String payload =
			"{\"model\":\"" +
			HEALTH_MODEL +
			"\",\"messages\":" +
			"[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1}";
		HttpRequest request = HttpRequest.newBuilder(URI.create(rootUrl() + "/chat/completions"))
			.timeout(HEALTH_TIMEOUT)
			.header("Content-Type", "application/json")
			.header("Authorization", "Bearer " + config.apiKey())
			.POST(HttpRequest.BodyPublishers.ofString(payload))
			.build();
		HttpResponse<String> response;
		try {
			response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException ex) {
			return new HealthStatus(false, "Could not reach " + config.apiUrl());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new HealthStatus(false, "Interrupted during health check");
		}
		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return new HealthStatus(true, "Working");
		}
		return new HealthStatus(
			false,
			"z.ai rejected the key (HTTP " +
				response.statusCode() +
				(response.body().isBlank() ? ")" : "): " + response.body())
		);
	}

	private String rootUrl() {
		String root = config.apiUrl();
		while (root.endsWith("/")) {
			root = root.substring(0, root.length() - 1);
		}
		return root;
	}
}
