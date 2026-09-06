package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Web search for the model, backed by DuckDuckGo's legacy HTML endpoint
 * ({@code html.duckduckgo.com/html/}). No API key required.
 */
@Service
public class WebSearchTool implements ToolProvider {

	private static final int DEFAULT_RESULTS = 8;
	private static final int MAX_RESULTS = 15;
	private static final String SEARCH_ENDPOINT = "https://html.duckduckgo.com/html/";

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	/** Browser-like user agents; DuckDuckGo rejects the default Java UA string. */
	private static final List<String> USER_AGENTS = List.of(
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
		"Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0"
	);

	@Tool(
		name = "web_search",
		value = {
			"Searches the web with DuckDuckGo and returns numbered results with title, URL, and snippet.",
			"Use web_fetch on a result's URL to read the full page.",
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

		// DuckDuckGo's endpoint intermittently rate-limits automation (HTTP 429/202). Retry a
		// couple of times with a fresh user agent before giving up.
		SearchResponse response = null;
		IOException lastError = null;
		for (int attempt = 0; attempt < 3 && response == null; attempt++) {
			try {
				String form = "q=" + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8) + "&b=&l=us-en";
				HttpRequest request =
					HttpRequest
						.newBuilder(URI.create(SEARCH_ENDPOINT))
						.timeout(Duration.ofSeconds(20))
						.header("Content-Type", "application/x-www-form-urlencoded")
						.header(
							"User-Agent",
							USER_AGENTS.get((int) (Math.random() * USER_AGENTS.size()))
						)
						.POST(HttpRequest.BodyPublishers.ofString(form))
						.build();
				HttpResponse<byte[]> candidate =
					HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
				// DuckDuckGo serves UTF-8 bytes but mislabels them as ISO-8859-1, so decode
				// manually instead of trusting the response charset header.
				if (candidate.statusCode() == 429 || candidate.statusCode() == 503 || candidate.statusCode() == 202) {
					Thread.sleep(2_000L * (attempt + 1));
					continue;
				}
				response = new SearchResponse(
					candidate.statusCode(),
					new String(candidate.body(), StandardCharsets.UTF_8)
				);
			} catch (IOException ex) {
				lastError = ex;
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return "Error: interrupted while searching";
			}
		}

		if (response == null) {
			return lastError != null
				? "Error: search failed after retries: " + lastError.getMessage()
				: "Error: DuckDuckGo kept rejecting the request; try again shortly";
		}
		if (response.statusCode() != 200) {
			return "Error: DuckDuckGo returned HTTP " + response.statusCode();
		}

		List<SearchResult> results = parseResults(response.body(), limit);
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

	private record SearchResponse(int statusCode, String body) {}

	/**
	 * Parses the results page: each organic result lives in a {@code div.result__body} with an
	 * {@code a.result__a} link and a snippet. Sponsored entries ({@code duckduckgo.com/y.js})
	 * are dropped and result links, wrapped by DuckDuckGo in {@code /l/?uddg=<url>} redirects,
	 * are unwrapped.
	 */
	private static List<SearchResult> parseResults(String html, int limit) {
		org.jsoup.nodes.Document document = Jsoup.parse(html);
		List<SearchResult> results = new ArrayList<>();
		for (Element body : document.select("div.result__body")) {
			Element link = body.selectFirst("a.result__a");
			if (link == null) {
				continue;
			}
			String url = unwrapRedirect(link.absUrl("href"));
			String title = link.text();
			Element snippetElement = body.selectFirst(".result__snippet");
			String snippet = snippetElement == null ? "" : snippetElement.text();
			if (title.isBlank() || url.isBlank() || url.contains("duckduckgo.com/y.js")) {
				continue;
			}
			results.add(new SearchResult(title, url, snippet));
			if (results.size() >= limit) {
				break;
			}
		}
		return results;
	}

	/** Rewrites {@code https://duckduckgo.com/l/?uddg=<encoded>} wrapper links to the target URL. */
	private static String unwrapRedirect(String href) {
		if (href.isBlank()) {
			return "";
		}
		try {
			URI uri = URI.create(href.startsWith("//") ? "https:" + href : href);
			String host = uri.getHost() == null ? "" : uri.getHost();
			String path = uri.getPath() == null ? "" : uri.getPath();
			if (host.endsWith("duckduckgo.com") && path.contains("/l/") && uri.getRawQuery() != null) {
				for (String param : uri.getRawQuery().split("&")) {
					int equals = param.indexOf('=');
					if (equals > 0 && param.substring(0, equals).equals("uddg")) {
						return URLDecoder.decode(param.substring(equals + 1), StandardCharsets.UTF_8);
					}
				}
			}
			return href;
		} catch (IllegalArgumentException ex) {
			return href;
		}
	}
}
