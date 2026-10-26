package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.DirectoryListing;
import io.github.iso53.castiel.model.FileContent;
import io.github.iso53.castiel.service.FileExplorerService;
import io.github.iso53.castiel.service.WorkspaceEventBus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
	 * Serves a file's raw bytes (evidence gallery, lightbox).
	 *
	 * <p>Workspace content can originate from the engaged target (fetched pages,
	 * downloaded PoCs), so only media types the browser renders without executing
	 * script are served inline. Everything else — HTML pages above all — is forced
	 * into an opaque download: {@code application/octet-stream} plus
	 * {@code Content-Disposition: attachment} and a sandboxing CSP, so a crafted
	 * file can never execute script on this origin when previewed. The filename is
	 * sanitized and RFC 5987 encoded, so a crafted name cannot smuggle header
	 * syntax (quotes, CR/LF) either.
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
			boolean inline = isInlineSafe(mediaType);
			return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition(file.name(), inline))
				.header("Content-Security-Policy", "sandbox")
				.header("X-Content-Type-Options", "nosniff")
				.contentType(inline ? mediaType : MediaType.APPLICATION_OCTET_STREAM)
				.body(file.bytes());
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
	}

	/** Media types the browser renders without executing scripts in this origin. */
	private static boolean isInlineSafe(MediaType mediaType) {
		String type = mediaType.getType();
		return (
			type.equals("image") ||
			type.equals("video") ||
			type.equals("audio") ||
			(type.equals("application") && mediaType.getSubtype().equals("pdf"))
		);
	}

	/**
	 * Builds the Content-Disposition value: inline for safe media, download for
	 * everything else. The fallback name keeps printable ASCII only and drops
	 * quoting characters; the RFC 5987 name is percent-encoded UTF-8.
	 */
	private static String disposition(String name, boolean inline) {
		String fallback = name.replaceAll("[^\\x20-\\x7e]", "_").replace("\"", "_").replace("\\", "_");
		String encoded = URLEncoder.encode(fallback, StandardCharsets.UTF_8).replace("+", "%20");
		return (inline ? "inline" : "attachment") + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
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
