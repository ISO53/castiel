package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.WorkspaceSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Workspace file search for the model.
 *
 * <p>Returns a compact, deterministic text summary that the model can reason about.
 * Multiple queries may be given (comma-separated); each is matched against file/folder
 * names and text file contents.
 *
 * <p>Built for speed on Java 25:
 * <ul>
 *   <li>{@link Files#walkFileTree} with subtree pruning skips junk directories.</li>
 *   <li>Content scanning uses virtual threads gated by a CPU-sized semaphore so parallel
 *       disk reads never thrash the pool.</li>
 * </ul>
 */
@Service
public class WorkspaceSearchTool implements ToolProvider {

	private static final int MAX_RESULTS_PER_QUERY = 20;
	private static final long MAX_CONTENT_BYTES = 1_000_000;
	private static final long TIME_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(5);
	private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
		".git",
		".idea",
		".next",
		".vs",
		".vscode",
		"__pycache__",
		".venv",
		"venv",
		"node_modules",
		"target",
		"dist",
		"build",
		"out"
	);

	private final WorkspaceSession workspace;

	public WorkspaceSearchTool(WorkspaceSession workspace) {
		this.workspace = workspace;
	}

	private record CompiledQuery(String label, Pattern pattern) {}

	@Tool(
		name = "workspace_search",
		value = {
			"Searches the open workspace for files, folders, and text content.",
			"Provide one or more queries separated by commas. Each query can be a literal string or a regex.",
			"Set regex=true to treat a query as a regular expression.",
			"Matching is case-insensitive unless caseSensitive is true.",
			"Also matches file and folder names, not just file contents.",
		}
	)
	public String search(
		@P("Comma-separated query strings to search for") String queries,
		@P("Whether the queries are regular expressions; default false") Boolean regex,
		@P("Whether matching is case-sensitive; default false") Boolean caseSensitive
	) {
		Path root = workspace.root().orElseThrow(() -> new IllegalArgumentException("No workspace is open"));
		List<CompiledQuery> compiled = compile(queries, Boolean.TRUE.equals(regex), Boolean.TRUE.equals(caseSensitive));

		long deadline = System.nanoTime() + TIME_BUDGET_NANOS;

		List<List<String>> matches = new ArrayList<>(compiled.size());
		for (int i = 0; i < compiled.size(); i++) {
			matches.add(new ArrayList<>());
		}
		AtomicInteger[] counts = new AtomicInteger[compiled.size()];
		for (int i = 0; i < counts.length; i++) {
			counts[i] = new AtomicInteger();
		}
		AtomicInteger filesScanned = new AtomicInteger();

		List<Path> folders = new ArrayList<>();
		List<Path> files = new ArrayList<>();
		try {
			Files.walkFileTree(
				root,
				new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
						if (System.nanoTime() > deadline) {
							return FileVisitResult.SKIP_SUBTREE;
						}
						if (
							!dir.equals(root) &&
							SKIPPED_DIRECTORIES.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))
						) {
							return FileVisitResult.SKIP_SUBTREE;
						}
						folders.add(dir);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
						if (System.nanoTime() > deadline) {
							return FileVisitResult.SKIP_SIBLINGS;
						}
						files.add(file);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) {
						return FileVisitResult.CONTINUE;
					}
				}
			);
		} catch (IOException ex) {
			return "Error: workspace traversal failed: " + ex.getMessage();
		}

		matchNames(compiled, folders, "[folder]", matches, counts);
		matchNames(compiled, files, "[file]", matches, counts);

		Semaphore gate = new Semaphore(Math.max(4, Runtime.getRuntime().availableProcessors()));
		try (ExecutorService scanner = Executors.newVirtualThreadPerTaskExecutor()) {
			for (Path file : files) {
				gate.acquireUninterruptibly();
				scanner.submit(() -> {
					try {
						scanFile(file, compiled, matches, counts, filesScanned, deadline);
					} finally {
						gate.release();
					}
				});
			}
		}

		return formatResults(compiled, matches, filesScanned.get());
	}

	private List<CompiledQuery> compile(String queries, boolean regex, boolean caseSensitive) {
		int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		List<CompiledQuery> compiled = new ArrayList<>();
		for (String raw : queries.split(",")) {
			String text = raw.strip();
			if (text.isEmpty()) {
				continue;
			}
			Pattern pattern;
			try {
				pattern = regex ? Pattern.compile(text, flags) : Pattern.compile(Pattern.quote(text), flags);
			} catch (RuntimeException ex) {
				return List.of(new CompiledQuery("(invalid regex: " + text + ")", Pattern.compile(".\\A")));
			}
			compiled.add(new CompiledQuery(text, pattern));
		}
		if (compiled.isEmpty()) {
			compiled.add(new CompiledQuery("", Pattern.compile("$.\\A")));
		}
		return compiled;
	}

	private void matchNames(
		List<CompiledQuery> compiled,
		List<Path> paths,
		String kind,
		List<List<String>> results,
		AtomicInteger[] counts
	) {
		for (int queryIndex = 0; queryIndex < compiled.size(); queryIndex++) {
			if (counts[queryIndex].get() >= MAX_RESULTS_PER_QUERY) {
				continue;
			}
			for (Path path : paths) {
				String name = path.getFileName().toString();
				Matcher matcher = compiled.get(queryIndex).pattern().matcher(name);
				if (matcher.find()) {
					addResult(results, queryIndex, kind + " " + relative(path));
				}
			}
		}
	}

	private void addResult(List<List<String>> results, int index, String line) {
		List<String> list = results.get(index);
		if (list.size() < MAX_RESULTS_PER_QUERY) {
			list.add(line);
		}
	}

	private void scanFile(
		Path file,
		List<CompiledQuery> compiled,
		List<List<String>> results,
		AtomicInteger[] counts,
		AtomicInteger filesScanned,
		long deadline
	) {
		if (System.nanoTime() > deadline) {
			return;
		}
		if (!Files.isReadable(file)) {
			return;
		}
		try {
			long size = Files.size(file);
			if (size > MAX_CONTENT_BYTES) {
				return;
			}
		} catch (IOException _) {
			return;
		}

		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
		} catch (IOException _) {
			return;
		}
		if (looksBinary(bytes)) {
			return;
		}
		if (bytes.length == 0) {
			return;
		}

		String content;
		try {
			content = new String(bytes, StandardCharsets.UTF_8);
		} catch (RuntimeException e) {
			return;
		}
		filesScanned.incrementAndGet();

		String[] lines = content.split("\n", -1);
		for (int queryIndex = 0; queryIndex < compiled.size(); queryIndex++) {
			if (counts[queryIndex].get() >= MAX_RESULTS_PER_QUERY) {
				continue;
			}
			int lineNumber = 1;
			for (String line : lines) {
				if (counts[queryIndex].get() >= MAX_RESULTS_PER_QUERY) {
					break;
				}
				if (line.endsWith("\r")) {
					line = line.substring(0, line.length() - 1);
				}
				Matcher matcher = compiled.get(queryIndex).pattern().matcher(line);
				if (matcher.find()) {
					addResult(results, queryIndex, relative(file) + ":" + lineNumber);
					counts[queryIndex].incrementAndGet();
				}
				lineNumber++;
			}
		}
	}

	private String relative(Path path) {
		Path root = workspace.root().orElseThrow();
		String separator = System.getProperty("file.separator", "/");
		return root.relativize(path).toString().replace(separator, "/");
	}

	private static boolean looksBinary(byte[] bytes) {
		int limit = Math.min(bytes.length, 8192);
		for (int i = 0; i < limit; i++) {
			if (bytes[i] == 0) {
				return true;
			}
		}
		return false;
	}

	private static String formatResults(List<CompiledQuery> compiled, List<List<String>> results, int filesScanned) {
		StringBuilder out = new StringBuilder();
		out.append("Workspace search - ").append(filesScanned).append(" files scanned.\n\n");
		boolean anyHit = false;
		for (int i = 0; i < compiled.size(); i++) {
			List<String> list = results.get(i);
			out.append("=== ").append(compiled.get(i).label()).append(" ===\n");
			if (list.isEmpty()) {
				out.append("no matches\n");
			} else {
				anyHit = true;
				for (String line : list) {
					out.append(line).append("\n");
				}
				if (list.size() >= MAX_RESULTS_PER_QUERY) {
					out.append("[results truncated at ").append(MAX_RESULTS_PER_QUERY).append("]\n");
				}
			}
			out.append("\n");
		}
		if (!anyHit) {
			out.append("No matches found in the workspace.\n");
		}
		return out.toString().strip();
	}
}
