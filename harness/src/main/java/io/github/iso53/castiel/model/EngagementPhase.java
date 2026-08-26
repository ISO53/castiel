package io.github.iso53.castiel.model;

/**
 * Snapshot of the current pentest phase, returned by the engagement API and the
 * {@code workspace_info} tool.
 *
 * @param phase       Current phase number, 1-based.
 * @param name        Human-readable phase name.
 * @param summary     One-line description of what the phase allows.
 * @param totalPhases Total number of phases in an engagement.
 */
public record EngagementPhase(int phase, String name, String summary, int totalPhases) {
}