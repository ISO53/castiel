package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iso53.castiel.util.Http;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Public proof-of-concept lookup over PoC-in-GitHub (github.com/nomi-sec/PoC-in-GitHub),
 * a CC0-licensed index that continuously collects GitHub repositories containing PoC
 * exploits for CVEs. The index is CVE-id keyed, so lookups are exact, and results
 * arrive pre-sorted by GitHub stars.
 */
@Service
public class PocSearchService {

	/** One indexed PoC repository. The API reports stars as a string, e.g. "2884". */
	public record Poc(String fullName, String url, String description, int stars, String pushedAt) {}

	private static final String API_URL = "https://poc-in-github.motikan2010.net/api/v1/";

	/**
	 * The service honors the limit up to the full result set (log4shell alone has 451
	 * PoCs), and a large page keeps the lookup to a single request per CVE.
	 */
	private static final int FETCH_LIMIT = 1000;
	private static final Duration TTL = Duration.ofHours(12);

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final ObjectMapper JSON = new ObjectMapper();

	private record Entry(Instant expiresAt, List<Poc> value) {}

	private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

	/** The indexed PoC repositories for one CVE, best-starred first. */
	public List<Poc> pocs(String cveId) {
		Entry hit = cache.get(cveId);
		if (hit != null && hit.expiresAt().isAfter(Instant.now())) {
			return hit.value();
		}
		List<Poc> pocs = fetch(cveId);
		cache.put(cveId, new Entry(Instant.now().plus(TTL), pocs));
		return pocs;
	}

	private List<Poc> fetch(String cveId) {
		String url =
			API_URL +
			"?cve_id=" +
			URLEncoder.encode(cveId, StandardCharsets.UTF_8) +
			"&sort=stargazers_count&limit=" +
			FETCH_LIMIT;
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", Http.USER_AGENT)
			.GET()
			.build();
		try {
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IOException("HTTP " + response.statusCode());
			}
			List<Poc> pocs = new ArrayList<>();
			for (JsonNode entry : JSON.readTree(response.body()).path("pocs")) {
				pocs.add(
					new Poc(
						entry.path("full_name").asText(""),
						entry.path("html_url").asText(""),
						entry.path("description").asText(""),
						parseStars(entry.path("stargazers_count").asText("0")),
						entry.path("pushed_at").asText("")
					)
				);
			}
			// The API sorts by stars already; the re-sort keeps that guarantee local.
			pocs.sort((a, b) -> Integer.compare(b.stars(), a.stars()));
			return List.copyOf(pocs);
		} catch (IOException ex) {
			throw new IllegalStateException("PoC lookup failed: " + ex.getMessage(), ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("PoC lookup interrupted", ex);
		}
	}

	private static int parseStars(String raw) {
		try {
			return Integer.parseInt(raw.strip());
		} catch (NumberFormatException ex) {
			return 0;
		}
	}
}
