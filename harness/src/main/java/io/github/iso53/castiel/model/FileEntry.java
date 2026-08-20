package io.github.iso53.castiel.model;

/**
 * A single directory or file entry inside a directory listing.
 *
 * @param name      The entry's file or folder name.
 * @param path      Absolute path to the entry.
 * @param directory Whether this entry is a directory.
 */
public record FileEntry(String name, String path, boolean directory) {
}