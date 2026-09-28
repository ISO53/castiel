package io.github.iso53.castiel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iso53.castiel.model.KaliTool;
import io.github.iso53.castiel.model.KaliToolDto;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * In-memory index of Kali Linux tools extracted from official documentation.
 * Provides fast multi-word keyword search, category filtering, and local installation checks.
 */
@Service
public class KaliToolCatalog {

	private static final Logger log = LoggerFactory.getLogger(KaliToolCatalog.class);
	private static final String CATALOG_RESOURCE = "kali-tools.json";
	private static final Pattern WORD_PATTERN = Pattern.compile("[^a-zA-Z0-9_\\-]+");
	private static final ObjectMapper JSON = new ObjectMapper();

	private List<KaliTool> tools = List.of();
	private Map<String, KaliTool> toolsById = Map.of();
	private final Set<String> installedBinaries = ConcurrentHashMap.newKeySet();

	@PostConstruct
	public void initialize() {
		long start = System.currentTimeMillis();
		loadTools();
		refreshInstalled();
		log.info(
			"Loaded {} Kali tools and indexed in {} ms. Installed on host: {} binaries",
			tools.size(),
			System.currentTimeMillis() - start,
			installedBinaries.size()
		);
	}

	private void loadTools() {
		try {
			ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
			if (!resource.exists()) {
				log.warn("Kali tools resource {} not found; catalog is empty", CATALOG_RESOURCE);
				return;
			}
			try (InputStream is = resource.getInputStream()) {
				List<KaliTool> loaded = JSON.readValue(is, new TypeReference<List<KaliTool>>() {});
				this.tools = List.copyOf(loaded);
				Map<String, KaliTool> byId = new LinkedHashMap<>();
				for (KaliTool tool : this.tools) {
					byId.put(tool.id().toLowerCase(Locale.ROOT), tool);
				}
				this.toolsById = Map.copyOf(byId);
			}
		} catch (Exception ex) {
			log.error("Failed to load Kali tools catalog: {}", ex.getMessage(), ex);
		}
	}

	/**
	 * Scans the PATH directories on the host machine to discover which binaries are installed.
	 */
	public void refreshInstalled() {
		installedBinaries.clear();
		String pathEnv = System.getenv("PATH");
		if (pathEnv == null || pathEnv.isBlank()) {
			return;
		}
		for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
			try {
				Path p = Path.of(dir);
				if (Files.isDirectory(p)) {
					try (var stream = Files.list(p)) {
						stream
							.filter(Files::isExecutable)
							.map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
							.forEach(installedBinaries::add);
					}
				}
			} catch (Exception ignored) {
				// Directory not accessible or invalid path entry
			}
		}
	}

	public boolean isInstalled(KaliTool tool) {
		if (tool == null) {
			return false;
		}
		if (installedBinaries.contains(tool.id().toLowerCase(Locale.ROOT))) {
			return true;
		}
		for (String cmd : tool.commands()) {
			if (installedBinaries.contains(cmd.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	public Optional<KaliTool> findById(String id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(toolsById.get(id.toLowerCase(Locale.ROOT)));
	}

	public List<String> categories() {
		Set<String> cats = new TreeSet<>();
		for (KaliTool tool : tools) {
			cats.addAll(tool.categories());
		}
		return List.copyOf(cats);
	}

	public List<KaliToolDto> allTools() {
		return tools
			.stream()
			.map(tool -> tool.withInstalled(isInstalled(tool)))
			.toList();
	}

	/**
	 * Searches tools by keywords and filters with relevance scoring.
	 */
	public List<KaliToolDto> search(String query, String category, Boolean installedOnly, Integer limit) {
		int max = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
		String normalizedCategory =
			category != null && !category.isBlank() ? category.trim().toLowerCase(Locale.ROOT) : null;
		boolean filterInstalled = Boolean.TRUE.equals(installedOnly);

		if (query == null || query.isBlank()) {
			return tools
				.stream()
				.filter(
					tool ->
						normalizedCategory == null ||
						tool
							.categories()
							.stream()
							.anyMatch(c -> c.equalsIgnoreCase(normalizedCategory))
				)
				.filter(tool -> !filterInstalled || isInstalled(tool))
				.limit(max)
				.map(tool -> tool.withInstalled(isInstalled(tool)))
				.toList();
		}

		String cleanQuery = query.toLowerCase(Locale.ROOT).strip();
		String[] queryTokens = WORD_PATTERN.split(cleanQuery);
		List<String> tokens = Arrays.stream(queryTokens)
			.map(String::strip)
			.filter(s -> s.length() >= 2)
			.toList();

		if (tokens.isEmpty()) {
			return tools
				.stream()
				.filter(
					tool ->
						normalizedCategory == null ||
						tool
							.categories()
							.stream()
							.anyMatch(c -> c.equalsIgnoreCase(normalizedCategory))
				)
				.filter(tool -> !filterInstalled || isInstalled(tool))
				.limit(max)
				.map(tool -> tool.withInstalled(isInstalled(tool)))
				.toList();
		}

		// Calculate scores for matching tools
		record ScoredTool(KaliTool tool, int score) {}
		List<ScoredTool> scored = new ArrayList<>();

		for (KaliTool tool : tools) {
			if (
				normalizedCategory != null &&
				tool
					.categories()
					.stream()
					.noneMatch(c -> c.equalsIgnoreCase(normalizedCategory))
			) {
				continue;
			}
			boolean installed = isInstalled(tool);
			if (filterInstalled && !installed) {
				continue;
			}

			int score = calculateScore(tool, cleanQuery, tokens);
			if (score > 0) {
				scored.add(new ScoredTool(tool, score));
			}
		}

		scored.sort((a, b) -> Integer.compare(b.score(), a.score()));

		return scored
			.stream()
			.limit(max)
			.map(st -> st.tool().withInstalled(isInstalled(st.tool())))
			.toList();
	}

	private int calculateScore(KaliTool tool, String cleanQuery, List<String> tokens) {
		int score = 0;
		String id = tool.id().toLowerCase(Locale.ROOT);
		String name = tool.name().toLowerCase(Locale.ROOT);

		// Exact match bonus
		if (id.equals(cleanQuery) || name.equals(cleanQuery)) {
			score += 100;
		}

		int tokensMatched = 0;
		for (String token : tokens) {
			boolean tokenFound = false;

			if (id.contains(token) || name.contains(token)) {
				score += 30;
				tokenFound = true;
			}

			for (String cmd : tool.commands()) {
				String cmdLower = cmd.toLowerCase(Locale.ROOT);
				if (cmdLower.equals(token)) {
					score += 40;
					tokenFound = true;
				} else if (cmdLower.contains(token)) {
					score += 20;
					tokenFound = true;
				}
			}

			for (String cat : tool.categories()) {
				if (cat.toLowerCase(Locale.ROOT).contains(token)) {
					score += 15;
					tokenFound = true;
				}
			}

			if (tool.summary() != null && tool.summary().toLowerCase(Locale.ROOT).contains(token)) {
				score += 8;
				tokenFound = true;
			}

			if (tool.description() != null && tool.description().toLowerCase(Locale.ROOT).contains(token)) {
				score += 3;
				tokenFound = true;
			}

			if (tokenFound) {
				tokensMatched++;
			}
		}

		// Bonus if all query tokens were matched
		if (tokensMatched == tokens.size()) {
			score += 35;
		}

		return score;
	}
}
