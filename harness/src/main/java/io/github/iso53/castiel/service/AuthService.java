package io.github.iso53.castiel.service;

import io.github.iso53.castiel.util.AppPaths;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Shared-secret authentication for the opt-in {@code --auth} gate.
 *
 * <p>One credential (the token in {@link AuthStore}) and one role: whoever holds it.
 * Browser flows exchange the token once for a session cookie; scripts pass the token
 * as a bearer header. Sessions are in-memory only - restarting castiel drops them,
 * which is also how {@code --reset-token} takes effect.</p>
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private static final int MAX_LOGIN_FAILURES = 5;
	private static final Duration LOCKOUT = Duration.ofSeconds(60);

	// SHA-256 of the token; comparison is constant-time on the digests so neither the
	// token value nor its length leaks through timing.
	private volatile byte[] tokenDigest;

	private final boolean enabled;
	private final Duration sessionIdle;
	private final Duration sessionAbsolute;
	private final Environment env;

	private final Map<String, Session> sessions = new ConcurrentHashMap<>();
	private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

	/** One authenticated browser session. */
	private static final class Session {

		private final Instant createdAt = Instant.now();
		private volatile Instant lastAccess = Instant.now();
	}

	/** Failed login bookkeeping per remote address. */
	private static final class Attempt {

		private int failures;
		private Instant lockedUntil;
	}

	public AuthService(io.github.iso53.castiel.config.AuthProperties properties, Environment env) {
		this.enabled = properties.enabled();
		this.sessionIdle = properties.sessionIdle();
		this.sessionAbsolute = properties.sessionAbsolute();
		this.env = env;
	}

	@PostConstruct
	void init() {
		if (!enabled) {
			refuseWhenExposed();
			return;
		}
		AuthStore.LoadedToken stored = AuthStore.loadOrCreate();
		tokenDigest = sha256(stored.token());
		if (stored.newlyCreated()) {
			log.info("Auth is enabled. Access token (stored in {}): {}", AppPaths.authFile(), stored.token());
		} else {
			log.info("Auth is enabled; token loaded from disk (display it with: castiel --show-token)");
		}
	}

	/** Whether the {@code --auth} gate is active; false leaves the API open as before. */
	public boolean enabled() {
		return enabled;
	}

	/** Absolute session lifetime; also the login cookie's Max-Age. */
	public Duration sessionAbsolute() {
		return sessionAbsolute;
	}

	/** Constant-time token check; blank or unset input never matches. */
	public boolean verifyToken(String candidate) {
		byte[] digest = tokenDigest;
		if (digest == null || candidate == null || candidate.isBlank()) {
			return false;
		}
		return MessageDigest.isEqual(digest, sha256(candidate.trim()));
	}

	/** Issues a fresh 128-bit session id; called after a verified login. */
	public String createSession() {
		byte[] raw = new byte[16];
		new SecureRandom().nextBytes(raw);
		String id = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		sessions.put(id, new Session());
		return id;
	}

	/**
	 * Validates a session id against the idle and absolute timeouts, refreshing the
	 * idle clock on success. Expired ids are removed on sight.
	 */
	public boolean validateSession(String id) {
		if (id == null) {
			return false;
		}
		Session session = sessions.get(id);
		if (session == null) {
			return false;
		}
		Instant now = Instant.now();
		if (
			session.createdAt.plus(sessionAbsolute).isBefore(now) || session.lastAccess.plus(sessionIdle).isBefore(now)
		) {
			sessions.remove(id);
			return false;
		}
		session.lastAccess = now;
		return true;
	}

	/** Drops one session (logout). */
	public void invalidate(String id) {
		if (id != null) {
			sessions.remove(id);
		}
	}

	/** Whether the remote address is currently locked out of the login endpoint. */
	public boolean isLocked(String ip) {
		Attempt attempt = attempts.get(ip);
		return attempt != null && attempt.lockedUntil != null && attempt.lockedUntil.isAfter(Instant.now());
	}

	/** Counts a failed login; after {@code MAX_LOGIN_FAILURES} the address is locked out. */
	public void recordFailure(String ip) {
		attempts.compute(ip, (key, attempt) -> {
			Instant now = Instant.now();
			if (attempt == null) {
				attempt = new Attempt();
			} else if (attempt.lockedUntil != null && attempt.lockedUntil.isBefore(now)) {
				// The lockout has been served; start a fresh count.
				attempt.failures = 0;
				attempt.lockedUntil = null;
			}
			attempt.failures++;
			if (attempt.failures >= MAX_LOGIN_FAILURES) {
				attempt.failures = 0;
				attempt.lockedUntil = now.plus(LOCKOUT);
				log.warn("Login attempts from {} exceeded the limit; locked out for {}", ip, LOCKOUT);
			}
			return attempt;
		});
	}

	/** Clears the failure count after a successful login. */
	public void recordSuccess(String ip) {
		attempts.remove(ip);
	}

	// Auth disabled plus a non-loopback bind is the classic footgun: the API runs
	// arbitrary commands, so exposure without the gate must not boot at all. The CLI
	// already refuses --bind without --auth; this boot-time check covers overrides
	// that bypass the command line (e.g. the SERVER_ADDRESS environment variable).
	private void refuseWhenExposed() {
		String address = env.getProperty("server.address");
		if (address == null || address.isBlank()) {
			return;
		}
		boolean loopback;
		try {
			loopback = InetAddress.getByName(address).isLoopbackAddress();
		} catch (UnknownHostException ex) {
			// Unresolvable addresses cannot bind either; let the server start fail on its own.
			loopback = true;
		}
		if (loopback) {
			return;
		}
		String message =
			"Auth is disabled and castiel binds to " +
			address +
			" - anyone who can reach this address can " +
			"run commands on this machine. Start with --auth, or keep the default localhost bind.";
		log.error(message);
		throw new IllegalStateException(message);
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}
