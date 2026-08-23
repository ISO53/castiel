package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.WorkspaceSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Performs exact string replacements inside files on behalf of the model. */
@Service
public class FileEditTool implements ToolProvider {

	private final WorkspaceSession workspace;

	FileEditTool(WorkspaceSession workspace) {
		this.workspace = workspace;
	}

	@Tool(
		name = "edit_file",
		value = {
			"Replaces an exact text snippet inside a file.",
			"The snippet must match the file content exactly, including indentation.",
			"Fails when the snippet appears zero or several times unless replace_all is true.",
			"The file must have been read with read_file first.",
		}
	)
	public String editFile(
		@P("Relative or absolute path of the file to edit") String path,
		@P("Exact text to replace") String oldString,
		@P("Replacement text") String newString,
		@P("True to replace every occurrence instead of failing on multiple matches") Boolean replaceAll
	) {
		Path file = workspace.resolveInWorkspace(path);
		if (oldString == null || oldString.isEmpty()) {
			return "Error: old_string is required";
		}
		if (oldString.equals(newString)) {
			return "Error: new_string must differ from old_string";
		}
		if (!Files.isRegularFile(file)) {
			return "Error: not a readable file: " + file;
		}
		if (!workspace.isKnown(file)) {
			return "Error: read " + file + " with read_file before editing it";
		}
		try {
			String content = Files.readString(file);
			String replacement = newString == null ? "" : newString;
			int occurrences = countOccurrences(content, oldString);
			if (occurrences == 0) {
				return "Error: old_string not found in " + file;
			}
			if (occurrences > 1 && !Boolean.TRUE.equals(replaceAll)) {
				return (
					"Error: old_string appears " +
					occurrences +
					" times in " +
					file +
					"; include more surrounding text to make it unique, or set replace_all=true"
				);
			}

			String updated = Boolean.TRUE.equals(replaceAll)
				? content.replace(oldString, replacement)
				: replaceFirst(content, oldString, replacement);
			Files.writeString(file, updated);
			return "Replaced " + (Boolean.TRUE.equals(replaceAll) ? occurrences : 1) + " occurrence(s) in " + file;
		} catch (IOException ex) {
			return "Error: could not edit " + file + ": " + ex.getMessage();
		}
	}

	private static int countOccurrences(String content, String needle) {
		int count = 0;
		for (
			int index = content.indexOf(needle);
			index >= 0;
			index = content.indexOf(needle, index + needle.length())
		) {
			count++;
		}
		return count;
	}

	private static String replaceFirst(String content, String needle, String replacement) {
		int index = content.indexOf(needle);
		return index < 0
			? content
			: content.substring(0, index) + replacement + content.substring(index + needle.length());
	}
}
