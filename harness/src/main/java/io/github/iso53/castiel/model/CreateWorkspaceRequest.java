package io.github.iso53.castiel.model;

/**
 * Request body for creating a new workspace folder under a parent directory.
 *
 * @param parentPath Absolute path of the parent directory.
 * @param name       Name of the folder to create inside {@code parentPath}.
 */
public record CreateWorkspaceRequest(String parentPath, String name) {
}
