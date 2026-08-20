package io.github.iso53.castiel.service;

import io.github.iso53.castiel.model.DirectoryListing;
import io.github.iso53.castiel.model.FileEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Resolves and lists local directories for the workspace file explorer.
 *
 * <p>Only the direct children of a single requested directory are returned at a time;
 * the full filesystem is never traversed.
 */
@Service
public class FileExplorerService {

	/**
	 * Lists the contents of a directory.
	 *
	 * @param path                 Absolute directory path to list, or {@code null}/{@code blank} to start
	 *                             from the user's desktop directory.
	 * @param includeFiles         Whether to include file entries ({@code false} returns only directories).
	 * @param includeHiddenFolders Whether to include hidden entries. Hidden means a
	 *                             dot-prefixed name, or the OS hidden attribute (e.g. DOS Hidden
	 *                             on Windows).
	 * @return Metadata for the listed directory together with its entries.
	 * @throws IllegalArgumentException If the path is not a readable directory or listing fails.
	 */
	public DirectoryListing list(String path, boolean includeFiles, boolean includeHiddenFolders) {
		Path target = resolve(path);
		if (!Files.isDirectory(target)) {
			throw new IllegalArgumentException("Not a readable directory: " + target);
		}

		List<FileEntry> entries = new ArrayList<>();
		try (Stream<Path> children = Files.list(target)) {
			children
				.filter(child -> includeFiles || Files.isDirectory(child))
				.filter(child -> includeHiddenFolders || !isHidden(child))
				.sorted(
					Comparator.comparing((Path child) -> Files.isDirectory(child))
						.reversed()
						.thenComparing(child -> child.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
				)
				.forEach(child ->
					entries.add(
						new FileEntry(
							child.getFileName().toString(),
							child.toAbsolutePath().normalize().toString(),
							Files.isDirectory(child)
						)
					)
				);
		} catch (IOException | UnsupportedOperationException ex) {
			throw new IllegalArgumentException("Could not list directory: " + target, ex);
		}

		Path normalized = target.toAbsolutePath().normalize();
		Path parent = normalized.getParent();
		return new DirectoryListing(
			normalized.toString(),
			normalized.getFileName().toString(),
			parent != null ? parent.toString() : null,
			entries
		);
	}

	/**
	 * Resolves the requested path, defaulting to the user's desktop when none is given.
	 */
	private Path resolve(String path) {
		if (path == null || path.isBlank()) {
			return defaultStartingDirectory();
		}
		return Path.of(path).toAbsolutePath().normalize();
	}

	/**
	 * The default starting position for the explorer: the user's Desktop directory.
	 *
	 * <p>Built from {@code user.home} via {@link Path#of(String, String...)} so the correct
	 * OS-specific separator, file layout, and desktop location are used on both Windows and
	 * Linux. Falls back to the home directory when no Desktop exists.
	 */
	private Path defaultStartingDirectory() {
		String home = System.getProperty("user.home");
		Path desktop = Path.of(home, "Desktop");
		if (Files.isDirectory(desktop)) {
			return desktop;
		}
		return Path.of(home);
	}

	/**
	 * Returns whether a path is considered hidden.
	 *
	 * <p>Treats names that start with {@code '.'} as hidden on every platform (Unix convention),
	 * and also checks {@link Files#isHidden(Path)} for the OS hidden attribute (required on
	 * Windows, where dot-prefixed names are not hidden by default). If the OS check throws an
	 * {@link IOException}, only the name-based rule is used.
	 */
	private boolean isHidden(Path path) {
		Path fileName = path.getFileName();
		if (fileName != null && fileName.toString().startsWith(".")) {
			return true;
		}
		try {
			return Files.isHidden(path);
		} catch (IOException ex) {
			return false;
		}
	}
}
