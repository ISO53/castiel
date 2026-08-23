package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Fetches web pages for the model and converts them to readable markdown. */
@Service
public class WebFetchTool implements ToolProvider {

	private static final int MAX_FETCH_CHARS = 20_000;

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Tool(
		name = "web_fetch",
		value = {
			"Fetches a web page and returns its readable text content (HTML converted to markdown).",
			"Public URLs only; pages behind a login are not supported.",
			"The full page text is returned; analyze it yourself.",
		}
	)
	public String webFetch(
		@P("Absolute http(s) URL to fetch") String url,
		@P("Optional focus hint; the full page text is returned regardless") String prompt
	) {
		if (url == null || url.isBlank()) {
			return "Error: url is required";
		}
		String cleanedUrl = url.strip();
		if (cleanedUrl.startsWith("http://")) {
			cleanedUrl = "https://" + cleanedUrl.substring("http://".length());
		}
		if (!cleanedUrl.startsWith("https://")) {
			return "Error: only absolute http(s) URLs are supported";
		}

		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(cleanedUrl))
				.timeout(Duration.ofSeconds(30))
				.header(
					"User-Agent",
					"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
				)
				.header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return "Error: the server returned HTTP " + response.statusCode() + " for " + cleanedUrl;
			}

			org.jsoup.nodes.Document document = Jsoup.parse(response.body(), cleanedUrl);
			String markdown = toMarkdown(document.body());
			if (markdown.isBlank()) {
				return "(no readable content at " + cleanedUrl + ")";
			}
			if (markdown.length() > MAX_FETCH_CHARS) {
				markdown =
					markdown.substring(0, MAX_FETCH_CHARS) + "\n... [truncated at " + MAX_FETCH_CHARS + " characters]";
			}
			String title = document.title() == null || document.title().isBlank() ? "(untitled)" : document.title();
			return "# " + title + "\nURL: " + cleanedUrl + "\n\n" + markdown;
		} catch (IllegalArgumentException ex) {
			return "Error: invalid URL: " + cleanedUrl;
		} catch (IOException ex) {
			return "Error: could not fetch " + cleanedUrl + ": " + ex.getMessage();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return "Error: interrupted while fetching " + cleanedUrl;
		}
	}

	/** Converts a parsed HTML body to simple markdown: headings, links, lists, code, paragraphs. */
	private static String toMarkdown(Element body) {
		StringBuilder out = new StringBuilder();
		body.childNodes().forEach(child -> appendNode(out, child));
		return out.toString().replaceAll("\\n{3,}", "\n\n").strip();
	}

	private static void appendNode(StringBuilder out, Node node) {
		switch (node) {
			case TextNode text -> out.append(text.text());
			case Element element -> appendElement(out, element);
			case null, default -> {
			}
		}
	}

	private static void appendElement(StringBuilder out, Element element) {
		switch (element.tagName()) {
			case "script", "style", "noscript" -> {
			}
			case "h1", "h2", "h3", "h4", "h5", "h6" -> {
				int level = element.tagName().charAt(1) - '0';
				out.append("\n\n").append("#".repeat(level)).append(' ').append(element.text()).append("\n\n");
			}
			case "p" -> {
				out.append("\n\n");
				element.childNodes().forEach(child -> appendNode(out, child));
				out.append("\n\n");
			}
			case "br" -> out.append('\n');
			case "hr" -> out.append("\n\n---\n\n");
			case "pre" -> out.append("\n\n```\n").append(element.text()).append("\n```\n\n");
			case "blockquote" -> {
				out.append("\n\n");
				element
					.text()
					.lines()
					.forEach(line -> out.append("> ").append(line).append('\n'));
				out.append('\n');
			}
			case "ul", "ol" -> {
				out.append("\n\n");
				int index = 1;
				for (Element item : element.children()) {
					if (!item.tagName().equals("li")) {
						continue;
					}
					out.append(element.tagName().equals("ol") ? index++ + ". " : "- ");
					item.childNodes().forEach(child -> appendNode(out, child));
					out.append('\n');
				}
				out.append('\n');
			}
			case "a" -> {
				String text = element.text();
				if (text.isBlank()) {
					break;
				}
				String href = element.absUrl("href");
				out.append(href.isBlank() ? text : "[" + text + "](" + href + ")");
			}
			default -> element.childNodes().forEach(child -> appendNode(out, child));
		}
	}
}
