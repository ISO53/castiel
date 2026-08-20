package io.github.iso53.castiel.model;

import java.util.List;

/**
 * The contents of a single directory.
 *
 * @param path       Absolute path of the listed directory.
 * @param name       Name of the listed directory.
 * @param parentPath Absolute path of the parent directory, or {@code null} at a filesystem root.
 * @param entries    Directories and (optionally) files directly inside the listed directory.
 */
public record DirectoryListing(String path, String name, String parentPath, List<FileEntry> entries) {
}