package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.model.DirectoryListing;
import io.github.iso53.castiel.service.FileExplorerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the local directory explorer used to pick a workspace folder.
 */
@RestController
@RequestMapping("/api/files")
public class FileExplorerController {

    private final FileExplorerService fileExplorerService;

    public FileExplorerController(FileExplorerService fileExplorerService) {
        this.fileExplorerService = fileExplorerService;
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
}
