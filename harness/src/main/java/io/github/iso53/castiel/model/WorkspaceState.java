package io.github.iso53.castiel.model;

/**
 * Immutable snapshot of the active workspace session.
 *
 * @param cwd Absolute path of the current working directory, or {@code null} when none is set.
 */
public record WorkspaceState(String cwd) {
}
