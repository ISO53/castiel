package io.github.iso53.castiel.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.iso53.castiel.service.WorkspaceSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates or fully overwrites text files on behalf of the model. */
@Service
public class FileWriteTool implements ToolProvider {

	private final WorkspaceSession workspace;

	FileWriteTool(WorkspaceSession workspace) {
		this.workspace = workspace;
	}

	@Tool(
		name = "write_file",
		value = {
			"Writes a text file, creating it or fully overwriting an existing one.",
			"Relative paths are resolved against the current workspace directory.",
			"Overwriting an existing file requires reading it with read_file first.",
			"If the purpose is editing something in the file, edit_file tool should be used instead."
		}
	)
	public String writeFile(
		@P("Relative or absolute path of the file to write") String path,
		@P("Full content to write to the file") String content
	) {
		if (content == null) {
			return "Error: content is required";
		}
		Path file = workspace.resolveInWorkspace(path);
		if (Files.exists(file) && !Files.isRegularFile(file)) {
			return "Error: " + file + " exists and is not a regular file";
		}
		if (Files.exists(file) && !workspace.isKnown(file)) {
			return "Error: " + file + " already exists; read it with read_file before overwriting";
		}
		try {
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			Files.writeString(file, content);
			workspace.remember(file);
			return "Wrote " + content.length() + " characters to " + file;
		} catch (IOException ex) {
			return "Error: could not write " + file + ": " + ex.getMessage();
		}
	}
}
