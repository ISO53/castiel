package io.github.iso53.castiel.tool.webfetch;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.tool.ToolProvider;
import io.github.iso53.castiel.util.Text;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * Fetches URLs for the model. HTML is converted to markdown (or returned raw / as plain
 * text on request); every other textual content type (JSON, XML, plain text) passes
 * through verbatim. Bodies over 5 MB and non-textual content are refused.
 */
@Service
public class WebFetchTool implements ToolProvider {

	private static final int MAX_FETCH_CHARS = 20_000;

	/** Hard cap on how much of a response body is downloaded before the fetch is refused. */
	private static final int MAX_BODY_BYTES = 5_000_000;

	/** Honest UA so servers can see who is fetching; a bare variant for Cloudflare challenges. */
	private static final String USER_AGENT = "castiel (+https://github.com/ISO53/castiel)";
	private static final String BARE_USER_AGENT = "castiel";

	/** Notice appended to a page too long to hand to the model whole. */
	private static final String TRUNCATION_NOTICE = "\n... [truncated at %d characters]";

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Tool(
		name = "web_fetch",
		value = {
			"Fetches a URL and returns its content. HTML pages are converted to readable markdown",
			"by default; format=html returns the raw source, format=text returns stripped plain text.",
			"JSON, XML and plain-text responses are returned verbatim. Accepts http and https.",
			"The full content is returned; analyze it yourself.",
		}
	)
	public String webFetch(
		@P("Absolute http(s) URL to fetch") String url,
		@P(
			"Output format for HTML pages: markdown (default), text, or html. Ignored for other content types."
		) String format
	) {
		if (url == null || url.isBlank()) {
			return "Error: url is required";
		}
		String cleanedUrl = url.strip();
		if (!cleanedUrl.startsWith("http://") && !cleanedUrl.startsWith("https://")) {
			return "Error: only absolute http(s) URLs are supported";
		}
		String outputFormat = format == null || format.isBlank() ? "markdown" : format.strip().toLowerCase();
		if (!outputFormat.equals("markdown") && !outputFormat.equals("text") && !outputFormat.equals("html")) {
			return "Error: format must be markdown, text, or html";
		}

		HttpResponse<InputStream> response = null;
		String fetchError = null;
		String userAgent = USER_AGENT;
		URI uri = null;
		try {
			uri = URI.create(cleanedUrl);
		} catch (IllegalArgumentException ex) {
			return "Error: malformed URL '" + cleanedUrl + "': " + ex.getMessage();
		}
		for (int attempt = 0; attempt < 2 && response == null; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(60))
					.header("User-Agent", userAgent)
					.header("Accept", acceptHeader(outputFormat))
					.GET()
					.build();
				HttpResponse<InputStream> candidate = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
				int status = candidate.statusCode();
				if (status == 429 || status == 503) {
					closeQuietly(candidate.body());
					Thread.sleep(2_000L * (attempt + 1));
					continue;
				}
				if (
					status == 403 &&
					userAgent.equals(USER_AGENT) &&
					candidate
						.headers()
						.firstValue("cf-mitigated")
						.map(value -> value.contains("challenge"))
						.orElse(false)
				) {
					closeQuietly(candidate.body());
					userAgent = BARE_USER_AGENT; // retry once without the full UA string
					continue;
				}
				response = candidate;
			} catch (IOException ex) {
				fetchError = describe(ex);
				try {
					Thread.sleep(1_500L);
				} catch (InterruptedException sleepEx) {
					Thread.currentThread().interrupt();
					return "Error: interrupted while fetching " + cleanedUrl;
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return "Error: interrupted while fetching " + cleanedUrl;
			}
		}

		if (response == null) {
			return "Error: unable to fetch " + cleanedUrl + (fetchError == null ? " after retries" : ": " + fetchError);
		}
		if (response.statusCode() != 200) {
			closeQuietly(response.body());
			return "Error: the server returned HTTP " + response.statusCode() + " for " + cleanedUrl;
		}
		String contentType = response.headers().firstValue("content-type").orElse("").strip();
		String mime = contentType.isBlank() ? "text/plain" : contentType.split(";", 2)[0].strip().toLowerCase();
		if (!contentType.isBlank() && !isTextualMime(mime)) {
			closeQuietly(response.body());
			return (
				"Error: unsupported content type '" +
				contentType +
				"' for " +
				cleanedUrl +
				" — only textual content (HTML, JSON, XML, plain text) is fetched"
			);
		}

		String body;
		try {
			body = readBounded(response.body(), response.headers().firstValueAsLong("content-length").orElse(-1), contentType);
		} catch (IOException ex) {
			return "Error: unable to fetch " + cleanedUrl + ": " + describe(ex);
		}

		String output;
		boolean html = mime.equals("text/html") || mime.equals("application/xhtml+xml");
		if (!html) {
			output = body.strip(); // JSON, XML, plain text pass through verbatim
		} else {
			switch (outputFormat) {
				case "html" -> output = body;
				case "text" -> {
					org.jsoup.nodes.Document document = Jsoup.parse(body, cleanedUrl);
					document.select("script, style, noscript, iframe, object, embed").remove();
					Element bodyElement = document.body();
					output = bodyElement == null ? "" : bodyElement.text();
				}
				default -> {
					org.jsoup.nodes.Document document = Jsoup.parse(body, cleanedUrl);
					Element bodyElement = document.body();
					if (bodyElement == null) {
						return "(no readable content at " + cleanedUrl + ")";
					}
					output = HtmlToMarkdown.convert(bodyElement);
					if (output.isBlank()) {
						return "(no readable content at " + cleanedUrl + ")";
					}
					String title = document.title();
					if (!title.isBlank()) {
						output = "# " + title + "\n\n" + output;
					}
				}
			}
		}
		return renderResult(cleanedUrl, contentType, mime, outputFormat, output);
	}

	/** Whether the MIME type is textual: text/*, JSON, XML, or JavaScript. */
	private static boolean isTextualMime(String mime) {
		return (
			mime.startsWith("text/") ||
			mime.equals("application/json") ||
			mime.endsWith("+json") ||
			mime.equals("application/xml") ||
			mime.endsWith("+xml") ||
			mime.contains("javascript")
		);
	}

	/**
	 * Reads the response body capped at {@link #MAX_BODY_BYTES}. A declared
	 * Content-Length over the cap refuses before any download; an undeclared
	 * body is cut off as soon as the cap is exceeded while streaming.
	 */
	private static String readBounded(InputStream in, long declaredLength, String contentType) throws IOException {
		if (declaredLength > MAX_BODY_BYTES) {
			throw new IOException(
				"response body declared " + declaredLength + " bytes, over the " + MAX_BODY_BYTES + " byte limit"
			);
		}
		Charset charset = getCharset(contentType);
		try (in) {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] chunk = new byte[8_192];
			int read;
			while ((read = in.read(chunk)) != -1) {
				if (buffer.size() + read > MAX_BODY_BYTES) {
					throw new IOException(
						"response body exceeded the " + MAX_BODY_BYTES + " byte limit while streaming"
					);
				}
				buffer.write(chunk, 0, read);
			}
			return new String(buffer.toByteArray(), charset);
		}
	}

	/**
	 * Extracts the character encoding from a Content-Type header
	 * (e.g. "text/html; charset=windows-1251"). Falls back to UTF-8 when absent or invalid.
	 */
	private static Charset getCharset(String contentType) {
		if (contentType != null) {
			for (String part : contentType.split(";")) {
				String attribute = part.strip().toLowerCase();
				if (attribute.startsWith("charset=")) {
					String name = attribute.substring("charset=".length()).replace("\"", "").strip();
					try {
						return Charset.forName(name);
					} catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
						// Invalid or unsupported charset name, fall back to UTF-8 below.
					}
				}
			}
		}
		return StandardCharsets.UTF_8;
	}

	/** Per-format Accept header so servers can negotiate what we actually read. */
	private static String acceptHeader(String format) {
		return switch (format) {
			case "html" -> "text/html;q=1.0, */*;q=0.1";
			case "text" -> "text/plain;q=1.0, text/html;q=0.9, */*;q=0.1";
			default -> "text/html;q=1.0, text/plain;q=0.9, application/json;q=0.9, */*;q=0.1";
		};
	}

	/** Normalizes fetch failures: a real message when present, the exception class otherwise — never null. */
	private static String describe(IOException ex) {
		String message = ex.getMessage();
		return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
	}

	private static void closeQuietly(AutoCloseable closeable) {
		try {
			closeable.close();
		} catch (Exception ignored) {}
	}

	/** Uniform result header so the model always sees what kind of content it received. */
	private static String renderResult(String url, String contentType, String mime, String format, String output) {
		if (output == null || output.isBlank()) {
			return "(no readable content at " + url + ")";
		}
		output = Text.truncate(output, MAX_FETCH_CHARS, TRUNCATION_NOTICE);
		return (
			"URL: " +
			url +
			"\nContent-Type: " +
			(contentType.isBlank() ? mime : contentType) +
			"\nFormat: " +
			format +
			"\n\n" +
			output
		);
	}
}
