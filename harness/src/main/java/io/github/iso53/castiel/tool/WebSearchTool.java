package io.github.iso53.castiel.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.util.Http;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Web search for the model, backed by the keyless FreeSerp API
 * ({@code https://freeserp.ai/api.php}), always against the open-web index.
 */
@Service
public class WebSearchTool implements ToolProvider {

	private static final int DEFAULT_RESULTS = 8;
	private static final int MAX_RESULTS = 15;
	private static final String SEARCH_ENDPOINT = "https://freeserp.ai/api.php";

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final ObjectMapper JSON = new ObjectMapper();

	@Tool(
		name = "web_search",
		value = {
			"Searches the open web and returns numbered results with title, URL, snippet,",
			"language, and publication date when known. Use web_fetch on a result's URL",
			"to read the full page.",
		}
	)
	public String webSearch(
		@P("The search query") String query,
		@P("Optional maximum number of results between 1 and 15; default is 8") Integer maxResults
	) {
		if (query == null || query.isBlank()) {
			return "Error: query is required";
		}
		int limit = maxResults == null ? DEFAULT_RESULTS : Math.clamp(maxResults, 1, MAX_RESULTS);

		// The free endpoint asks heavy users to slow down politely; back off on
		// 429/5xx instead of hammering it, and give up after a few attempts.
		List<SearchResult> results = null;
		String searchError = null;
		for (int attempt = 0; attempt < 3 && results == null; attempt++) {
			try {
				String url = SEARCH_ENDPOINT
					+ "?index=web"
					+ "&q=" + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8)
					+ "&size=" + limit
					+ "&agent=castiel";
				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(20))
					.header("User-Agent", Http.USER_AGENT)
					.header("Accept", "application/json")
					.GET()
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				int status = response.statusCode();
				if (status == 429 || status >= 500) {
					Thread.sleep(2_000L * (attempt + 1));
					continue;
				}
				if (status != 200) {
					return "Error: the search API returned HTTP " + status;
				}
				results = parseResults(response.body(), limit);
			} catch (IOException ex) {
				searchError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
				try {
					Thread.sleep(1_500L);
				} catch (InterruptedException sleepEx) {
					Thread.currentThread().interrupt();
					return "Error: interrupted while searching";
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return "Error: interrupted while searching";
			}
		}

		if (results == null) {
			return searchError != null
				? "Error: search failed after retries: " + searchError
				: "Error: the search API kept rejecting the request; try again shortly";
		}
		if (results.isEmpty()) {
			return "No results found for: " + query.strip();
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < results.size(); i++) {
			SearchResult result = results.get(i);
			out.append("[%d] %s%n%s%n%s%n%n".formatted(i + 1, result.title(), result.url(), result.snippet()));
		}
		return out.toString().strip();
	}

	private record SearchResult(String title, String url, String snippet) {}

	/**
	 * Parses the FreeSerp JSON response: a {@code results} array of hits with url,
	 * title, snippet, and — when known — language and publication date, which are
	 * appended to the snippet so the model can weigh freshness.
	 */
	private static List<SearchResult> parseResults(String body, int limit) throws IOException {
		JsonNode root = JSON.readTree(body);
		List<SearchResult> results = new ArrayList<>();
		for (JsonNode hit : root.path("results")) {
			String url = hit.path("url").asText("");
			String title = hit.path("title").asText("");
			if (title.isBlank() || url.isBlank()) {
				continue;
			}
			String snippet = hit.path("snippet").asText("");
			String language = hit.path("language").asText("");
			String published = hit.path("published_at").asText("");
			StringBuilder meta = new StringBuilder();
			if (!language.isBlank()) {
				meta.append("lang: ").append(language);
			}
			if (!published.isBlank()) {
				if (!meta.isEmpty()) {
					meta.append(", ");
				}
				meta.append("published: ").append(published);
			}
			if (!meta.isEmpty()) {
				snippet = snippet.isBlank() ? "[" + meta + "]" : snippet + " [" + meta + "]";
			}
			results.add(new SearchResult(title, url, snippet));
			if (results.size() >= limit) {
				break;
			}
		}
		return results;
	}
}
