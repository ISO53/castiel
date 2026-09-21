package io.github.iso53.castiel.model;

/**
 * OAuth-backed credentials for a provider account (e.g. Cline sign-in).
 *
 * @param accessToken  Short-lived access token; the provider derives its API key from this.
 * @param refreshToken Long-lived token used to obtain new access tokens.
 * @param expiresAt    Access token expiry in epoch milliseconds, or {@code null} when unknown.
 * @param email        Signed-in account email, for display.
 * @param accountId    Provider-side account id, or {@code null}.
 */
public record ProviderOAuthCredentials(
	String accessToken,
	String refreshToken,
	Long expiresAt,
	String email,
	String accountId
) {

	public boolean isBlank() {
		return accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank();
	}
}