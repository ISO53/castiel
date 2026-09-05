package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.DirectoryListing;
import io.github.iso53.castiel.model.FileContent;
import io.github.iso53.castiel.service.FileExplorerService;
import io.github.iso53.castiel.service.WorkspaceEventBus;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpHeaders;

import java.nio.file.Path;

/**
 * Exposes the local directory explorer used to pick a workspace folder.
 */
@RestController
@RequestMapping("/api/files")
public class FileExplorerController {

	private final FileExplorerService fileExplorerService;
	private final WorkspaceEventBus workspaceEvents;

	public FileExplorerController(FileExplorerService fileExplorerService, WorkspaceEventBus workspaceEvents) {
		this.fileExplorerService = fileExplorerService;
		this.workspaceEvents = workspaceEvents;
	}

	/**
	 * Lists a single directory's contents.
	 *
	 * @param path                 Absolute directory to list. When omitted, the user's desktop is returned.
	 * @param includeFiles         Whether to include file entries. {@code false} returns only directories.
	 * @param includeHiddenFolders Whether to include hidden entries. {@code false} hides OS-hidden items.
	 * @return The directory's metadata and its direct entries.
	 */
	@GetMapping
	public DirectoryListing list(
		@RequestParam(name = "path", required = false) String path,
		@RequestParam(name = "includeFiles", defaultValue = "true") boolean includeFiles,
		@RequestParam(name = "includeHiddenFolders", defaultValue = "false") boolean includeHiddenFolders
	) {
		try {
			return fileExplorerService.list(path, includeFiles, includeHiddenFolders);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/**
	 * Reads a text file's contents for the editor.
	 */
	@GetMapping("/content")
	public FileContent readFile(@RequestParam("path") String path) {
		try {
			return fileExplorerService.readFile(path);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/**
	 * Serves a file's raw bytes with a guessed content type (evidence gallery, lightbox).
	 */
	@GetMapping("/raw")
	public ResponseEntity<byte[]> readFileRaw(@RequestParam("path") String path) {
		try {
			FileExplorerService.RawFile file = fileExplorerService.readRaw(path);
			MediaType mediaType;
			try {
				mediaType = MediaType.parseMediaType(file.contentType());
			} catch (InvalidMediaTypeException ex) {
				mediaType = MediaType.APPLICATION_OCTET_STREAM;
			}
			return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.name() + "\"")
				.contentType(mediaType)
				.body(file.bytes());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/**
	 * Saves text content back to an existing file.
	 */
	@PutMapping("/content")
	public FileContent writeFile(@RequestBody WriteFileRequest request) {
		if (request == null || isBlank(request.path())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
		}
		try {
			FileContent content = fileExplorerService.writeFile(request.path(), request.content());
			workspaceEvents.publish(fileName(request.path()));
			return content;
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/**
	 * Creates an empty file or folder inside an existing directory.
	 */
	@PostMapping("/items")
	public DirectoryListing createItem(@RequestBody CreateItemRequest request) {
		if (request == null || isBlank(request.parentPath()) || isBlank(request.name())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parentPath and name are required");
		}
		try {
			return fileExplorerService.createItem(request.parentPath(), request.name(), request.directory());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/**
	 * Renames a file or folder in place.
	 */
	@PatchMapping("/items")
	public DirectoryListing renameItem(@RequestBody RenameItemRequest request) {
		if (request == null || isBlank(request.path()) || isBlank(request.name())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path and name are required");
		}
		try {
			return fileExplorerService.renameItem(request.path(), request.name());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/**
	 * Permanently deletes a file, or a folder with all of its contents.
	 */
	@DeleteMapping("/items")
	public DirectoryListing deleteItem(@RequestParam("path") String path) {
		if (isBlank(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
		}
		try {
			return fileExplorerService.deleteItem(path);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/** Best-effort file name for the change ping; "*" when it cannot be determined. */
	private static String fileName(String path) {
		try {
			Path file = Path.of(path);
			return file.getFileName() != null ? file.getFileName().toString() : "*";
		} catch (Exception ex) {
			return "*";
		}
	}

	/** Request body for {@link #writeFile}. */
	public record WriteFileRequest(String path, String content) {}

	/** Request body for {@link #createItem}. */
	public record CreateItemRequest(String parentPath, String name, boolean directory) {}

	/** Request body for {@link #renameItem}. */
	public record RenameItemRequest(String path, String name) {}
}
