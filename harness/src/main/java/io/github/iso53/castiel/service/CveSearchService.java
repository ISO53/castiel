package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Vulnerability intelligence over free, keyless public sources:
 *
 * <ul>
 *   <li>keyword search and record detail from the CVE Program itself ({@code search.cve.org}
 *       and {@code cveawg.mitre.org}) — the authoritative registry every other vulnerability
 *       database derives from, so it is both the most complete and the most current;</li>
 *   <li>known-exploitation flags from the CISA-ADP vulnrichment the records themselves carry;</li>
 *   <li>public PoC counts from {@link PocSearchService}.</li>
 * </ul>
 *
 * <p>Results are cached in memory — records and enrichment feeds for half a day or a day —
 * so repeated agent questions stay cheap, in the spirit of the Search API's responsible-use
 * guide. Every request identifies the app, as that guide requires for distributed apps.
 */
@Service
public class CveSearchService {

	/** One search hit, with the enrichment flags merged in. */
	public record CveSummary(
		String cveId,
		String state,
		String title,
		String description,
		Double cvssScore,
		String severity,
		boolean knownExploited
	) {}

	/** CVSS metrics found on a record. Either version may be absent when a CNA omitted it. */
	public record Metric(Double score40, String vector40, Double score31, String vector31) {}

	/** One affected product with its declared version ranges. */
	public record Affected(String vendor, String product, String defaultStatus, List<VersionRange> versions) {}

	/** One version range exactly as the CNA declared it. */
	public record VersionRange(String version, String status, String lessThan, String lessThanOrEqual) {}

	/** The full detail view of one CVE record. */
	public record CveDetail(
		String cveId,
		String state,
		String title,
		String description,
		String assigner,
		String published,
		List<String> weaknesses,
		List<Affected> affected,
		Metric metric,
		boolean knownExploited,
		int publicPocCount,
		List<String> references
	) {}

	private static final String SEARCH_URL = "https://search.cve.org/search";
	private static final String DETAIL_URL = "https://cveawg.mitre.org/api/cve/";

	private static final Duration RECORD_TTL = Duration.ofHours(12);
	private static final Duration FEED_TTL = Duration.ofHours(24);
	/** Identical searches within this window are answered from memory, not the API. */
	private static final Duration SEARCH_TTL = Duration.ofMinutes(10);

	/** The CVE Search API's responsible-use guide requires app name, version and contact info. */
	private static final String USER_AGENT = "castiel (+https://github.com/ISO53/castiel)";

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final ObjectMapper JSON = new ObjectMapper();

	private record Entry(Instant expiresAt, Object value) {}

	private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

	private final PocSearchService pocSearchService;

	public CveSearchService(PocSearchService pocSearchService) {
		this.pocSearchService = pocSearchService;
	}

	/** One search answer: the ranked page plus the full match count behind the query. */
	public record SearchResult(List<CveSummary> summaries, int totalResults) {}

	/**
	 * Keyword search over the CVE Search API, ranked known-exploited first, then by CVSS
	 * score, then newest. Rejected records are dropped.
	 */
	public SearchResult search(String keyword, int limit, int pageNumber) {
		return cached("search:" + keyword + "|" + limit + "|" + pageNumber, SEARCH_TTL, () ->
			searchUncached(keyword, limit, pageNumber)
		);
	}

	private SearchResult searchUncached(String keyword, int limit, int pageNumber) {
		ObjectNode body = JSON.createObjectNode();
		body.put("keywordSearch", keyword);
		body.put("resultsPerPage", limit);
		// The API rejects pageNumber below 1 outright, and a missing keywordSearch would
		// return the entire registry — the tool layer guarantees a non-blank keyword.
		body.put("pageNumber", Math.max(1, pageNumber));

		List<CveSummary> summaries;
		int total;
		try {
			JsonNode response = postJson(SEARCH_URL, JSON.writeValueAsString(body));
			total = response.path("pagination").path("totalResults").asInt(0);
			summaries = parseSummaries(response.path("records"));

			// The API orders results by CVE id, so on broad queries the fetched window is
			// oldest-first and ranking inside it buries every modern CVE. When the whole
			// match set fits the 10k pagination window, re-fetch its newest slice (up to
			// 500 records) and rank that instead. Explicit pages paginate literally.
			if (total > limit && total <= 10_000 && pageNumber <= 1) {
				int pageSize = Math.min(total, 500);
				body.put("resultsPerPage", pageSize);
				body.put("pageNumber", (total + pageSize - 1) / pageSize);
				summaries = parseSummaries(postJson(SEARCH_URL, JSON.writeValueAsString(body)).path("records"));
			}
		} catch (IOException ex) {
			throw new IllegalStateException("CVE search failed: " + message(ex), ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("CVE search interrupted", ex);
		}

		summaries.sort(ranking());
		if (summaries.size() > limit) {
			summaries = new ArrayList<>(summaries.subList(0, limit));
		}
		return new SearchResult(summaries, total);
	}

	private List<CveSummary> parseSummaries(JsonNode records) {
		List<CveSummary> summaries = new ArrayList<>();
		for (JsonNode record : records) {
			JsonNode metadata = record.path("cveMetadata");
			String cveId = metadata.path("cveId").asText("");
			String state = metadata.path("state").asText("");
			if (cveId.isEmpty() || "REJECTED".equals(state)) {
				continue;
			}
			JsonNode cna = record.path("containers").path("cna");
			Double score = highestScore(metricOf(record));
			summaries.add(
				new CveSummary(
					cveId,
					state,
					cna.path("title").asText(null),
					firstDescription(cna),
					score,
					severityOf(score),
					kevOf(record)
				)
			);
		}
		return summaries;
	}

	/**
	 * The full record for one CVE, or {@code null} when the CVE Program has no such id.
	 * A rejected id still resolves and comes back with its REJECTED state for the caller to handle.
	 */
	public CveDetail detail(String cveId) {
		// The registry rejects lowercase ids in the path with a 400.
		String id = cveId == null ? "" : cveId.strip().toUpperCase(Locale.ROOT);
		JsonNode record = cached("cve:" + id, RECORD_TTL, () -> {
			try {
				return getJson(DETAIL_URL + URLEncoder.encode(id, StandardCharsets.UTF_8));
			} catch (IOException ex) {
				if (message(ex).contains("HTTP 404")) {
					return null;
				}
				throw new IllegalStateException("CVE lookup failed: " + message(ex), ex);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("CVE lookup interrupted", ex);
			}
		});
		if (record == null) {
			return null;
		}

		JsonNode metadata = record.path("cveMetadata");
		JsonNode cna = record.path("containers").path("cna");
		List<String> references = new ArrayList<>();
		for (JsonNode ref : cna.path("references")) {
			String url = ref.path("url").asText("");
			if (!url.isEmpty()) {
				references.add(url);
			}
		}

		return new CveDetail(
			metadata.path("cveId").asText(id),
			metadata.path("state").asText(""),
			cna.path("title").asText(null),
			firstDescription(cna),
			metadata.path("assignerShortName").asText(""),
			dateOf(metadata.path("datePublished").asText("")),
			weaknessesOf(cna),
			affectedOf(cna),
			metricOf(record),
			kevOf(record),
			pocSearchService.pocs(id).size(),
			references.stream().limit(10).toList()
		);
	}

	/** CISA's Known Exploited Vulnerabilities flag, taken from the record's own CISA-ADP container. */
	private static boolean kevOf(JsonNode record) {
		JsonNode containers = record.path("containers");
		if (hasKevMetric(containers.path("cna").path("metrics"))) {
			return true;
		}
		for (JsonNode adp : containers.path("adp")) {
			if (hasKevMetric(adp.path("metrics"))) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasKevMetric(JsonNode metrics) {
		for (JsonNode entry : metrics) {
			if ("kev".equals(entry.path("other").path("type").asText())) {
				return true;
			}
		}
		return false;
	}

	/** KEV first, then CVSS score, then newest. */
	private static Comparator<CveSummary> ranking() {
		return Comparator.comparing((CveSummary summary) -> summary.knownExploited() ? 0 : 1)
			.thenComparing(
				summary -> summary.cvssScore() == null ? -1.0 : summary.cvssScore(),
				Comparator.reverseOrder()
			)
			.thenComparing(CveSummary::cveId, Comparator.reverseOrder());
	}

	/**
	 * Collects CVSS metrics from the CNA container first, then from ADP containers (CISA
	 * publishes scores there), taking the first v4.0 and v3.1 (falling back to v3.0) found.
	 */
	private static Metric metricOf(JsonNode record) {
		JsonNode containers = record.path("containers");
		List<JsonNode> metricArrays = new ArrayList<>();
		metricArrays.add(containers.path("cna").path("metrics"));
		for (JsonNode adp : containers.path("adp")) {
			metricArrays.add(adp.path("metrics"));
		}
		Double score40 = null;
		String vector40 = null;
		Double score31 = null;
		String vector31 = null;
		for (JsonNode metrics : metricArrays) {
			for (JsonNode entry : metrics) {
				if (score40 == null) {
					JsonNode v40 = entry.path("cvssV4_0");
					if (v40.has("baseScore")) {
						score40 = v40.path("baseScore").asDouble();
						vector40 = v40.path("vectorString").asText("");
					}
				}
				if (score31 == null) {
					JsonNode v31 = entry.path("cvssV3_1");
					if (!v31.has("baseScore")) {
						v31 = entry.path("cvssV3_0");
					}
					if (v31.has("baseScore")) {
						score31 = v31.path("baseScore").asDouble();
						vector31 = v31.path("vectorString").asText("");
					}
				}
			}
		}
		return new Metric(score40, vector40, score31, vector31);
	}

	/** The English description, falling back to the first one present. */
	private static String firstDescription(JsonNode cna) {
		JsonNode descriptions = cna.path("descriptions");
		for (JsonNode description : descriptions) {
			if ("en".equals(description.path("lang").asText())) {
				return description.path("value").asText("");
			}
		}
		return descriptions.isEmpty() ? "" : descriptions.get(0).path("value").asText("");
	}

	/** CWE ids where given, human-readable weakness names otherwise; legacy "n/a" dropped. */
	private static List<String> weaknessesOf(JsonNode cna) {
		LinkedHashSet<String> weaknesses = new LinkedHashSet<>();
		for (JsonNode problemType : cna.path("problemTypes")) {
			for (JsonNode description : problemType.path("descriptions")) {
				String cwe = description.path("cweId").asText("");
				String text = naToBlank(description.path("description").asText(""));
				if (!cwe.isEmpty()) {
					weaknesses.add(cwe);
				} else if (!text.isEmpty()) {
					weaknesses.add(text);
				}
			}
		}
		return List.copyOf(weaknesses);
	}

	private static List<Affected> affectedOf(JsonNode cna) {
		List<Affected> affected = new ArrayList<>();
		for (JsonNode entry : cna.path("affected")) {
			String vendor = naToBlank(entry.path("vendor").asText(""));
			String product = naToBlank(entry.path("product").asText(""));
			// Legacy records ship "n/a" placeholders that would print as garbage rows.
			if (vendor.isBlank() && product.isBlank()) {
				continue;
			}
			List<VersionRange> versions = new ArrayList<>();
			for (JsonNode version : entry.path("versions")) {
				String number = naToBlank(version.path("version").asText(""));
				if (number.isBlank()) {
					continue;
				}
				versions.add(
					new VersionRange(
						number,
						version.path("status").asText(""),
						naToBlank(version.path("lessThan").asText("")),
						naToBlank(version.path("lessThanOrEqual").asText(""))
					)
				);
			}
			affected.add(
				new Affected(vendor, product, naToBlank(entry.path("defaultStatus").asText("")), List.copyOf(versions))
			);
		}
		return List.copyOf(affected);
	}

	/** Blank or "n/a" placeholders (legacy records) become blank for uniform handling. */
	private static String naToBlank(String value) {
		String stripped = value.strip().toLowerCase(Locale.ROOT);
		return stripped.equals("n/a") || stripped.equals("na") ? "" : value.strip();
	}

	private static Double highestScore(Metric metric) {
		if (metric.score40() != null && metric.score31() != null) {
			return Math.max(metric.score40(), metric.score31());
		}
		return metric.score40() != null ? metric.score40() : metric.score31();
	}

	/** FIRST's qualitative severity band for a 0-10 base score. */
	private static String severityOf(Double score) {
		if (score == null) {
			return null;
		}
		if (score <= 0) {
			return "NONE";
		} else if (score < 4) {
			return "LOW";
		} else if (score < 7) {
			return "MEDIUM";
		} else if (score < 9) {
			return "HIGH";
		}
		return "CRITICAL";
	}

	/** ISO timestamps become plain dates. */
	private static String dateOf(String timestamp) {
		return timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
	}

	private static String message(Exception exception) {
		return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
	}

	@SuppressWarnings("unchecked")
	private <V> V cached(String key, Duration ttl, Supplier<V> loader) {
		Entry hit = cache.get(key);
		if (hit != null && hit.expiresAt().isAfter(Instant.now())) {
			return (V) hit.value();
		}
		V value = loader.get();
		cache.put(key, new Entry(Instant.now().plus(ttl), value));
		return value;
	}

	private boolean cacheFresh(String key) {
		Entry hit = cache.get(key);
		return hit != null && hit.expiresAt().isAfter(Instant.now());
	}

	private JsonNode getJson(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", USER_AGENT)
			.header("Accept", "application/json")
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode() + " from " + url);
		}
		return JSON.readTree(response.body());
	}

	private JsonNode postJson(String url, String json) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode() + " from " + url);
		}
		return JSON.readTree(response.body());
	}
}
