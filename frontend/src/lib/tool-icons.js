// Maps harness tool names to the icon shown on their tool call header.
// Unknown names (tools from connected MCP servers) fall back to the shared
// plug icon, so every call always renders with an appropriate glyph.
import {
	Bot,
	FilePen,
	FilePlus,
	FileText,
	FolderSearch,
	Globe,
	Info,
	Link2,
	MessageCircleQuestionMark,
	Plug,
	SquareTerminal,
} from "@lucide/vue";

export const TOOL_ICONS = {
	bash: SquareTerminal,
	bg_start: SquareTerminal,
	bg_read: SquareTerminal,
	bg_send: SquareTerminal,
	bg_kill: SquareTerminal,
	bg_list: SquareTerminal,
	read_file: FileText,
	write_file: FilePlus,
	edit_file: FilePen,
	web_search: Globe,
	web_fetch: Link2,
	workspace_info: Info,
	workspace_search: FolderSearch,
	sub_agent: Bot,
	ask_user_question: MessageCircleQuestionMark,
};

export function toolIcon(name) {
	return TOOL_ICONS[name] ?? Plug;
}
