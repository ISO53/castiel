package io.github.iso53.castiel.config;

import io.github.iso53.castiel.service.AuthService;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
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
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Two envelope gates run for every request, with and without {@code --auth}:
 * the {@code Host} header must name this app (defeats DNS rebinding), and
 * command-sending browser requests must come from this app's own origin.
 * With auth on, {@code /api/**} additionally needs a valid session cookie
 * (browser flow) or the bearer token (scripting). Static UI assets stay open;
 * they hold no secrets and the login screen has to be reachable.
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

	// Host names that mean "this machine"; anything else in the Host header is the
	// fingerprint of DNS rebinding.
	private static final Set<String> LOOPBACK_HOSTS = Set.of(
		"localhost",
		"127.0.0.1",
		"::1",
		"[::1]",
		"0:0:0:0:0:0:0:1"
	);

	private static final PathPatternParser PATTERN_PARSER = PathPatternParser.defaultInstance;
	private static final PathPattern API_PATTERN = PATTERN_PARSER.parse("/api/**");
	private static final PathPattern AUTH_STATUS_PATTERN = PATTERN_PARSER.parse("/api/auth/status");
	private static final PathPattern AUTH_LOGIN_PATTERN = PATTERN_PARSER.parse("/api/auth/login");
	private static final PathPattern AUTH_LOGOUT_PATTERN = PATTERN_PARSER.parse("/api/auth/logout");

	private final AuthService auth;

	/** Host names the Host header may present, lowercase. */
	private final Set<String> allowedHosts;

	/** Whether the bind address covers every interface (there, --auth guards instead). */
	private final boolean wildcardBind;

	public AuthWebFilter(AuthService auth, Environment env) {
		this.auth = auth;
		Set<String> hosts = new HashSet<>();
		for (String name : LOOPBACK_HOSTS) {
			hosts.add(name.toLowerCase(Locale.ROOT));
		}
		// Proxy deployments present a foreign Host name (e.g. machine.tailnet.ts.net
		// behind `tailscale serve`); those are opted into explicitly.
		String extra = env.getProperty("castiel.web.allowed-hosts");
		if (extra != null && !extra.isBlank()) {
			for (String name : extra.split(",")) {
				if (!name.isBlank()) {
					hosts.add(name.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		String bind = env.getProperty("server.address", "").trim();
		this.wildcardBind = bind.isBlank() || bind.equals("0.0.0.0") || bind.equals("::");
		if (!wildcardBind) {
			hosts.add(bind.toLowerCase(Locale.ROOT));
		}
		this.allowedHosts = Set.copyOf(hosts);
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		// First gate, both auth modes: is the envelope addressed to this app at all?
		if (!isAllowedHost(request)) {
			log.warn("Rejected request with unexpected Host header {}", request.getHeaders().getHost());
			return deny(exchange, HttpStatus.FORBIDDEN, "unexpected host header");
		}
		if (!auth.enabled()) {
			// No credential is needed, so a browser page must still prove it is this
			// app's own UI: command-sending requests from other origins are turned away.
			if (MUTATING.contains(request.getMethod()) && !isOwnOrigin(request)) {
				log.warn("Rejected cross-origin {} {}", request.getMethod(), request.getPath().value());
				return deny(exchange, HttpStatus.FORBIDDEN, "cross-origin request rejected");
			}
			return chain.filter(exchange);
		}
		String path = request.getPath().value(); // raw (undecoded) path; log output only
		if (!API_PATTERN.matches(request.getPath())) {
			return chain.filter(exchange);
		}
		if (request.getMethod() == HttpMethod.OPTIONS) {
			return chain.filter(exchange);
		}
		// Authenticated API payloads must never land in caches.
		exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
		if (isPublic(request)) {
			return chain.filter(exchange);
		}

		String sessionId = sessionIdOf(request);
		if (sessionId != null && auth.validateSession(sessionId)) {
			// SameSite=Strict already blocks cross-site cookie sends; the origin check
			// is the second line (e.g. against DNS rebinding onto 127.0.0.1).
			if (MUTATING.contains(request.getMethod()) && !isOwnOrigin(request)) {
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

	/**
	 * /api/auth/status, /login and /logout are the only endpoints reachable without a
	 * credential; logout without a session is a harmless no-op. Matched against the
	 * decoded request path, exactly like the router would.
	 */
	private static boolean isPublic(ServerHttpRequest request) {
		return (
			AUTH_STATUS_PATTERN.matches(request.getPath()) ||
			AUTH_LOGIN_PATTERN.matches(request.getPath()) ||
			AUTH_LOGOUT_PATTERN.matches(request.getPath())
		);
	}

	/**
	 * The Host header's host part (without port) must name this app: a loopback
	 * spelling, the configured bind address, or an entry in
	 * {@code castiel.web.allowed-hosts}. A wildcard bind covers every interface, so
	 * any spelling is legitimate there and {@code --auth} guards the API instead.
	 */
	private boolean isAllowedHost(ServerHttpRequest request) {
		if (wildcardBind) {
			return true;
		}
		InetSocketAddress host = request.getHeaders().getHost();
		if (host == null) {
			return false;
		}
		return allowedHosts.contains(hostName(host));
	}

	/**
	 * Browser-fired requests always carry an Origin (fallback: Referer) naming the
	 * page that sent them, and pages cannot forge that header; it must be this app's
	 * own address. Requests without origin information pass: non-browser clients
	 * legitimately omit it. Only the host and port are compared - the scheme is left
	 * out because TLS-terminating proxies make the request's own scheme unreliable.
	 */
	private boolean isOwnOrigin(ServerHttpRequest request) {
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
		if (hostHeader == null || !host.equalsIgnoreCase(hostName(hostHeader))) {
			return false;
		}
		int originPort = origin.getPort() != -1 ? origin.getPort() : defaultPort(origin.getScheme());
		int hostPort = hostHeader.getPort();
		if (hostPort != -1) {
			return originPort == hostPort;
		}
		// Host without a port: only a match when the origin rides the request's own
		// default port.
		int requestPort =
			request.getURI().getPort() != -1 ? request.getURI().getPort() : defaultPort(request.getURI().getScheme());
		return originPort == requestPort;
	}

	/** Lowercased host name of the Host header, without the IPv6 brackets. */
	private static String hostName(InetSocketAddress host) {
		String name = host.getHostString().toLowerCase(Locale.ROOT);
		if (name.startsWith("[") && name.endsWith("]")) {
			name = name.substring(1, name.length() - 1);
		}
		return name;
	}

	private static int defaultPort(String scheme) {
		return "https".equalsIgnoreCase(scheme) ? 443 : 80;
	}

	private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, String message) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
		return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
	}
}
