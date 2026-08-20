package io.github.iso53.castiel.service;

import io.github.iso53.castiel.model.WorkspaceState;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/**
 * Holds the live workspace session for the harness process.
 *
 * <p>Spring creates a single instance; inject this wherever tools need the current working
 * directory. The frontend mirrors the same state via the workspace REST API.
 */
@Service
public class WorkspaceSession {

	private volatile WorkspaceState state = new WorkspaceState(null);

	/**
	 * Returns the current workspace snapshot.
	 */
	public WorkspaceState get() {
		return state;
	}

	/**
	 * Sets the workspace to an existing directory.
	 *
	 * @param cwd Absolute path of an existing directory.
	 * @return The updated workspace snapshot.
	 * @throws IllegalArgumentException If the path is missing, blank, or not a directory.
	 */
	public synchronized WorkspaceState open(String cwd) {
		Path path = requireExistingDirectory(cwd);
		state = new WorkspaceState(path.toString());
		return state;
	}

	/**
	 * Creates a new directory under {@code parentPath} and sets it as the workspace.
	 *
	 * <p>Uses {@link Path#of(String, String...)} so the child path is built with the correct
	 * OS separator on Windows, macOS, and Linux.
	 *
	 * @param parentPath Absolute path of the parent directory.
	 * @param name       Folder name to create (must not contain path separators).
	 * @return The updated workspace snapshot.
	 * @throws IllegalArgumentException If validation fails or the folder already exists.
	 */
	public synchronized WorkspaceState create(String parentPath, String name) {
		Path parent = requireExistingDirectory(parentPath);
		String folderName = requireValidFolderName(name);
		Path target = parent.resolve(folderName).normalize();

		if (!target.startsWith(parent.normalize())) {
			throw new IllegalArgumentException("Invalid folder name: " + name);
		}
		if (Files.exists(target)) {
			throw new IllegalArgumentException(
				"A folder named \"" + folderName + "\" already exists in " + parent
			);
		}

		try {
			Files.createDirectory(target);
		} catch (FileAlreadyExistsException ex) {
			throw new IllegalArgumentException(
				"A folder named \"" + folderName + "\" already exists in " + parent,
				ex
			);
		} catch (IOException ex) {
			throw new IllegalArgumentException("Could not create workspace folder: " + target, ex);
		}

		state = new WorkspaceState(target.toAbsolutePath().normalize().toString());
		return state;
	}

	private Path requireExistingDirectory(String path) {
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("Directory path is required");
		}
		Path resolved;
		try {
			resolved = Path.of(path).toAbsolutePath().normalize();
		} catch (InvalidPathException ex) {
			throw new IllegalArgumentException("Invalid directory path: " + path, ex);
		}
		if (!Files.isDirectory(resolved)) {
			throw new IllegalArgumentException("Not a readable directory: " + resolved);
		}
		return resolved;
	}

	private String requireValidFolderName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Workspace name is required");
		}
		String trimmed = name.trim();
		if (trimmed.equals(".") || trimmed.equals("..")) {
			throw new IllegalArgumentException("Invalid folder name: " + trimmed);
		}
		if (trimmed.contains("/") || trimmed.contains("\\")) {
			throw new IllegalArgumentException("Folder name must not contain path separators");
		}
		// Reject characters illegal on Windows (and harmless to ban everywhere).
		if (trimmed.chars().anyMatch(ch -> "<>:\"|?*".indexOf(ch) >= 0 || ch < 32)) {
			throw new IllegalArgumentException("Folder name contains illegal characters: " + trimmed);
		}
		try {
			Path.of(trimmed);
		} catch (InvalidPathException ex) {
			throw new IllegalArgumentException("Invalid folder name: " + trimmed, ex);
		}
		return trimmed;
	}
}
