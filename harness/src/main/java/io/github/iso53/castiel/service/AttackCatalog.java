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

	/** The tactics in canonical matrix order. */
	public List<AttackTactic> tactics() {
		return tactics;
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
		String tacticFilter = normalizedTactic(tactic);
		String platformFilter =
			platform == null || platform.isBlank() ? null : platform.strip().toLowerCase(Locale.ROOT);

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
			.toList();
		if (tokens.isEmpty()) {
			return candidates;
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

	private String normalizedTactic(String tactic) {
		if (tactic == null || tactic.isBlank()) {
			return null;
		}
		String cleaned = tactic.strip().toLowerCase(Locale.ROOT).replace(' ', '-');
		for (AttackTactic known : tactics) {
			if (known.shortname().equalsIgnoreCase(cleaned) || known.name().equalsIgnoreCase(tactic.strip())) {
				return known.shortname();
			}
		}
		return cleaned;
	}

	private int score(AttackTechnique technique, String cleanQuery, List<String> tokens) {
		int score = 0;
		String id = technique.id().toLowerCase(Locale.ROOT);
		String name = technique.name().toLowerCase(Locale.ROOT);
		String summary = technique.summary().toLowerCase(Locale.ROOT);
		String detection = technique.detection().toLowerCase(Locale.ROOT);
		String software = String.join(" ", technique.software()).toLowerCase(Locale.ROOT);
		String procedures = technique
			.procedures()
			.stream()
			.map(p -> p.source() + " " + p.text())
			.reduce("", (a, b) -> a + " " + b)
			.toLowerCase(Locale.ROOT);

		if (id.equals(cleanQuery) || name.equals(cleanQuery)) {
			score += 100;
		}

		int tokensMatched = 0;
		for (String token : tokens) {
			boolean tokenFound = false;
			if (id.contains(token)) {
				score += 30;
				tokenFound = true;
			}
			if (name.contains(token)) {
				score += 30;
				tokenFound = true;
			}
			if (software.contains(token)) {
				score += 15;
				tokenFound = true;
			}
			if (procedures.contains(token)) {
				score += 8;
				tokenFound = true;
			}
			if (summary.contains(token)) {
				score += 8;
				tokenFound = true;
			}
			if (detection.contains(token)) {
				score += 3;
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
}
