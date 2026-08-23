package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.WorkspaceSession;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads text files for the model, with line numbers and pagination. */
@Service
public class FileReadTool implements ToolProvider {

	private static final int MAX_READ_LINES = 2_000;
	private static final int MAX_READ_CHARS = 100_000;

	private final WorkspaceSession workspace;

	FileReadTool(WorkspaceSession workspace) {
		this.workspace = workspace;
	}

	@Tool(
		name = "read_file",
		value = {
			"Reads a UTF-8 text file and returns numbered lines (cat -n format).",
			"Relative paths are resolved against the current workspace directory.",
			"Use offset and limit to page through large files (default: first 2000 lines).",
		}
	)
	public String readFile(
		@P("Relative or absolute path of the file to read") String path,
		@P("Optional 1-based line number to start reading from; omit or null to start at the beginning") Integer offset,
		@P("Optional maximum number of lines to return; omit or null for the default of 2000") Integer limit
	) {
		Path file = workspace.resolveInWorkspace(path);
		if (!Files.isRegularFile(file)) {
			return "Error: not a readable file: " + file;
		}
		try {
			if (looksBinary(file)) {
				return "Error: " + file + " looks like a binary file and cannot be shown as text";
			}

			List<String> lines = Files.readAllLines(file);
			if (lines.isEmpty() || (lines.size() == 1 && lines.getFirst().isEmpty())) {
				return "(empty file)";
			}

			int start = Math.max(1, offset == null ? 1 : offset);
			int end = Math.min(lines.size(), start - 1 + (limit == null ? MAX_READ_LINES : Math.max(1, limit)));

			StringBuilder out = new StringBuilder();
			for (int number = start; number <= end; number++) {
				out.append("%6d\t%s%n".formatted(number, lines.get(number - 1)));
			}
			if (end < lines.size()) {
				out.append(
					"[showing lines %d-%d of %d; call again with offset=%d to continue]%n".formatted(
						start,
						end,
						lines.size(),
						end + 1
					)
				);
			}

			String result = out.toString();
			if (result.length() > MAX_READ_CHARS) {
				result =
					result.substring(0, MAX_READ_CHARS) +
					"\n... [output truncated at " +
					MAX_READ_CHARS +
					" characters]";
			}
			workspace.remember(file);
			return result;
		} catch (IOException ex) {
			return "Error: could not read " + file + ": " + ex.getMessage();
		}
	}

	private static boolean looksBinary(Path file) throws IOException {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
			for (byte b : in.readNBytes(8_192)) {
				if (b == 0) {
					return true;
				}
			}
		}
		return false;
	}
}
