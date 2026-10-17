package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.CveSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Vulnerability lookups against the CVE Program's own registry — the freshest and most
 * complete vulnerability source there is, so findings are not limited to what the model
 * already knows. Keyword search goes over the new CVE Search API; record detail comes
 * from the CVE Services API. Both carry known-exploitation (KEV) flags from CISA's
 * KEV status and public PoC counts.
 */
@Service
public class VulnSearchTool implements ToolProvider {

	public static final String SEARCH_NAME = "vuln_search";
	public static final String DETAIL_NAME = "vuln_detail";

	private static final int DEFAULT_RESULTS = 8;
	private static final int MAX_RESULTS = 20;
	private static final int MAX_PAGE = 500;
	private static final int MAX_ATTEMPTS = 3;

	/** Version-shaped tokens, e.g. "2.4.49", "v3.2.5", "10.2.9-h1". */
	private static final Pattern VERSION_TOKEN = Pattern.compile("[vV]?\\d+(?:\\.\\d+)+(?:[-+][A-Za-z0-9.]+)?\\b");
	private static final Pattern CVE_ID = Pattern.compile("CVE-\\d{4}-\\d{4,}", Pattern.CASE_INSENSITIVE);

	private final CveSearchService cveSearchService;

	public VulnSearchTool(CveSearchService cveSearchService) {
		this.cveSearchService = cveSearchService;
	}

	@Tool(
		name = SEARCH_NAME,
		value = {
			"Searches the official CVE registry for vulnerabilities, so findings can use knowledge",
			"newer than the model's training data. Accepts product names, versions and CVE ids.",
			"Version tokens are handled: if a product+version query matches nothing, the search",
			"relaxes step by step and flags results whose text mentions the version. Results",
			"carry CVSS score and known-exploitation (KEV) flags; ranking puts actively exploited",
			"and high-CVSS records first across the newest matches. Page 1 ranks the newest",
			"matches; higher pages paginate the id-ordered list without re-ranking. Numeric",
			"arguments outside their ranges are clamped. Flag meanings: KEV = CISA confirmed",
			"exploitation in the wild; CVSS = static severity. A KEV-listed vulnerability is a",
			"better lead than an unexploited critical. Call vuln_detail for affected version",
			"ranges and references.",
		}
	)
	public String search(
		@P("Product name, version or CVE id, e.g. 'apache httpd 2.4.49' or 'CVE-2021-44228'") String query,
		@P("Optional maximum number of results between 1 and 20 (default 8); values outside the range are clamped") Integer limit,
		@P("Optional result page starting at 1 (default 1); values below 1 are clamped") Integer page
	) {
		if (query == null || query.isBlank()) {
			return "Error: query is required";
		}
		String keyword = query.strip();
		int max = limit == null ? DEFAULT_RESULTS : (int) Math.clamp(limit.longValue(), 1, MAX_RESULTS);
		int pageNumber = page == null ? 1 : (int) Math.clamp(page.longValue(), 1, MAX_PAGE);

		// A CVE id resolves straight to the record: the registry's keyword search cannot
		// match ids (they are not in the searchable text).
		Matcher idMatcher = CVE_ID.matcher(keyword);
		if (idMatcher.find()) {
			return detail(idMatcher.group().toUpperCase(Locale.ROOT));
		}

		List<String> versions = versionTokens(keyword);
		List<String> attempts = new ArrayList<>();
		attempts.add(keyword);
		String productOnly = withoutVersions(keyword, versions);
		if (!productOnly.isBlank()) {
			attempts.add(productOnly);
		}
		if (!versions.isEmpty()) {
			attempts.add(String.join(" ", versions));
		}

		CveSearchService.SearchResult found = null;
		String usedQuery = keyword;
		try {
			for (String attempt : attempts.subList(0, Math.min(MAX_ATTEMPTS, attempts.size()))) {
				found = cveSearchService.search(attempt, max, pageNumber);
				usedQuery = attempt;
				// A relaxed attempt only wins when it actually matches the version we are
				// hunting for; otherwise keep relaxing down the ladder.
				if (!found.summaries().isEmpty()
					&& (versions.isEmpty() || mentionsAnyVersion(found.summaries(), versions))) {
					break;
				}
			}
		} catch (RuntimeException ex) {
			return "Error: " + ex.getMessage();
		}
		List<CveSearchService.CveSummary> results = found == null ? List.of() : found.summaries();
		if (results.isEmpty()) {
			int total = found == null ? 0 : found.totalResults();
			if (total > 0) {
				// The query matched; the caller's page just ran past the results.
				int pages = (total + max - 1) / max;
				return "No results on page " + pageNumber + " — \"" + usedQuery + "\" matched "
					+ total + " CVE(s) at " + max + " per page (" + pages + " page(s) exist). Try a lower page.";
			}
			return "No CVEs found for: " + keyword
				+ ". If that is an exploit nickname (like log4shell or pwnkit), resolve it to its"
				+ " CVE id from your own knowledge and call vuln_detail with the id instead.";
		}
		if (!versions.isEmpty()) {
			// Version-matching records lead within their rank tier; the rest stay visible
			// because a description like "2.4.49 and earlier" can phrase it differently.
			results = Stream.concat(
					results.stream().filter(summary -> mentionsAnyVersion(List.of(summary), versions)),
					results.stream().filter(summary -> !mentionsAnyVersion(List.of(summary), versions)))
				.toList();
		}

		StringBuilder out = new StringBuilder();
		if (!usedQuery.equalsIgnoreCase(keyword)) {
			out.append("(relaxed query: \"").append(usedQuery).append("\")\n");
		}
		out.append("Found ").append(results.size()).append(" CVE(s):\n\n");
		for (int i = 0; i < results.size(); i++) {
			CveSearchService.CveSummary summary = results.get(i);
			out.append('[').append(i + 1).append("] ").append(summary.cveId())
				.append(badges(summary, versions)).append('\n');
			out.append("  ").append(summarize(summary)).append('\n');
			out.append("  call vuln_detail(\"").append(summary.cveId())
				.append("\") for affected versions, references and PoC counts.\n\n");
		}
		return out.toString().stripTrailing();
	}

	@Tool(
		name = DETAIL_NAME,
		value = {
			"Returns one CVE record in full: description, affected products with exact version",
			"ranges (verify the observed version against them before calling something vulnerable),",
			"CWE weaknesses, CVSS vectors, KEV exploitation status and references. CVE ids",
			"must come from vuln_search, never invented.",
		}
	)
	public String detail(@P("CVE id from vuln_search results, e.g. 'CVE-2021-44228'") String cveId) {
		if (cveId == null || !cveId.strip().toUpperCase(Locale.ROOT).matches("CVE-\\d{4}-\\d{4,}")) {
			return "Error: a valid CVE id is required, e.g. CVE-2021-44228";
		}
		CveSearchService.CveDetail detail;
		try {
			detail = cveSearchService.detail(cveId.strip().toUpperCase(Locale.ROOT));
		} catch (RuntimeException ex) {
			return "Error: " + ex.getMessage();
		}
		if (detail == null) {
			return "No CVE record found for " + cveId.strip();
		}
		if ("REJECTED".equals(detail.state())) {
			return detail.cveId() + " is REJECTED in the CVE registry and is not a vulnerability.";
		}

		StringBuilder out = new StringBuilder();
		out.append(detail.cveId()).append(" — ").append(detail.state());
		if (!detail.assigner().isBlank()) {
			out.append(" (assigner ").append(detail.assigner()).append(')');
		}
		if (!detail.published().isBlank()) {
			out.append(", published ").append(detail.published());
		}
		out.append('\n');
		if (detail.title() != null && !detail.title().isBlank()) {
			out.append(detail.title()).append('\n');
		}
		if (!detail.description().isBlank()) {
			out.append('\n').append(detail.description()).append('\n');
		}
		if (!detail.weaknesses().isEmpty()) {
			out.append("\nWeaknesses: ").append(String.join(", ", detail.weaknesses())).append('\n');
		}
		if (detail.metric() != null && (detail.metric().score40() != null || detail.metric().score31() != null)) {
			out.append("\nCVSS: ");
			boolean both = detail.metric().score40() != null && detail.metric().score31() != null;
			if (detail.metric().score40() != null) {
				out.append("v4.0 ").append(score(detail.metric().score40())).append(' ').append(detail.metric().vector40());
			}
			if (both) {
				out.append(" | ");
			}
			if (detail.metric().score31() != null) {
				out.append("v3.1 ").append(score(detail.metric().score31())).append(' ').append(detail.metric().vector31());
			}
			out.append('\n');
		}
		out.append("Known exploited in the wild (CISA KEV): ").append(detail.knownExploited() ? "yes" : "no").append('\n');
		out.append("Public PoCs on GitHub: ").append(detail.publicPocCount() >= 1000 ? "1000+" : detail.publicPocCount());
		if (detail.publicPocCount() > 0) {
			out.append(" — call exploit_search(\"").append(detail.cveId()).append("\") for links");
		}
		out.append('\n');

		if (!detail.affected().isEmpty()) {
			out.append("\nAffected products:\n");
			List<CveSearchService.Affected> shown = detail.affected().stream().limit(8).toList();
			for (CveSearchService.Affected product : shown) {
				out.append("- ").append(productName(product)).append(": ").append(rangeSummary(product)).append('\n');
			}
			if (detail.affected().size() > shown.size()) {
				out.append("  (+").append(detail.affected().size() - shown.size()).append(" more products)\n");
			}
		}
		if (!detail.references().isEmpty()) {
			out.append("\nReferences:\n");
			for (String url : detail.references()) {
				out.append("- ").append(url).append('\n');
			}
		}
		return out.toString().stripTrailing();
	}

	private static String badges(CveSearchService.CveSummary summary, List<String> versions) {
		StringBuilder badges = new StringBuilder();
		if (mentionsAnyVersion(List.of(summary), versions)) {
			badges.append(" [version ").append(String.join(", ", versions)).append(']');
		}
		if (summary.knownExploited()) {
			badges.append(" [KEV]");
		}
		if (summary.cvssScore() != null) {
			badges.append(" [CVSS ").append(score(summary.cvssScore())).append(' ').append(summary.severity()).append(']');
		}
		return badges.toString();
	}

	private static String summarize(CveSearchService.CveSummary summary) {
		String title = summary.title() == null || summary.title().isBlank() ? "" : summary.title() + " — ";
		return truncate(title + summary.description(), 240);
	}

	/** Version-shaped tokens in the query, e.g. "2.4.49" or "10.2.9-h1". */
	private static List<String> versionTokens(String query) {
		List<String> versions = new ArrayList<>();
		for (String token : query.split("\\s+")) {
			if (VERSION_TOKEN.matcher(token).matches()) {
				versions.add(token);
			}
		}
		return versions;
	}

	/** The query with its version tokens removed, so product words can search alone. */
	private static String withoutVersions(String query, List<String> versions) {
		List<String> kept = new ArrayList<>();
		for (String token : query.split("\\s+")) {
			if (!versions.contains(token)) {
				kept.add(token);
			}
		}
		return String.join(" ", kept).strip();
	}

	/** Whether any summary's searchable text mentions one of the version tokens. */
	private static boolean mentionsAnyVersion(List<CveSearchService.CveSummary> summaries, List<String> versions) {
		if (versions.isEmpty()) {
			return false;
		}
		for (CveSearchService.CveSummary summary : summaries) {
			String text = ((summary.title() == null ? "" : summary.title()) + " " + summary.description())
				.toLowerCase(Locale.ROOT);
			for (String version : versions) {
				if (text.contains(version.toLowerCase(Locale.ROOT))) {
					return true;
				}
			}
		}
		return false;
	}

	private static String productName(CveSearchService.Affected product) {
		return product.vendor().isBlank() ? product.product() : product.vendor() + " " + product.product();
	}

	private static String rangeSummary(CveSearchService.Affected product) {
		if (product.versions().isEmpty()) {
			return product.defaultStatus().isBlank() ? "no version details given" : "default status: " + product.defaultStatus();
		}
		StringBuilder out = new StringBuilder();
		List<CveSearchService.VersionRange> ranges = product.versions().stream().limit(6).toList();
		for (CveSearchService.VersionRange range : ranges) {
			if (out.length() > 0) {
				out.append("; ");
			}
			out.append(range.version());
			if (!range.lessThan().isBlank()) {
				out.append(" < ").append(range.lessThan());
			} else if (!range.lessThanOrEqual().isBlank()) {
				out.append(" <= ").append(range.lessThanOrEqual());
			}
			if (!range.status().isBlank()) {
				out.append(" (").append(range.status()).append(')');
			}
		}
		if (product.versions().size() > ranges.size()) {
			out.append("; +").append(product.versions().size() - ranges.size()).append(" more versions");
		}
		return out.toString();
	}

	/** One-decimal formatting; kills float noise like 99.99900000000001. */
	private static String score(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static String truncate(String text, int max) {
		String stripped = text.strip();
		if (stripped.length() <= max) {
			return stripped;
		}
		return stripped.substring(0, max).stripTrailing() + "…";
	}
}