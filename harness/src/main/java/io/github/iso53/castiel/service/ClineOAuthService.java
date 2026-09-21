package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iso53.castiel.model.ClineOAuthStartResponse;
import io.github.iso53.castiel.model.ClineOAuthStatusResponse;
import io.github.iso53.castiel.model.LlmProviderConfig;
import io.github.iso53.castiel.model.ProviderOAuthCredentials;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cline account sign-in via the WorkOS device authorization flow, plus token
 * refresh for the persisted Cline provider entry. Mirrors the flow in Cline's
 * own CLI: device code → browser approval → WorkOS token poll → registration
 * with Cline's backend, which mints the session tokens used as the API key.
 */
@Service
public class ClineOAuthService {

	private static final Logger log = LoggerFactory.getLogger(ClineOAuthService.class);

	private static final String WORKOS_CLIENT_ID = "client_01K3A541FN8TA3EPPHTD2325AR";
	private static final String WORKOS_DEVICE_AUTH_URL = "https://api.workos.com/user_management/authorize/device";
	private static final String WORKOS_TOKEN_URL = "https://api.workos.com/user_management/authenticate";
	// Cline prefixes its session token when exposing it as an API key.
	private static final String WORKOS_TOKEN_PREFIX = "workos:";

	private static final String CLINE_PROVIDER_ID = "cline";
	private static final String CLINE_API_URL = "https://api.cline.bot/api/v1";
	private static final String CLINE_REGISTER_PATH = "https://api.cline.bot/api/v1/auth/register";
	private static final String CLINE_REFRESH_PATH = "https://api.cline.bot/api/v1/auth/refresh";

	// Refresh access tokens this long before they expire (mirrors Cline CLI).
	private static final long REFRESH_BUFFER_MS = 5 * 60 * 1000;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
	private static final ObjectMapper JSON = new ObjectMapper();

	// Single-user local app: at most one device sign-in in flight.
	private record PendingLogin(
		String deviceCode,
		String userCode,
		String verificationUrl,
		long expiresAt,
		long intervalMs,
		long nextPollAt
	) {
		PendingLogin withNextPoll(long value) {
			return new PendingLogin(deviceCode, userCode, verificationUrl, expiresAt, intervalMs, value);
		}

		// RFC 8628 slow_down: the poll interval grows by one second.
		PendingLogin withSlowerInterval() {
			long next = intervalMs + 1000;
			return new PendingLogin(
				deviceCode,
				userCode,
				verificationUrl,
				expiresAt,
				next,
				System.currentTimeMillis() + next
			);
		}
	}

	private volatile PendingLogin pendingLogin;

	private final UserSettingsService userSettingsService;

	public ClineOAuthService(UserSettingsService userSettingsService) {
		this.userSettingsService = userSettingsService;
	}

	/**
	 * Starts a new device sign-in: registers the device with WorkOS and stores the
	 * pending flow. Any previous in-flight flow is replaced.
	 */
	public synchronized ClineOAuthStartResponse start() {
		try {
			String form = form(Map.of("client_id", WORKOS_CLIENT_ID));
			HttpResponse<String> response = post(WORKOS_DEVICE_AUTH_URL, form, "application/x-www-form-urlencoded");
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Device authorization failed with HTTP " + response.statusCode());
			}
			JsonNode json = parse(response.body());
			String deviceCode = json.path("device_code").asText("");
			String userCode = json.path("user_code").asText("");
			String verificationUrl = json.path("verification_uri_complete").asText("");
			if (verificationUrl.isEmpty()) {
				verificationUrl = json.path("verification_uri").asText("");
			}
			if (deviceCode.isEmpty() || userCode.isEmpty() || verificationUrl.isEmpty()) {
				throw new IllegalStateException("Incomplete device authorization response");
			}
			long now = System.currentTimeMillis();
			long expiresAt = now + json.path("expires_in").asInt(300) * 1000L;
			long intervalMs = Math.max(1, json.path("interval").asInt(5)) * 1000L;
			pendingLogin = new PendingLogin(deviceCode, userCode, verificationUrl, expiresAt, intervalMs, now);
			return new ClineOAuthStartResponse(userCode, verificationUrl, expiresAt);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not start Cline sign-in: " + ex.getMessage(), ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while starting Cline sign-in", ex);
		}
	}

	/**
	 * Advances the pending sign-in by at most one WorkOS token poll (rate-limited by
	 * the flow's poll interval) and returns the resulting state.
	 */
	public synchronized ClineOAuthStatusResponse poll() {
		PendingLogin pending = this.pendingLogin;
		if (pending == null) {
			return new ClineOAuthStatusResponse(ClineOAuthStatusResponse.State.IDLE, null, null, null, null);
		}
		long now = System.currentTimeMillis();
		if (now >= pending.expiresAt()) {
			pendingLogin = null;
			return new ClineOAuthStatusResponse(ClineOAuthStatusResponse.State.EXPIRED, null, null, null, null);
		}
		if (now < pending.nextPollAt()) {
			return new ClineOAuthStatusResponse(ClineOAuthStatusResponse.State.PENDING, null, null, null, null);
		}
		try {
			String form = form(
				Map.of(
					"grant_type",
					"urn:ietf:params:oauth:grant-type:device_code",
					"device_code",
					pending.deviceCode(),
					"client_id",
					WORKOS_CLIENT_ID
				)
			);
			HttpResponse<String> response = post(WORKOS_TOKEN_URL, form, "application/x-www-form-urlencoded");
			JsonNode json = parse(response.body());
			String error = json.path("error").asText("");
			if (error.isEmpty()) {
				String accessToken = json.path("access_token").asText("");
				String refreshToken = json.path("refresh_token").asText("");
				if (accessToken.isEmpty() || refreshToken.isEmpty()) {
					return pending("Incomplete authorization response");
				}
				return register(accessToken, refreshToken);
			}
			return switch (error) {
				case "authorization_pending" -> {
					pendingLogin = pending.withNextPoll(now + pending.intervalMs());
					yield pending(null);
				}
				case "slow_down" -> {
					pendingLogin = pending.withSlowerInterval();
					yield pending(null);
				}
				case "expired_token" -> {
					pendingLogin = null;
					yield new ClineOAuthStatusResponse(ClineOAuthStatusResponse.State.EXPIRED, null, null, null, null);
				}
				case "access_denied" -> {
					pendingLogin = null;
					yield new ClineOAuthStatusResponse(ClineOAuthStatusResponse.State.DENIED, null, null, null, null);
				}
				default -> pending(json.path("error_description").asText(error));
			};
		} catch (IOException | InterruptedException ex) {
			// Transient network issue: keep waiting, the device code is still valid.
			log.warn("Cline sign-in poll failed; will retry", ex);
			return pending("Network issue while checking authorization; still trying");
		}
	}

	// Exchanges the approved WorkOS tokens for Cline session credentials and saves them.
	private ClineOAuthStatusResponse register(String workosAccessToken, String workosRefreshToken) {
		try {
			String body = JSON.writeValueAsString(
				Map.of("accessToken", workosAccessToken, "refreshToken", workosRefreshToken)
			);
			HttpResponse<String> response = post(CLINE_REGISTER_PATH, body, "application/json");
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return pending("Token registration failed with HTTP " + response.statusCode());
			}
			JsonNode data = parse(response.body()).path("data");
			String accessToken = data.path("accessToken").asText("");
			String refreshToken = data.path("refreshToken").asText("");
			if (accessToken.isEmpty() || refreshToken.isEmpty()) {
				return pending("Token registration returned no session tokens");
			}
			JsonNode userInfo = data.path("userInfo");
			ProviderOAuthCredentials credentials = new ProviderOAuthCredentials(
				accessToken,
				refreshToken,
				parseExpiresAt(data.path("expiresAt").asText(null)),
				userInfo.path("email").asText(null),
				userInfo.path("clineUserId").asText(null)
			);
			upsertClineConfig(credentials);
			pendingLogin = null;
			return new ClineOAuthStatusResponse(
				ClineOAuthStatusResponse.State.AUTHORIZED,
				credentials.email(),
				userSettingsService.get(),
				null,
				null
			);
		} catch (IOException | InterruptedException ex) {
			log.warn("Cline token registration failed; will retry", ex);
			return pending("Token registration failed; still trying");
		}
	}

	// Saves the signed-in credentials under the Cline provider entry (marking it active, like a connect).
	private void upsertClineConfig(ProviderOAuthCredentials credentials) {
		LlmProviderConfig existing = userSettingsService.get().providers().get(CLINE_PROVIDER_ID);
		userSettingsService.upsertProvider(CLINE_PROVIDER_ID, withAuth(baseConfig(existing), credentials));
	}

	// Builds the Cline provider config from an existing entry (or defaults) without credentials.
	private LlmProviderConfig baseConfig(LlmProviderConfig existing) {
		return new LlmProviderConfig(
			existing != null ? existing.type() : null,
			existing != null ? existing.apiUrl() : CLINE_API_URL,
			"",
			existing != null ? existing.contextWindow() : null,
			null
		);
	}

	private LlmProviderConfig withAuth(LlmProviderConfig config, ProviderOAuthCredentials credentials) {
		return new LlmProviderConfig(
			config.type(),
			config.apiUrl(),
			WORKOS_TOKEN_PREFIX + credentials.accessToken(),
			config.contextWindow(),
			credentials
		);
	}

	// Replaces the credentials with nothing: the OAuth key is dead, the user must sign in again.
	private LlmProviderConfig clearAuth(LlmProviderConfig config) {
		LlmProviderConfig next = new LlmProviderConfig(
			config.type(),
			config.apiUrl(),
			"",
			config.contextWindow(),
			null
		);
		String providerId = findProviderId(config);
		if (providerId != null) {
			userSettingsService.replaceProvider(providerId, next);
		}
		return next;
	}

	// Locates the persisted provider id a config object belongs to (configs are passed by reference).
	private String findProviderId(LlmProviderConfig config) {
		return userSettingsService
			.get()
			.providers()
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue() == config)
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}

	private ClineOAuthStatusResponse pending(String message) {
		return new ClineOAuthStatusResponse(ClineOAuthStatusResponse.State.PENDING, null, null, null, message);
	}

	// Rejected refresh: 400/401/403 with an invalid/expired/revoked/unauthorized marker.
	private boolean isInvalidGrant(int status, String body) {
		if (status != 400 && status != 401 && status != 403) {
			return false;
		}
		String normalized = body == null ? "" : body.toLowerCase();
		return (
			normalized.contains("invalid") ||
			normalized.contains("expired") ||
			normalized.contains("revoked") ||
			normalized.contains("unauthorized")
		);
	}

	// Parses Cline's ISO-8601 expiry timestamp into epoch milliseconds, or null when unparseable.
	private static Long parseExpiresAt(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(value).toEpochMilli();
		} catch (Exception ignored) {
			// Fall through to the offset form.
		}
		try {
			return OffsetDateTime.parse(value).toInstant().toEpochMilli();
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Refreshes the OAuth credentials embedded in {@code config} when they are near
	 * expiry, persisting the rotated tokens. Returns the (possibly updated) config;
	 * on transient failure the original config is returned so callers keep working
	 * with the current token.
	 */
	public synchronized LlmProviderConfig refreshIfExpiring(LlmProviderConfig config) {
		ProviderOAuthCredentials auth = config.auth();
		if (auth == null || auth.isBlank() || auth.expiresAt() == null) {
			return config;
		}
		if (auth.expiresAt() - System.currentTimeMillis() > REFRESH_BUFFER_MS) {
			return config;
		}
		try {
			String body = JSON.writeValueAsString(
				Map.of("refreshToken", auth.refreshToken(), "grantType", "refresh_token")
			);
			HttpResponse<String> response = post(CLINE_REFRESH_PATH, body, "application/json");
			if (response.statusCode() >= 300) {
				if (isInvalidGrant(response.statusCode(), response.body())) {
					log.warn("Cline refresh token rejected; clearing sign-in state");
					return clearAuth(config);
				}
				log.warn("Cline token refresh failed with HTTP {}; keeping current token", response.statusCode());
				return config;
			}
			JsonNode data = parse(response.body()).path("data");
			String accessToken = data.path("accessToken").asText("");
			String refreshToken = data.path("refreshToken").asText("");
			if (accessToken.isEmpty() || refreshToken.isEmpty()) {
				log.warn("Cline token refresh returned no tokens; keeping current credentials");
				return config;
			}
			JsonNode userInfo = data.path("userInfo");
			ProviderOAuthCredentials updated = new ProviderOAuthCredentials(
				accessToken,
				refreshToken,
				parseExpiresAt(data.path("expiresAt").asText(null)),
				userInfo.path("email").asText(auth.email()),
				userInfo.path("clineUserId").asText(auth.accountId())
			);
			LlmProviderConfig next = withAuth(config, updated);
			String providerId = findProviderId(config);
			if (providerId != null) {
				// Refreshes must not steal the UI default provider.
				userSettingsService.replaceProvider(providerId, next);
			}
			return next;
		} catch (IOException | InterruptedException ex) {
			log.warn("Cline token refresh failed; keeping current token", ex);
			return config;
		}
	}

	private HttpResponse<String> post(String url, String body, String contentType)
		throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(REQUEST_TIMEOUT)
			.header("Content-Type", contentType)
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();
		return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static String form(Map<String, String> values) {
		StringBuilder result = new StringBuilder();
		values.forEach((key, value) -> {
			if (!result.isEmpty()) {
				result.append('&');
			}
			result.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
			result.append('=');
			result.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
		});
		return result.toString();
	}

	private static JsonNode parse(String body) throws IOException {
		return JSON.readTree(body == null ? "{}" : body);
	}
}
