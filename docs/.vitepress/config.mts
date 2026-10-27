import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vitepress";

// Deployed to GitHub Pages as a project site: https://iso53.github.io/castiel/
export default defineConfig({
	base: "/castiel/",
	lang: "en",
	title: "castiel",
	description:
		"castiel is an AI-assisted penetration testing harness. Connect a workspace to a language model of your choice and work together through a chat, while you stay in charge.",
	head: [
		["link", { rel: "icon", type: "image/svg+xml", href: "/castiel/favicon.svg" }],
	],
	// The brand is black-only; there is no light theme to toggle.
	appearance: false,
	// Keep the output at docs/dist so the existing GitHub Pages workflow
	// (which uploads docs/dist) keeps working unchanged.
	outDir: "dist",
	// Code blocks: VitePress's default palette assumes a gray surface, not pure
	// black, and its saturated tokens read as garish on this site. Use the muted
	// vitesse-dark theme for both modes (appearance is disabled) so tokens stay
	// CSS-variable driven and easy to tune in custom.css.
	markdown: {
		theme: { light: "vitesse-dark", dark: "vitesse-dark" },
	},
	lastUpdated: true,
	themeConfig: {
		// The guide pages use the default docs layout; the landing page keeps its
		// own NavBar (which links here as "Docs").
		nav: [
			{ text: "Docs", link: "/guide/", activeMatch: "^/guide/" },
			{ text: "GitHub", link: "https://github.com/ISO53/castiel" },
		],
		sidebar: {
			"/guide/": [
				{
					text: "Introduction",
					items: [
						{ text: "What is castiel", link: "/guide/" },
						{ text: "Getting started", link: "/guide/getting-started" },
					],
				},
				{
					text: "Core concepts",
					items: [
						{ text: "Engagement phases", link: "/guide/engagement-phases" },
						{ text: "Engagement documents", link: "/guide/engagement-documents" },
						{ text: "Tools", link: "/guide/tools" },
						{ text: "Sub-agents & background work", link: "/guide/sub-agents" },
					],
				},
				{
					text: "Configuration",
					items: [
						{ text: "LLM providers", link: "/guide/providers" },
						{ text: "MCP servers", link: "/guide/mcp" },
						{ text: "CLI reference", link: "/guide/cli" },
					],
				},
				{
					text: "About",
					items: [{ text: "Acknowledgments", link: "/guide/acknowledgments" }],
				},
			],
		},
		socialLinks: [{ icon: "github", link: "https://github.com/ISO53/castiel" }],
		// Local full-text search (MiniSearch) — no external service, works on Pages.
		search: {
			provider: "local",
		},
		editLink: {
			pattern: "https://github.com/ISO53/castiel/edit/main/docs/:path",
			text: "Edit this page on GitHub",
		},
	},
	vite: {
		resolve: {
			alias: {
				"@": fileURLToPath(new URL("../src", import.meta.url)),
			},
		},
	},
});
