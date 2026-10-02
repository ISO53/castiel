package io.github.iso53.castiel.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Text-file sniffing shared by the file tools, the explorer and workspace search.
 * One implementation so the three callers cannot drift on what counts as binary.
 */
public final class Text {

	/** Bytes inspected by the sniff; a NUL this early almost never occurs in text. */
	private static final int SNIFF_BYTES = 8_192;

	private Text() {}

	/** Whether a file looks binary. Reads only its first {@link #SNIFF_BYTES}. */
	public static boolean isBinary(Path file) throws IOException {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
			return isBinary(in.readNBytes(SNIFF_BYTES));
		}
	}

	/** Whether a byte array looks binary: a NUL byte within the sniffed prefix. */
	public static boolean isBinary(byte[] bytes) {
		int limit = Math.min(bytes.length, SNIFF_BYTES);
		for (int index = 0; index < limit; index++) {
			if (bytes[index] == 0) {
				return true;
			}
		}
		return false;
	}
}
