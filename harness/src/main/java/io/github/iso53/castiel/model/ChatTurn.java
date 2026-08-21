package io.github.iso53.castiel.model;

/**
 * One turn in a chat thread sent from the frontend.
 *
 * @param role    {@code user} or {@code assistant}.
 * @param content Message text.
 */
public record ChatTurn(String role, String content) {
}
