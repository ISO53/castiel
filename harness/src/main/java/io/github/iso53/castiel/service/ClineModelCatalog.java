package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iso53.castiel.model.ModelInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ClinePass model catalog. ClinePass rides on the same OpenAI-compatible endpoint and
 * the same credentials as the {@code cline} provider; only the model ids differ (a
 * {@code cline-pass/} prefix routes a request against the subscription's quota). Those
 * ids are absent from the OpenAI {@code /models} list Cline's API exposes, so they are
 * read from the public recommended-models feed Cline's own clients use. When the feed
 * cannot be reached the ClinePass models are simply omitted from the listing.
 */
@Service
public class ClineModelCatalog {

	private static final Logger log = LoggerFactory.getLogger(ClineModelCatalog.class);

	/** Category stamped on ClinePass models so the UI can group them separately. */
	public static final String CLINE_PASS_CATEGORY = "clinepass";

	private static final String FEED_URL = "https://api.cline.bot/api/v1/ai/cline/recommended-models";
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
	private static final long CACHE_TTL_MS = 5 * 60 * 1000;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
	private static final ObjectMapper JSON = new ObjectMapper();

	private final Object lock = new Object();
	private List<ModelInfo> cache;
	private long cacheExpiresAt;

	/**
	 * The ClinePass models, fresh from the feed when possible. Feed failures yield an
	 * empty list so callers simply serve their regular catalog without ClinePass models.
	 */
	public List<ModelInfo> models() {
		synchronized (lock) {
			if (cache != null && System.currentTimeMillis() < cacheExpiresAt) {
				return cache;
			}
		}
		List<ModelInfo> fetched = fetchFeed();
		if (fetched != null) {
			synchronized (lock) {
				cache = fetched;
				cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
			}
			return fetched;
		}
		log.warn("ClinePass model feed unavailable; listing without ClinePass models");
		return List.of();
	}

	/**
	 * Appends the ClinePass models to a provider's own catalog, skipping ids the catalog
	 * already lists. Base models stay first; ClinePass models follow as their own group.
	 */
	public List<ModelInfo> mergedWith(List<ModelInfo> base) {
		List<ModelInfo> pass = models();
		List<ModelInfo> result = new ArrayList<>(base);
		for (ModelInfo model : pass) {
			if (base.stream().noneMatch(existing -> existing.name().equals(model.name()))) {
				result.add(model);
			}
		}
		return result;
	}

	private static ModelInfo passModel(String id) {
		return new ModelInfo(id, null, List.of(), CLINE_PASS_CATEGORY);
	}

	// Fetches the public recommended-models feed and extracts the clinePass bucket; null on any failure.
	private List<ModelInfo> fetchFeed() {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(FEED_URL))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			JsonNode root = JSON.readTree(response.body());
			// The live endpoint serves the buckets at the top level; Cline's SDK also
			// tolerates a { data: { ... } } envelope, so accept both shapes.
			JsonNode entries = root.path("clinePass");
			if (!entries.isArray()) {
				entries = root.path("data").path("clinePass");
			}
			if (!entries.isArray() || entries.isEmpty()) {
				return null;
			}
			List<ModelInfo> models = new ArrayList<>();
			for (JsonNode entry : entries) {
				String id = entry.path("id").asText("").trim();
				if (!id.isEmpty()) {
					models.add(passModel(id));
				}
			}
			return models.isEmpty() ? null : models;
		} catch (IOException ex) {
			return null;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		}
	}
}
