package io.github.iso53.castiel.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The harness's own tools, grouped for display and individually toggleable.
 *
 * <p>Groups are declared here rather than reflected from the beans because the
 * interesting facts are editorial, not structural: which tools belong together,
 * which ones the app cannot run without, and what to call them in the UI.
 * Reflection only reveals that {@code bg_start} exists, not that it is one half
 * of "Background processes" or that it may not be switched off.
 *
 * <p>{@link #validateAgainst(Set)} makes the drift that reflection cannot catch
 * fail loudly at startup instead of silently dropping a tool from the UI.
 */
@Service
public class ToolCatalog {

	/**
	 * One user-facing group: its label, the tool names behind it, whether it may be disabled,
	 * and whether disabling it deserves a warning.
	 *
	 * <p>{@code critical} is not "the app stops working" -- nothing here does that. It marks the
	 * groups whose absence costs correctness rather than convenience: findings go unscored, or
	 * long-running work becomes impossible. The user-facing consequence text is deliberately
	 * generic, so this flag decides only *whether* to warn, never what to say.
	 */
	public record ToolGroup(
		String id,
		String label,
		String description,
		List<String> tools,
		boolean locked,
		boolean critical
	) {}

	private static final List<ToolGroup> GROUPS = List.of(
		new ToolGroup(
			"shell",
			"Shell",
			"Runs commands in the workspace. PowerShell on Windows, bash elsewhere.",
			List.of("bash"),
			true,
			false
		),
		new ToolGroup(
			"files",
			"Files",
			"Reads, writes and edits workspace files. Every view is fed by the JSON the agent writes.",
			List.of("read_file", "write_file", "edit_file"),
			true,
			false
		),
		new ToolGroup(
			"workspace_info",
			"Workspace layout",
			"Describes the workspace directory layout and what is missing from it.",
			List.of("workspace_info"),
			true,
			false
		),
		new ToolGroup(
			"web",
			"Web",
			"Fetches pages and searches the web for background research.",
			List.of("web_fetch", "web_search"),
			false,
			false
		),
		new ToolGroup(
			"workspace_search",
			"Workspace search",
			"Greps the workspace for files and content matching a query.",
			List.of("workspace_search"),
			false,
			true
		),
		new ToolGroup(
			"sub_agent",
			"Sub-agents",
			"Delegates narrow mechanical work to a sub-agent running its own model.",
			List.of("sub_agent"),
			false,
			false
		),
		new ToolGroup(
			"cvss_score",
			"CVSS scoring",
			"Computes official CVSS v4.0 and v3.1 scores and attaches them to findings.",
			List.of("cvss_score"),
			false,
			true
		),
		new ToolGroup(
			"kali_tools",
			"Kali discovery",
			"Searches the Kali tool catalog to find a purpose-built binary for a job.",
			List.of("search_kali_tools"),
			false,
			false
		),
		new ToolGroup(
			"kali_launch",
			"Kali launch",
			"Starts the Kali tools you picked in the Kali dialog as background scans.",
			List.of("kali_launch"),
			false,
			true
		),
		new ToolGroup(
			"ask_user_question",
			"Ask the user",
			"Lets the agent ask you a multiple-choice question and wait for your answer.",
			List.of("ask_user_question"),
			false,
			false
		),
		new ToolGroup(
			"background",
			"Background processes",
			"Starts long-running commands and reads, sends to, and kills them.",
			List.of("bg_start", "bg_list", "bg_read", "bg_send", "bg_kill"),
			false,
			true
		)
	);

	private final Map<String, ToolGroup> byId;
	private final Map<String, String> groupByTool;

	public ToolCatalog() {
		this.byId = GROUPS.stream()
			.collect(Collectors.toMap(ToolGroup::id, group -> group, (a, b) -> a, LinkedHashMap::new));
		Map<String, String> owners = new LinkedHashMap<>();
		for (ToolGroup group : GROUPS) {
			for (String tool : group.tools()) {
				owners.put(tool, group.id());
			}
		}
		this.groupByTool = Map.copyOf(owners);
	}

	/** Every group, in display order. */
	public List<ToolGroup> groups() {
		return GROUPS;
	}

	/** The group a tool belongs to, or null when it is not declared here. */
	private String groupOf(String toolName) {
		return groupByTool.get(toolName);
	}

	/** Whether a group is switched on right now. Locked groups are always on. */
	public boolean isGroupEnabled(String groupId, Set<String> disabledGroups) {
		ToolGroup group = byId.get(groupId);
		return group != null && !group.locked() && !disabledGroups.contains(groupId);
	}

	/**
	 * Whether a tool should be offered to the model.
	 *
	 * <p>Locked groups always resolve to enabled, so a hand-edited settings file
	 * cannot switch off the tools the app is built on. Undeclared tools are enabled
	 * too: the UI only knows about declared groups, so silently disabling something
	 * the user cannot even see would be worse than leaving it on.
	 */
	public boolean isEnabled(String toolName, Set<String> disabledGroups) {
		String groupId = groupOf(toolName);
		if (groupId == null) {
			return true;
		}
		return isGroupEnabled(groupId, disabledGroups);
	}

	/** Whether a group id exists in the catalogue and may be switched off by the user. */
	public boolean isKnownToggleable(String groupId) {
		ToolGroup group = byId.get(groupId);
		return group != null && !group.locked();
	}

	/** Fails startup when a declared name has no matching {@code @Tool}. */
	public void validateAgainst(Set<String> liveToolNames) {
		List<String> missing = groupByTool.keySet().stream()
			.filter(name -> !liveToolNames.contains(name))
			.sorted()
			.toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException(
				"ToolCatalog declares tools that no @Tool provides: " + String.join(", ", missing)
			);
		}
	}
}