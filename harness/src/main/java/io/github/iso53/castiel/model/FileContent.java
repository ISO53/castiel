package io.github.iso53.castiel.model;

/**
 * A text file's contents for the workspace file editor.
 *
 * @param path    Absolute file path.
 * @param name    File name.
 * @param size    File size in bytes on disk.
 * @param content UTF-8 decoded file contents.
 */
public record FileContent(String path, String name, long size, String content) {}
