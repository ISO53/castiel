package io.github.iso53.castiel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iso53.castiel.model.AttackTactic;
import io.github.iso53.castiel.model.AttackTechnique;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * In-memory index of MITRE ATT&CK Enterprise techniques extracted from MITRE's official
 * STIX bundle by {@code scripts/extract_attack_techniques.py}. Loaded once at startup
 * (the whole catalog is under a megabyte); provides keyword-scored search, tactic and
 * platform filtering, and exact-id lookup for the {@code search_attack} and
 * {@code attack_detail} tools and the explore dialog.
 */
@Service
public class AttackCatalog {

	private static final Logger log = LoggerFactory.getLogger(AttackCatalog.class);
	private static final String CATALOG_RESOURCE = "attack-techniques.json";
	private static final Pattern WORD_PATTERN = Pattern.compile("[^a-zA-Z0-9_\\-]+");
	/** Query filler words that match procedure text everywhere and rank nothing usefully. */
	private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "with", "via", "from", "may", "over", "using", "tool");
	private static final ObjectMapper JSON = new ObjectMapper();

	private List<AttackTechnique> techniques = List.of();
	private Map<String, AttackTechnique> techniquesById = Map.of();
	private List<AttackTactic> tactics = List.of();

	@PostConstruct
	public void initialize() {
		long start = System.currentTimeMillis();
		load();
		log.info(
			"Loaded {} ATT&CK techniques ({} tactics) in {} ms",
			techniques.size(),
			tactics.size(),
			System.currentTimeMillis() - start
		);
	}

	private void load() {
		try {
			ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
			if (!resource.exists()) {
				log.warn("ATT&CK catalog resource {} not found; catalog is empty", CATALOG_RESOURCE);
				return;
			}
			try (InputStream is = resource.getInputStream()) {
				JsonNode root = JSON.readTree(is);
				this.techniques = List.copyOf(
					JSON.treeToValue(root.path("techniques"), new TypeReference<List<AttackTechnique>>() {})
				);
				Map<String, AttackTechnique> byId = new LinkedHashMap<>();
				for (AttackTechnique technique : this.techniques) {
					byId.put(technique.id().toLowerCase(Locale.ROOT), technique);
				}
				this.techniquesById = Map.copyOf(byId);

				List<AttackTactic> tactics = new ArrayList<>();
				int order = 1;
				for (JsonNode tactic : root.path("tactics")) {
					tactics.add(
						new AttackTactic(tactic.path("name").asText(""), tactic.path("shortname").asText(""), order++)
					);
				}
				this.tactics = List.copyOf(tactics);
			}
		} catch (Exception ex) {
			log.error("Failed to load ATT&CK catalog: {}", ex.getMessage(), ex);
		}
	}

	/** Every technique, sorted by id. */
	public List<AttackTechnique> all() {
		return techniques;
	}

	/** The tactics derived from the bundled data, alphabetically; the filter's valid vocabulary. */
	public List<AttackTactic> tactics() {
		return tactics;
	}

	/** Distinct platform names in the catalog, sorted; the platform filter's valid vocabulary. */
	public List<String> platforms() {
		return techniques
			.stream()
			.flatMap(technique -> technique.platforms().stream())
			.distinct()
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.toList();
	}

	/** Whether a platform filter value matches any known platform (same rule the filter uses). */
	public boolean isKnownPlatform(String platform) {
		if (platform == null || platform.isBlank()) {
			return false;
		}
		String lowered = platform.strip().toLowerCase(Locale.ROOT);
		return platforms().stream().anyMatch(known -> known.toLowerCase(Locale.ROOT).contains(lowered));
	}

	/**
	 * Resolves a user-supplied tactic value (shortname like {@code lateral-movement} or display
	 * name like {@code Lateral Movement}) to a tactic. Empty when the value is unknown, so
	 * callers can reject it instead of silently filtering everything out.
	 */
	public Optional<AttackTactic> resolveTactic(String input) {
		if (input == null || input.isBlank()) {
			return Optional.empty();
		}
		String cleaned = input.strip().toLowerCase(Locale.ROOT).replace(' ', '-');
		for (AttackTactic known : tactics) {
			if (known.shortname().equalsIgnoreCase(cleaned) || known.name().equalsIgnoreCase(input.strip())) {
				return Optional.of(known);
			}
		}
		return Optional.empty();
	}

	/** Display name for a tactic shortname, or the input unchanged when unknown. */
	public String tacticDisplayName(String shortname) {
		for (AttackTactic tactic : tactics) {
			if (tactic.shortname().equalsIgnoreCase(shortname)) {
				return tactic.name();
			}
		}
		return shortname;
	}

	/** Exact lookup by technique id, case-insensitive. */
	public Optional<AttackTechnique> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(techniquesById.get(id.strip().toLowerCase(Locale.ROOT)));
	}

	/**
	 * Keyword-scored search with optional tactic and platform filters, capped at a
	 * small result count — the budget for the LLM tool calls. An exact technique-id
	 * query (e.g. {@code T1021.002}) resolves directly to that technique.
	 */
	public List<AttackTechnique> search(String query, String tactic, String platform, Integer max) {
		int limit = max == null ? 8 : Math.clamp(max, 1, 25);
		return ranked(query, tactic, platform).stream().limit(limit).toList();
	}

	/**
	 * Same ranking as {@link #search}, but uncapped — the budget for the explore
	 * dialog, where a human browses the full catalog and the 25-result clamp that
	 * protects the model's context window has no business.
	 */
	public List<AttackTechnique> browse(String query, String tactic, String platform) {
		return ranked(query, tactic, platform);
	}

	/** Filters by tactic/platform, ranks by query relevance; relevance order, uncapped. */
	private List<AttackTechnique> ranked(String query, String tactic, String platform) {
		// Unknown filter values return nothing rather than silently ignoring the filter:
		// "no results" must never masquerade as "all results".
		Optional<AttackTactic> resolvedTactic = resolveTactic(tactic);
		if (tactic != null && !tactic.isBlank() && resolvedTactic.isEmpty()) {
			return List.of();
		}
		String tacticFilter = resolvedTactic.map(AttackTactic::shortname).orElse(null);
		String platformFilter =
			platform == null || platform.isBlank() ? null : platform.strip().toLowerCase(Locale.ROOT);
		if (platformFilter != null && !isKnownPlatform(platform)) {
			return List.of();
		}

		List<AttackTechnique> candidates = techniques
			.stream()
			.filter(technique -> tacticFilter == null || technique.tactics().contains(tacticFilter))
			.filter(
				technique ->
					platformFilter == null ||
					technique
						.platforms()
						.stream()
						.anyMatch(p -> p.toLowerCase(Locale.ROOT).contains(platformFilter))
			)
			.toList();

		if (query == null || query.isBlank()) {
			return candidates;
		}

		String cleanQuery = query.toLowerCase(Locale.ROOT).strip();
		Optional<AttackTechnique> exact = findById(cleanQuery);
		if (exact.isPresent()) {
			return List.of(exact.get());
		}

		List<String> tokens = Arrays.stream(WORD_PATTERN.split(cleanQuery))
			.map(String::strip)
			.filter(s -> s.length() >= 2)
			.filter(s -> !STOPWORDS.contains(s))
			.toList();
		if (tokens.isEmpty()) {
			// A query of only trivial tokens ("a", "!!!") matches nothing meaningful;
			// serving the first rows of the catalog would present arbitrary techniques.
			return List.of();
		}

		record ScoredTechnique(AttackTechnique technique, int score) {}
		List<ScoredTechnique> scored = new ArrayList<>();
		for (AttackTechnique technique : candidates) {
			int score = score(technique, cleanQuery, tokens);
			if (score > 0) {
				scored.add(new ScoredTechnique(technique, score));
			}
		}
		scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
		return scored.stream().map(ScoredTechnique::technique).toList();
	}

	private int score(AttackTechnique technique, String cleanQuery, List<String> tokens) {
		int score = 0;
		String id = technique.id().toLowerCase(Locale.ROOT);
		String name = technique.name().toLowerCase(Locale.ROOT);

		if (id.equals(cleanQuery) || name.equals(cleanQuery)) {
			score += 100;
		}

		List<String> nameWords = words(technique.name());
		List<String> keywordWords = technique.keywords();
		List<String> softwareWords = words(String.join(" ", technique.software()));
		List<String> summaryWords = words(technique.summary());
		List<String> procedureWords = words(
			technique
				.procedures()
				.stream()
				.map(p -> p.source() + " " + p.text())
				.reduce("", (a, b) -> a + " " + b)
		);

		int tokensMatched = 0;
		for (String token : tokens) {
			List<String> variants = tokenVariants(token);
			boolean tokenFound = false;
			if (containsAny(variants, id)) {
				score += 30;
				tokenFound = true;
			}
			if (matchWord(nameWords, variants)) {
				score += 30;
				tokenFound = true;
			} else if (matchWord(keywordWords, variants)) {
				// Pentest shorthand ("wmi", "pth") matching a derived acronym.
				score += 25;
				tokenFound = true;
			}
			if (matchWord(softwareWords, variants)) {
				score += 15;
				tokenFound = true;
			}
			if (matchWord(procedureWords, variants)) {
				score += 8;
				tokenFound = true;
			}
			if (matchWord(summaryWords, variants)) {
				score += 8;
				tokenFound = true;
			}
			if (tokenFound) {
				tokensMatched++;
			}
		}
		if (tokensMatched == tokens.size()) {
			score += 35;
		}
		return score;
	}

	private static List<String> words(String text) {
		return Arrays.stream(WORD_PATTERN.split(text == null ? "" : text.toLowerCase(Locale.ROOT)))
			.filter(s -> !s.isEmpty())
			.toList();
	}

	/**
	 * Light stemming of a query token: plurals and verb forms ("shares", "queries",
	 * "exfiltrate") against ATT&CK's singular-noun phrasing ("share", "query",
	 * "exfiltration"). The variants are tried in addition to the raw token.
	 */
	private static List<String> tokenVariants(String token) {
		List<String> variants = new ArrayList<>();
		variants.add(token);
		if (token.endsWith("ies") && token.length() > 4) {
			variants.add(token.substring(0, token.length() - 3) + "y");
		}
		if (token.endsWith("es") && token.length() > 4) {
			variants.add(token.substring(0, token.length() - 2));
		}
		if (token.endsWith("s") && token.length() > 3) {
			variants.add(token.substring(0, token.length() - 1));
		}
		// Nominal/verbal suffixes: "execution" -> "execut" (matches "execute"),
		// "exfiltrate" -> "exfiltrat" (matches "exfiltration").
		if (token.endsWith("ion") && token.length() > 5) {
			variants.add(token.substring(0, token.length() - 3));
		}
		if (token.endsWith("e") && token.length() > 5) {
			variants.add(token.substring(0, token.length() - 1));
		}
		return variants;
	}

	private static boolean containsAny(List<String> variants, String text) {
		return variants.stream().anyMatch(text::contains);
	}

	/**
	 * Word-level match with prefix tolerance in both directions: a token matches when a
	 * word equals one of its variants, extends it, or is extended by it, with a minimum
	 * stem length so short tokens stay exact.
	 */
	private static boolean matchWord(List<String> words, List<String> variants) {
		for (String word : words) {
			for (String token : variants) {
				if (word.equals(token)) {
					return true;
				}
				if (word.length() >= 4 && token.startsWith(word)) {
					return true;
				}
				if (token.length() >= 4 && word.startsWith(token)) {
					return true;
				}
			}
		}
		return false;
	}
}
