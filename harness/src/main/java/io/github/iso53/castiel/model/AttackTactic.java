package io.github.iso53.castiel.model;

/**
 * One ATT&CK tactic (the kill-chain stage a technique serves), in canonical matrix order.
 *
 * @param name      Display name (e.g. {@code Lateral Movement}).
 * @param shortname Machine name used on technique records (e.g. {@code lateral-movement}).
 * @param order     1-based position in the Enterprise matrix.
 */
public record AttackTactic(String name, String shortname, int order) {}