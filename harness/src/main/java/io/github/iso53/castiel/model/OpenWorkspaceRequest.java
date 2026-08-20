package io.github.iso53.castiel.model;

/**
 * Request body for selecting an existing directory as the workspace.
 *
 * @param cwd Absolute path of the directory to open.
 */
public record OpenWorkspaceRequest(String cwd) {
}
