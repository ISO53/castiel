package io.github.iso53.castiel.tool.process;

/**
 * Append-only character buffer that keeps the most recent {@code capacity} characters
 * and reports positions with monotonically increasing offsets.
 *
 * <p>Multiple independent readers track their own last-read offset, so the agent and
 * the UI can tail the same stream without interfering. Characters evicted from the
 * front are counted; a read whose cursor falls behind eviction is told about the gap.
 *
 * <p>All methods are synchronized; writers are output-drain threads, readers are the
 * agent tool path and HTTP polling.
 */
public final class BoundedOutputBuffer {

	/** One read result: text plus the cursor position a follow-up read should pass. */
	public record Page(long cursor, String text, long discarded) {}

	private final StringBuilder data = new StringBuilder();
	private final int capacity;

	/** Global char index of {@code data.charAt(0)}; advances when the head is evicted. */
	private long startOffset;

	BoundedOutputBuffer(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.capacity = capacity;
	}

	/** Appends decoded output; evicts characters beyond the capacity from the front. */
	synchronized void append(CharSequence chunk) {
		data.append(chunk);
		int overflow = data.length() - capacity;
		if (overflow > 0) {
			data.delete(0, overflow);
			startOffset += overflow;
		}
	}

	/** Total characters ever appended. */
	synchronized long length() {
		return startOffset + data.length();
	}

	/**
	 * Returns everything appended after {@code cursor}, capped at {@code maxChars}.
	 *
	 * <p>A cursor older than the retained window yields a marker line describing how many
	 * characters were lost instead of silently pretending nothing was missed.
	 */
	public synchronized Page read(long cursor, int maxChars) {
		long end = startOffset + data.length();
		if (cursor >= end) {
			return new Page(Math.max(cursor, end), "", 0);
		}
		if (cursor < startOffset) {
			long discarded = startOffset - Math.max(cursor, 0);
			String marker =
				"[harness] " + discarded + " characters of earlier output were discarded (buffer limit reached)\n";
			int to = Math.min(data.length(), maxChars);
			return new Page(end, marker + data.substring(0, to), discarded);
		}
		int from = (int) (cursor - startOffset);
		int to = Math.min(data.length(), from + maxChars);
		return new Page(startOffset + to, data.substring(from, to), 0);
	}
}
