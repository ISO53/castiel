package io.github.iso53.castiel.model;

/**
 * Result of checking an in-flight OAuth device sign-in. Settings and health are
 * only populated for {@link State#AUTHORIZED}.
 *
 * @param status   Current sign-in state.
 * @param email    Signed-in account email (authorized only).
 * @param settings Updated persisted settings (authorized only).
 * @param health   Health check of the connected provider (authorized only).
 * @param message  Optional human-readable detail, e.g. a transient error hint.
 */
public record ClineOAuthStatusResponse(
	State status,
	String email,
	UserSettings settings,
	HealthStatus health,
	String message
) {
	/** Device sign-in lifecycle states. */
	public enum State {
		/** No sign-in flow in progress. */
		IDLE,
		/** Waiting for the user to finish browser authorization. */
		PENDING,
		/** Sign-in completed and the provider entry was saved. */
		AUTHORIZED,
		/** Device code expired; the user must start over. */
		EXPIRED,
		/** The user denied the authorization request. */
		DENIED,
	}

	public ClineOAuthStatusResponse withHealth(HealthStatus value) {
		return new ClineOAuthStatusResponse(status, email, settings, value, message);
	}
}
