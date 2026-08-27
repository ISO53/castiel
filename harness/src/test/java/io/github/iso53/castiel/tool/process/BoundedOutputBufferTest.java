package io.github.iso53.castiel.tool.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure logic tests for the bounded tail buffer backing managed processes. */
class BoundedOutputBufferTest {

	@Test
	void keepsRecentOutputAndCountsEvictedHead() {
		BoundedOutputBuffer buffer = new BoundedOutputBuffer(10);
		buffer.append("abcdefghij"); // exactly full
		buffer.append("KLMN");

		BoundedOutputBuffer.Page page = buffer.read(0, 100);
		assertTrue(page.text().startsWith("[harness]"), page.text());
		assertTrue(page.text().endsWith("efghijKLMN"), page.text());
		assertEquals(4, page.discarded());
	}

	@Test
	void deltaReadsResumeExactlyWhereThePreviousOneStopped() {
		BoundedOutputBuffer buffer = new BoundedOutputBuffer(1_000);
		buffer.append("hello ");

		BoundedOutputBuffer.Page first = buffer.read(0, 100);
		assertEquals("hello ", first.text());

		buffer.append("world");
		BoundedOutputBuffer.Page second = buffer.read(first.cursor(), 100);
		assertEquals("world", second.text());
		assertEquals(second.cursor(), second.cursor()); // stable offset
		assertFalse(second.discarded() > 0);

		BoundedOutputBuffer.Page drained = buffer.read(second.cursor(), 100);
		assertEquals("", drained.text());
	}

	@Test
	void maxCharsCapsEachReadWithoutLosingOffset() {
		BoundedOutputBuffer buffer = new BoundedOutputBuffer(1_000);
		buffer.append("abcdef");

		BoundedOutputBuffer.Page capped = buffer.read(0, 3);
		assertEquals("abc", capped.text());
		BoundedOutputBuffer.Page rest = buffer.read(capped.cursor(), 100);
		assertEquals("def", rest.text());
	}

	@Test
	void cursorAheadOfWriterYieldsEmptyPage() {
		BoundedOutputBuffer buffer = new BoundedOutputBuffer(16);
		buffer.append("x");
		assertTrue(buffer.read(999, 10).text().isEmpty());
	}
}
