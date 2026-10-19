package io.github.iso53.castiel.util;

/** Shared HTTP constants so every outbound client identifies itself the same way. */
public final class Http {

	/**
	 * Identifies castiel on shared public APIs; some APIs' responsible-use guides
	 * (e.g. the CVE Search API's) require app name and contact info in the UA string.
	 */
	public static final String USER_AGENT = "castiel (+https://github.com/ISO53/castiel)";

	private Http() {}
}
