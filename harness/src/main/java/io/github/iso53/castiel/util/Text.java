package io.github.iso53.castiel.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Text helpers shared by the file tools, the explorer, workspace
 * search and the truncating tool outputs: one binary sniff, one
 * truncation rule.
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

	/**
	 * Text cut to {@code limit} characters, with {@code noticeFormat} appended when it
	 * actually cut. The format takes two {@code %d} arguments, the limit and the
	 * original length, so a caller never builds the notice on the common short-text path.
	 * Shorter (or null) text is returned untouched.
	 */
	public static String truncate(String text, int limit, String noticeFormat) {
		if (text == null || text.length() <= limit) {
			return text;
		}
		return text.substring(0, limit) + noticeFormat.formatted(limit, text.length());
	}

	/**
	 * Elapsed time as one short phrase, e.g. {@code 45s}, {@code 12m}, {@code 1h05m}.
	 * One rule for every runtime the agent and the UI read, so a process and a
	 * sub-agent never phrase the same duration differently.
	 */
	public static String runtime(long seconds) {
		long minutes = seconds / 60;
		if (minutes < 1) {
			return seconds + "s";
		}
		if (minutes < 60) {
			return minutes + "m";
		}
		return "%dh%02dm".formatted(minutes / 60, minutes % 60);
	}
}
