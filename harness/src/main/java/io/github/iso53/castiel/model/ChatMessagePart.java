package io.github.iso53.castiel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A single segment within a chat message (text, reasoning/thinking, tool call, or questionnaire).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessagePart(
	String type,
	String text,
	String id,
	String name,
	String arguments,
	String result,
	Boolean open,
	String question,
	List<String> options,
	Boolean multiSelect,
	Boolean answered
) {}
