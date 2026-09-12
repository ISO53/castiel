package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.iso53.castiel.model.ChatTurn;
import io.github.iso53.castiel.model.ToolCallPayload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compact view of the thread the model sees. The full thread is always persisted; the
 * most recent tool calls stay verbatim while older results/arguments shrink to short
 * previews. Applied at request-build time only — never written back to the session.
 */
final class HistoryCompactor {

	private static final ObjectMapper JSON = new ObjectMapper();

	/** Tool calls (counted from the newest backwards) kept verbatim in the model's history view. */
	private static final int VERBATIM_TOOL_CALLS = 8;

	/** Preview characters kept from each compacted result/argument in the history view. */
	private static final int COMPACTED_PREVIEW_CHARS = 160;

	private HistoryCompactor() {
	}

	static List<ChatTurn> compactTurns(List<ChatTurn> turns) {
		int totalCalls = 0;
		for (ChatTurn turn : turns) {
			totalCalls += turn.toolCalls().size();
		}
		int firstVerbatim = totalCalls - VERBATIM_TOOL_CALLS;
		if (firstVerbatim <= 0) {
			return turns;
		}
		List<ChatTurn> compacted = new ArrayList<>(turns.size());
		int index = 0;
		for (ChatTurn turn : turns) {
			if (turn.toolCalls().isEmpty()) {
				compacted.add(turn);
				continue;
			}
			boolean changed = false;
			List<ToolCallPayload> calls = new ArrayList<>(turn.toolCalls().size());
			for (ToolCallPayload call : turn.toolCalls()) {
				if (index >= firstVerbatim) {
					calls.add(call);
				} else {
					calls.add(new ToolCallPayload(
						call.id(),
						call.name(),
						previewArguments(call.arguments(), COMPACTED_PREVIEW_CHARS),
						preview(call.result(), COMPACTED_PREVIEW_CHARS)
					));
					changed = true;
				}
				index++;
			}
			compacted.add(changed ? new ChatTurn(turn.role(), turn.content(), calls) : turn);
		}
		return compacted;
	}

	/** Truncated preview with a notice telling the model how to get the content back. */
	private static String preview(String text, int limit) {
		if (text == null || text.length() <= limit) {
			return text;
		}
		return text.substring(0, limit)
			+ "\n[harness: older tool call payload compacted — original " + text.length()
			+ " characters; repeat the call if this output is needed again]";
	}

	// Compacts tool call arguments to a preview that stays valid JSON for providers to parse.
	private static String previewArguments(String arguments, int limit) {
		if (arguments == null || arguments.length() <= limit) {
			return arguments;
		}
		try {
			JsonNode parsed = JSON.readTree(arguments);
			if (parsed.isObject()) {
				ObjectNode copy = ((ObjectNode) parsed).deepCopy();
				truncateStringValues(copy, limit);
				return JSON.writeValueAsString(copy);
			}
		} catch (IOException ignored) {
			// Not a JSON object; fall through to the notice below.
		}
		ObjectNode notice = JSON.createObjectNode();
		notice.put(
			"note",
			"[harness: older tool call arguments compacted — original " + arguments.length()
				+ " characters; repeat the call if the arguments are needed again]"
		);
		return notice.toString();
	}

	/** Truncates long string values in the object tree; nested objects and arrays recurse. */
	private static void truncateStringValues(ObjectNode node, int limit) {
		for (Map.Entry<String, JsonNode> field : node.properties()) {
			JsonNode value = field.getValue();
			if (value.isTextual()) {
				node.put(field.getKey(), preview(value.asText(), limit));
			} else if (value.isObject()) {
				truncateStringValues((ObjectNode) value, limit);
			} else if (value.isArray()) {
				value.forEach(item -> {
					if (item.isObject()) {
						truncateStringValues((ObjectNode) item, limit);
					}
				});
			}
		}
	}
}
