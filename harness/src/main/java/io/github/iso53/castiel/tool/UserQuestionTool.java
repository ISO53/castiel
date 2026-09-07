package io.github.iso53.castiel.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Lets the model ask the human a multiple-choice question and block on the answer.
 *
 * <p>{@link io.github.iso53.castiel.service.HarnessService} intercepts
 * {@code ask_user_question} tool calls: the frontend shows the question in a questionnaire,
 * the user's answer arrives on {@code POST /api/chat/questions/{toolCallId}/answer}, and it
 * is returned to the model as the tool result.
 */
@Service
public class UserQuestionTool implements ToolProvider {

	public static final String NAME = "ask_user_question";

	private static final long ANSWER_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();

	private final Map<String, CompletableFuture<String>> pendingQuestions = new ConcurrentHashMap<>();

	@Tool(
		name = NAME,
		value = {
			"Ask the human user a multiple-choice question and wait for their answer.",
			"Use only when blocked on a decision that is genuinely the user's to make.",
			"The user can also type a custom answer instead of picking one of the options.",
		}
	)
	public String askUserQuestion(
		@P("Complete question, specific and ending with a question mark") String question,
		@P("Two to six short answer options") List<String> options,
		@P("True when several options may be selected") Boolean multiSelect
	) {
		// Never executed directly: HarnessService intercepts ask_user_question calls and waits
		// for the user's answer via awaitUserAnswer. This method only defines the tool schema.
		return "Error: internal routing failure; " + NAME + " was not intercepted";
	}

	/** Blocks until the frontend answers the pending question for this tool call id. */
	public String awaitUserAnswer(String toolCallId) {
		CompletableFuture<String> future = new CompletableFuture<>();
		pendingQuestions.put(toolCallId, future);
		try {
			String answer = future.get(ANSWER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			return answer == null || answer.isBlank()
				? "The user dismissed the question without answering."
				: "The user answered: " + answer;
		} catch (TimeoutException ex) {
			return "Error: the user did not answer within 10 minutes; continue with your best judgment";
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return "Error: interrupted while waiting for the user";
		} catch (ExecutionException ex) {
			return "Error: waiting for the user failed: " + ex.getMessage();
		} finally {
			pendingQuestions.remove(toolCallId);
		}
	}

	/** Delivers the user's answer; returns false when no matching question is pending. */
	public boolean completeUserAnswer(String toolCallId, String answer) {
		CompletableFuture<String> future = pendingQuestions.get(toolCallId);
		return future != null && future.complete(answer);
	}

	// ---- argument repair -------------------------------------------------------------------
	// Models frequently send the options as glued prose ("Option one.Option two.Option three.")
	// or as a one-element array instead of the declared List<String>. This tool owns the
	// repair of its own arguments; the harness applies it wherever the arguments are
	// consumed (live UI events, persisted history, replayed model history). Idempotent.

	private static final ObjectMapper REPAIR_JSON = new ObjectMapper();

	/** Splits a numbered option list such as "1. foo 2. bar". */
	private static final Pattern NUMBERED_OPTIONS = Pattern.compile("(?:^|\\s)\\d{1,2}\\.\\s+");
	/** Splits glued sentences such as "First option.Second option." (letter, period, capital). */
	private static final Pattern GLUED_OPTIONS = Pattern.compile("(?<=[a-z0-9\\)\"]\\.)\\s*(?=[\\p{Lu}\\p{Lt}])");

	/**
	 * Returns {@code arguments} with {@code options} repaired into a JSON string array when
	 * {@code name} is this tool; any other tool's arguments pass through untouched.
	 */
	public static String normalizeArguments(String name, String arguments) {
		if (!NAME.equals(name)) {
			return arguments == null ? "" : arguments;
		}
		try {
			JsonNode root = REPAIR_JSON.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
			if (!root.isObject()) {
				return arguments;
			}
			JsonNode options = root.get("options");
			List<String> repaired = null;
			if (options == null || options.isNull() || (options.isArray() && options.isEmpty())) {
				repaired = List.of();
			} else if (options.isTextual()) {
				repaired = splitOptionText(options.asText());
			} else if (options.isArray() && options.size() == 1 && options.get(0).isTextual()) {
				List<String> split = splitOptionText(options.get(0).asText());
				repaired = split.size() > 1 ? split : List.of(options.get(0).asText());
			}
			if (repaired == null) {
				return arguments; // Already a proper array (or an unknown shape left untouched).
			}
			((ObjectNode) root).set("options", REPAIR_JSON.valueToTree(repaired));
			return REPAIR_JSON.writeValueAsString(root);
		} catch (Exception ex) {
			return arguments; // Repair is best-effort; never break a stream over it.
		}
	}

	/** Best-effort split of a single options string into individual options. */
	private static List<String> splitOptionText(String text) {
		String trimmed = text == null ? "" : text.strip();
		if (trimmed.isEmpty()) {
			return List.of();
		}
		List<String> items = new ArrayList<>();
		for (String item : NUMBERED_OPTIONS.split(trimmed)) {
			if (!item.isBlank()) {
				items.add(item.strip());
			}
		}
		if (items.size() > 1) {
			return items;
		}
		items.clear();
		for (String item : GLUED_OPTIONS.split(trimmed)) {
			if (!item.isBlank()) {
				items.add(item.strip());
			}
		}
		return items.size() > 1 ? items : List.of(trimmed);
	}
}
