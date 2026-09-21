package io.github.iso53.castiel.model;

/**
 * Payload for a freshly started OAuth device sign-in.
 *
 * @param userCode        Code the user types into the browser.
 * @param verificationUrl URL the user must open to authorize the device.
 * @param expiresAt       Epoch milliseconds when the device code expires.
 */
public record ClineOAuthStartResponse(String userCode, String verificationUrl, Long expiresAt) {}
