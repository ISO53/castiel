package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.iso53.castiel.util.AppPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shared-secret token store backing {@code --auth}. Static helpers so the
 * {@code --show-token} and {@code --reset-token} CLI flows can run before Spring boots.
 *
 * <p>The token lives in {@code auth.json} next to {@code settings.json} and is created
 * with owner-only permissions on POSIX filesystems (Windows relies on the profile
 * directory ACLs, mirroring how {@code settings.json} stores provider keys).</p>
 */
public final class AuthStore {

	private static final Logger log = LoggerFactory.getLogger(AuthStore.class);

	private static final ObjectMapper JSON = new ObjectMapper()
		.enable(SerializationFeature.INDENT_OUTPUT)
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	/** Wire shape of the token file. */
	private record StoredToken(String token, String createdAt) {}

	/** The token plus whether this call generated it (drives the first-boot log line). */
	public record LoadedToken(String token, boolean newlyCreated) {}

	private AuthStore() {}

	/** Reads the token, generating and persisting a fresh one when absent. */
	public static synchronized LoadedToken loadOrCreate() {
		String existing = load();
		if (existing != null) {
			return new LoadedToken(existing, false);
		}
		String generated = generate();
		write(generated);
		log.info("Stored the auth token in {}", AppPaths.authFile());
		return new LoadedToken(generated, true);
	}

	/** Returns the stored token, or {@code null} when auth was never initialized. */
	public static synchronized String load() {
		Path file = AppPaths.authFile();
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			StoredToken stored = JSON.readValue(file.toFile(), StoredToken.class);
			if (stored == null || stored.token() == null || stored.token().isBlank()) {
				return null;
			}
			return stored.token();
		} catch (IOException ex) {
			log.warn("Could not read the auth token from {}; treating it as unset", file, ex);
			return null;
		}
	}

	/** Generates a fresh token, persists it, and returns it. */
	public static synchronized String reset() {
		String generated = generate();
		write(generated);
		log.info("Replaced the auth token in {}", AppPaths.authFile());
		return generated;
	}

	// 32 random bytes: 256 bits of entropy, base64url without padding.
	private static String generate() {
		byte[] raw = new byte[32];
		new SecureRandom().nextBytes(raw);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
	}

	private static void write(String token) {
		Path file = AppPaths.authFile();
		try {
			Files.createDirectories(file.getParent());
			if (!Files.exists(file)) {
				if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
					Files.createFile(
						file,
						PosixFilePermissions.asFileAttribute(
							Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
						)
					);
				} else {
					Files.createFile(file);
				}
			}
			Files.writeString(
				file,
				JSON.writeValueAsString(new StoredToken(token, Instant.now().toString())),
				StandardCharsets.UTF_8
			);
		} catch (IOException ex) {
			throw new UncheckedIOException("Could not write the auth token to " + file, ex);
		}
	}
}
