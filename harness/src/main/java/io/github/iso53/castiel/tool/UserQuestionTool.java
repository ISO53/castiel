package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
}
