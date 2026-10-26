package io.github.iso53.castiel.controller;

import io.github.iso53.castiel.config.AuthWebFilter;
import io.github.iso53.castiel.service.AuthService;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Login, logout and status for the opt-in {@code --auth} gate.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService auth;

	public AuthController(AuthService auth) {
		this.auth = auth;
	}

	/** Public: lets the UI decide whether to show the login screen. */
	@GetMapping("/status")
	public Map<String, Boolean> status(ServerHttpRequest request) {
		return Map.of("enabled", auth.enabled(), "authenticated", isRequestAuthenticated(request));
	}

	/** Public: exchanges the shared secret for a session cookie. */
	@PostMapping("/login")
	public Mono<Map<String, Boolean>> login(
		@RequestBody LoginRequest body,
		ServerHttpRequest request,
		ServerHttpResponse response
	) {
		if (!auth.enabled()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "auth is not enabled");
		}
		String ip = remoteIp(request);
		if (auth.isLocked(ip)) {
			throw new ResponseStatusException(
				HttpStatus.TOO_MANY_REQUESTS,
				"too many failed attempts; try again later"
			);
		}
		if (!auth.verifyToken(body == null ? null : body.token())) {
			auth.recordFailure(ip);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
		}
		auth.recordSuccess(ip);
		response
			.getCookies()
			.add(AuthWebFilter.SESSION_COOKIE, sessionCookie(auth.createSession(), request, auth.sessionAbsolute()));
		return Mono.just(Map.of("authenticated", true));
	}

	/** Public by design: logging out without a session is a harmless no-op. */
	@PostMapping("/logout")
	public Mono<Map<String, Boolean>> logout(ServerHttpRequest request, ServerHttpResponse response) {
		String sessionId = AuthWebFilter.sessionIdOf(request);
		if (sessionId != null) {
			auth.invalidate(sessionId);
		}
		response
			.getCookies()
			.add(
				AuthWebFilter.SESSION_COOKIE,
				ResponseCookie.from(AuthWebFilter.SESSION_COOKIE, "")
					.httpOnly(true)
					.secure(isSecure(request))
					.sameSite("Strict")
					.path("/")
					.maxAge(Duration.ZERO)
					.build()
			);
		return Mono.just(Map.of("loggedOut", true));
	}

	private boolean isRequestAuthenticated(ServerHttpRequest request) {
		String sessionId = AuthWebFilter.sessionIdOf(request);
		if (sessionId != null && auth.validateSession(sessionId)) {
			return true;
		}
		String bearer = AuthWebFilter.bearerOf(request);
		return bearer != null && auth.verifyToken(bearer);
	}

	// Secure follows the forwarded scheme; behind `tailscale serve` the request arrives
	// as plain HTTP with X-Forwarded-Proto: https. (WebFlux's ServerHttpRequest has no
	// isSecure(); the URI scheme is wrong behind any TLS-terminating proxy anyway.)
	private static boolean isSecure(ServerHttpRequest request) {
		return "https".equalsIgnoreCase(request.getHeaders().getFirst("X-Forwarded-Proto"));
	}

	private static ResponseCookie sessionCookie(String sessionId, ServerHttpRequest request, Duration maxAge) {
		return ResponseCookie.from(AuthWebFilter.SESSION_COOKIE, sessionId)
			.httpOnly(true)
			.secure(isSecure(request))
			.sameSite("Strict")
			.path("/")
			.maxAge(maxAge)
			.build();
	}

	private static String remoteIp(ServerHttpRequest request) {
		InetSocketAddress remote = request.getRemoteAddress();
		if (remote == null) {
			return "unknown";
		}
		return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
	}

	/** Request body for {@link #login}. */
	public record LoginRequest(String token) {}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> statusError(ResponseStatusException ex) {
		String reason = ex.getReason() != null ? ex.getReason() : "request failed";
		return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
	}
}
