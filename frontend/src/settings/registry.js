import { Bot, Cpu, Plug, ShieldAlert, Wrench } from "@lucide/vue";

/**
 * The settings tree, in sidebar order. It is the single source of truth for
 * the sidebar, the content header, the search index and the lazy section chunks.
 *
 * A top-level node either groups `children` — the sidebar collapses it to
 * reveal them — or is a page in its own right and carries `component`.
 * `keywords` are the aliases users actually type, and carry most of the search
 * weight after the title.
 */
export const SETTINGS_TREE = [
	{
		id: "llm-providers",
		title: "LLM Providers",
		icon: Bot,
		description: "Connect the language model backends used by the harness.",
		children: [
			{
				id: "llama.cpp",
				title: "llama.cpp",
				logo: "llama.cpp",
				description: "Run open models locally with llama.cpp's built-in server, or point castiel at a remote one.",
				keywords: ["local", "model", "gguf", "openai compatible", "server", "context window"],
				component: () => import("@/components/settings/sections/LlamaCppSection.vue"),
			},
			{
				id: "ollama",
				title: "Ollama",
				logo: "ollama",
				description: "Run open models locally with Ollama, or point castiel at a remote Ollama server.",
				keywords: ["local", "model", "server", "context window"],
				component: () => import("@/components/settings/sections/OllamaSection.vue"),
			},
			{
				id: "openrouter",
				title: "OpenRouter",
				logo: "openrouter",
				description: "One OpenRouter key unlocks models from many upstream providers. castiel only ever needs this single key.",
				keywords: ["api key", "cloud", "models", "openai compatible", "byok"],
				component: () => import("@/components/settings/sections/OpenRouterSection.vue"),
			},
			{
				id: "cline",
				title: "Cline",
				logo: "cline",
				description: "Reach Anthropic, OpenAI, Google and more through Cline's OpenAI-compatible endpoint, with a key or an account sign-in.",
				keywords: ["api key", "account", "sign in", "oauth", "anthropic", "openai", "google"],
				component: () => import("@/components/settings/sections/ClineSection.vue"),
			},
		],
	},
	{
		id: "mcp",
		title: "MCP Servers",
		icon: Plug,
		description:
			"MCP servers give the agent extra tools such as a headless browser or proxy access. They are declared in the mcp.json file, which castiel watches and reloads automatically.",
		keywords: ["mcp.json", "tools", "obscura", "caido", "browser", "proxy", "config file"],
		component: () => import("@/components/settings/sections/McpServersSection.vue"),
	},
	{
		id: "tools",
		title: "Tools",
		icon: Wrench,
		description:
			"The tools the agent can call. Switching one off hides it from the model, sub-agents included.",
		keywords: ["bash", "shell", "files", "web", "search", "cvss", "kali", "sub-agent", "background", "disable"],
		component: () => import("@/components/settings/sections/ToolsSection.vue"),
	},
	{
		id: "sub-agents",
		title: "Sub-agents",
		icon: Cpu,
		description:
			"Smaller models the main agent delegates mechanical work to. The worker handles recon, scanning, OSINT and document formatting; the Kali dispatcher starts the tools you pick in the Kali dialog and terminates.",
		keywords: [
			"worker",
			"kali",
			"scanner",
			"launcher",
			"dispatch",
			"model",
			"rounds",
			"budget",
			"recon",
			"osint",
			"delegation",
		],
		component: () => import("@/components/settings/sections/SubAgentsSection.vue"),
	},
	{
		id: "scoring",
		title: "Vulnerability scoring",
		icon: ShieldAlert,
		description:
			"Findings are scored by the harness with both CVSS v4.0 and v3.1 vectors. Pick the version the vulnerabilities view shows; the choice applies to every workspace.",
		keywords: ["cvss", "score", "severity", "v4.0", "v3.1", "vector"],
		component: () => import("@/components/settings/sections/VulnerabilityScoringSection.vue"),
	},
];

/**
 * Every selectable row, in sidebar order: the leaves of grouped headers plus
 * the headers that are pages in their own right. Grouped headers are
 * navigation only — they collapse to reveal their leaves.
 */
export const SETTINGS_ROWS = SETTINGS_TREE.flatMap((node) =>
	node.children ? node.children.map((child) => ({ ...child, parent: node })) : [{ ...node, parent: null }],
);

/** Row lookup by id, for routing the content pane. */
export const SETTINGS_ITEM_BY_ID = new Map(SETTINGS_ROWS.map((row) => [row.id, row]));

/** First row, so the dialog always has something to show. */
export const DEFAULT_SETTINGS_ITEM_ID = SETTINGS_ROWS[0].id;
