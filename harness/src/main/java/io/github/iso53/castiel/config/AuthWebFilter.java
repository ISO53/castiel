package io.github.iso53.castiel.config;

import io.github.iso53.castiel.service.AuthService;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Opt-in API gate for {@code --auth}: every {@code /api/**} request must carry a valid
 * session cookie (browser flow) or the bearer token (scripting). Static UI assets stay
 * open; they hold no secrets and the login screen has to be reachable. When auth is
 * disabled the filter is a pass-through, so local runs behave exactly as before.
 */
public class AuthWebFilter implements WebFilter {

	private static final Logger log = LoggerFactory.getLogger(AuthWebFilter.class);

	/** Session cookie; HttpOnly + SameSite=Strict, set by the login endpoint. */
	public static final String SESSION_COOKIE = "castiel_session";

	private static final Set<HttpMethod> MUTATING = Set.of(
		HttpMethod.POST,
		HttpMethod.PUT,
		HttpMethod.DELETE,
		HttpMethod.PATCH
	);

	// Hosts accepted by the cross-origin check beyond the request's own Host header;
	// local development through the Vite proxy always counts as same-origin.
	private static final Set<String> LOOPBACK_HOSTS = Set.of(
		"localhost",
		"127.0.0.1",
		"::1",
		"[::1]",
		"0:0:0:0:0:0:0:1"
	);

	private final AuthService auth;

	public AuthWebFilter(AuthService auth) {
		this.auth = auth;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		if (!auth.enabled()) {
			return chain.filter(exchange);
		}
		ServerHttpRequest request = exchange.getRequest();
		String path = request.getPath().value();
		if (!path.startsWith("/api/")) {
			return chain.filter(exchange);
		}
		if (request.getMethod() == HttpMethod.OPTIONS) {
			return chain.filter(exchange);
		}
		// Authenticated API payloads must never land in caches.
		exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
		if (isPublic(path)) {
			return chain.filter(exchange);
		}

		String sessionId = sessionIdOf(request);
		if (sessionId != null && auth.validateSession(sessionId)) {
			// SameSite=Strict already blocks cross-site cookie sends; the origin check
			// is the second line (e.g. against DNS rebinding onto 127.0.0.1).
			if (MUTATING.contains(request.getMethod()) && !sameOrigin(request)) {
				log.warn("Rejected cross-origin {} {} with a session cookie", request.getMethod(), path);
				return deny(exchange, HttpStatus.FORBIDDEN, "cross-origin request rejected");
			}
			return chain.filter(exchange);
		}
		String bearer = bearerOf(request);
		if (bearer != null && auth.verifyToken(bearer)) {
			// The bearer token is not a cookie; CSRF does not apply.
			return chain.filter(exchange);
		}
		return deny(exchange, HttpStatus.UNAUTHORIZED, "authentication required");
	}

	/** Reads the session cookie value, or {@code null} when absent. */
	public static String sessionIdOf(ServerHttpRequest request) {
		HttpCookie cookie = request.getCookies().getFirst(SESSION_COOKIE);
		return cookie == null ? null : cookie.getValue();
	}

	/** Reads the {@code Authorization: Bearer} token, or {@code null} when absent. */
	public static String bearerOf(ServerHttpRequest request) {
		String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return null;
		}
		String token = header.substring(7).trim();
		return token.isEmpty() ? null : token;
	}

	// /api/auth/status, /login and /logout are the only endpoints reachable without a
	// credential; logout without a session is a harmless no-op.
	private static boolean isPublic(String path) {
		return path.equals("/api/auth/status") || path.equals("/api/auth/login") || path.equals("/api/auth/logout");
	}

	/**
	 * Compares the Origin (fallback: Referer) host against the request's Host header,
	 * an X-Forwarded-Host, or a loopback spelling. Requests without origin information
	 * pass: non-browser clients legitimately omit it, and SameSite=Strict still guards
	 * the cookie.
	 */
	private boolean sameOrigin(ServerHttpRequest request) {
		String candidate = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
		if (candidate == null || candidate.isBlank()) {
			candidate = request.getHeaders().getFirst(HttpHeaders.REFERER);
		}
		if (candidate == null || candidate.isBlank()) {
			return true;
		}
		URI origin;
		try {
			origin = new URI(candidate);
		} catch (URISyntaxException ex) {
			return false;
		}
		String host = origin.getHost();
		if (host == null) {
			return false;
		}
		InetSocketAddress hostHeader = request.getHeaders().getHost();
		String forwardedHost = request.getHeaders().getFirst("X-Forwarded-Host");
		return (
			matches(host, hostHeader == null ? null : hostHeader.getHostString()) ||
			matches(host, forwardedHost) ||
			LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))
		);
	}

	private static boolean matches(String originHost, String candidate) {
		return candidate != null && originHost.equalsIgnoreCase(candidate);
	}

	private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, String message) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
		return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
	}
}
