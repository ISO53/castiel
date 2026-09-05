package io.github.iso53.castiel.service;

import io.github.iso53.castiel.model.DirectoryListing;
import io.github.iso53.castiel.model.FileContent;
import io.github.iso53.castiel.model.FileEntry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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

	/** Hard cap for opening files in the editor. */
	public static final int MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;
	private static final String MAX_SIZE_LABEL = "2 MB";

	/** Hard cap for serving raw files to the evidence gallery. */
	public static final int MAX_RAW_FILE_SIZE_BYTES = 50 * 1024 * 1024;
	private static final String MAX_RAW_SIZE_LABEL = "50 MB";

	/** A file served verbatim: raw bytes plus a guessed content type. */
	public record RawFile(String name, String contentType, long size, byte[] bytes) {}

	/**
	 * Reads a text file for the editor.
	 *
	 * @param path Absolute file path.
	 * @return The file's metadata and UTF-8 decoded contents.
	 * @throws IllegalArgumentException If the path is not a readable regular file, the file
	 *                                  exceeds {@value #MAX_FILE_SIZE_BYTES} bytes, or looks binary.
	 */
	public FileContent readFile(String path) {
		Path target = resolveFile(path);
		long size = fileSize(target);
		if (size > MAX_FILE_SIZE_BYTES) {
			throw new IllegalArgumentException(
				"File is too large to open (" + formatSize(size) + "); the limit is " + MAX_SIZE_LABEL + "."
			);
		}

		byte[] bytes;
		try {
			bytes = Files.readAllBytes(target);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not read file: " + target, ex);
		}
		if (looksBinary(bytes)) {
			throw new IllegalArgumentException("This looks like a binary file and cannot be opened in the editor.");
		}

		return new FileContent(target.toString(), nameOf(target), size, new String(bytes, StandardCharsets.UTF_8));
	}

	/**
	 * Writes UTF-8 text content back to an existing file. Only files that already
	 * exist are accepted — the editor edits, it does not create.
	 *
	 * @param path    Absolute file path.
	 * @param content New file contents.
	 * @return Metadata of the written file.
	 * @throws IllegalArgumentException If the path is not a writable regular file or writing fails.
	 */
	public FileContent writeFile(String path, String content) {
		Path target = resolveFile(path);
		try {
			Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not write file: " + target, ex);
		}
		return new FileContent(target.toString(), nameOf(target), fileSize(target), content == null ? "" : content);
	}

	/**
	 * Reads a file verbatim for endpoints that serve bytes (evidence gallery, lightbox).
	 *
	 * @param path Absolute file path.
	 * @return The file's name, guessed content type, and raw bytes.
	 * @throws IllegalArgumentException If the path is not a readable regular file or exceeds the cap.
	 */
	public RawFile readRaw(String path) {
		Path target = resolveFile(path);
		long size = fileSize(target);
		if (size > MAX_RAW_FILE_SIZE_BYTES) {
			throw new IllegalArgumentException(
				"File is too large to serve (" + formatSize(size) + "); the limit is " + MAX_RAW_SIZE_LABEL + "."
			);
		}

		byte[] bytes;
		try {
			bytes = Files.readAllBytes(target);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not read file: " + target, ex);
		}

		String contentType;
		try {
			contentType = Files.probeContentType(target);
		} catch (IOException ex) {
			contentType = null;
		}
		if (contentType == null || contentType.isBlank()) {
			contentType = "application/octet-stream";
		}

		return new RawFile(nameOf(target), contentType, size, bytes);
	}

	private Path resolveFile(String path) {
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("path is required");
		}
		Path target = Path.of(path).toAbsolutePath().normalize();
		if (!Files.isRegularFile(target)) {
			throw new IllegalArgumentException("Not a readable file: " + target);
		}
		return target;
	}

	/**
	 * Creates an empty file or folder inside an existing directory.
	 *
	 * @param parentPath Absolute directory to create the item in.
	 * @param name       Item name; must not contain path separators or already exist.
	 * @param directory  {@code true} creates a folder, otherwise an empty file.
	 * @return Listing of the parent directory after creation.
	 * @throws IllegalArgumentException If the parent is not a readable directory, the name is
	 *                                  invalid, or the target already exists.
	 */
	public DirectoryListing createItem(String parentPath, String name, boolean directory) {
		Path parent = resolveExistingDirectory(parentPath);
		Path target = validChild(parent, name);
		if (Files.exists(target)) {
			throw new IllegalArgumentException("\"" + name + "\" already exists.");
		}
		try {
			if (directory) {
				Files.createDirectories(target);
			} else {
				Files.createFile(target);
			}
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not create \"" + name + "\": " + ex.getMessage(), ex);
		}
		return list(parent.toString(), true, false);
	}

	/**
	 * Renames a file or folder in place.
	 *
	 * @param path Absolute path of the item to rename.
	 * @param name New name; must not contain path separators or collide with a sibling.
	 * @return Listing of the parent directory after the rename.
	 * @throws IllegalArgumentException If the item does not exist or the new name is invalid/taken.
	 */
	public DirectoryListing renameItem(String path, String name) {
		Path source = Path.of(path).toAbsolutePath().normalize();
		if (!Files.exists(source)) {
			throw new IllegalArgumentException("Not found: " + source);
		}
		Path parent = source.getParent();
		Path target = validChild(parent, name);
		if (Files.exists(target)) {
			throw new IllegalArgumentException("\"" + name + "\" already exists.");
		}
		try {
			Files.move(source, target);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not rename \"" + nameOf(source) + "\": " + ex.getMessage(), ex);
		}
		return list(parent.toString(), true, false);
	}

	/**
	 * Permanently deletes a file, or a folder together with all of its contents.
	 *
	 * @param path Absolute path of the item to delete.
	 * @return Listing of the parent directory after deletion.
	 * @throws IllegalArgumentException If the path does not exist or deletion fails.
	 */
	public DirectoryListing deleteItem(String path) {
		Path target = Path.of(path).toAbsolutePath().normalize();
		if (!Files.exists(target)) {
			throw new IllegalArgumentException("Not found: " + target);
		}
		try {
			if (Files.isDirectory(target)) {
				// Depth-first: children first, then the folder itself.
				try (Stream<Path> paths = Files.walk(target)) {
					paths.sorted(Comparator.reverseOrder()).forEach(child -> {
						try {
							Files.delete(child);
						} catch (IOException ex) {
							throw new IllegalStateException("Could not delete " + child, ex);
						}
					});
				} catch (IllegalStateException ex) {
					throw new IllegalArgumentException(ex.getMessage(), ex.getCause());
				}
			} else {
				Files.delete(target);
			}
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not delete \"" + nameOf(target) + "\": " + ex.getMessage(), ex);
		}

		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent)) {
			// Deleted a drive root's only child edge case — fall back to listing the drive.
			parent = target.getRoot();
		}
		return list(parent.toString(), true, false);
	}

	private Path resolveExistingDirectory(String parentPath) {
		Path parent = Path.of(parentPath).toAbsolutePath().normalize();
		if (!Files.isDirectory(parent)) {
			throw new IllegalArgumentException("Not a readable directory: " + parent);
		}
		return parent;
	}

	/**
	 * Validates a new child name against its future parent: non-blank, free of path
	 * separators, resolving strictly inside the parent.
	 */
	private Path validChild(Path parent, String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("A name is required.");
		}
		String trimmed = name.trim();
		if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains(":")) {
			throw new IllegalArgumentException("The name may not contain path separators.");
		}
		Path resolved = parent.resolve(trimmed).normalize();
		if (!resolved.startsWith(parent)) {
			throw new IllegalArgumentException("The name resolves outside the folder.");
		}
		return resolved;
	}

	private long fileSize(Path target) {
		try {
			return Files.size(target);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not stat file: " + target, ex);
		}
	}

	private static String nameOf(Path target) {
		return target.getFileName().toString();
	}

	private static String formatSize(long bytes) {
		if (bytes >= 1_000_000) {
			return "%.1f MB".formatted(bytes / 1_000_000.0);
		}
		return bytes / 1000 + " kB";
	}

	/**
	 * Cheap binary sniff: a NUL byte in the first 8 kB almost never occurs in text.
	 */
	private static boolean looksBinary(byte[] bytes) {
		int limit = Math.min(bytes.length, 8192);
		for (int index = 0; index < limit; index++) {
			if (bytes[index] == 0) {
				return true;
			}
		}
		return false;
	}
}
