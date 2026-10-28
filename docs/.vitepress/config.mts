import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vitepress";
import { withMermaid } from "vitepress-plugin-mermaid";

// Deployed to GitHub Pages as a project site: https://iso53.github.io/castiel/
export default withMermaid(
	defineConfig({
		base: "/castiel/",
		lang: "en",
		title: "castiel",
		description:
			"castiel is an AI-assisted penetration testing harness. Connect a workspace to a language model of your choice and work together through a chat, while you stay in charge.",
		head: [["link", { rel: "icon", type: "image/svg+xml", href: "/castiel/favicon.svg" }]],
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
		// Mermaid diagrams (server.md): the default theme assumes a light surface;
		// match the black-only brand palette here.
		mermaid: {
			theme: "dark",
			themeVariables: {
				darkMode: true,
				background: "#000000",
				primaryColor: "#0a0a0a",
				primaryTextColor: "#ffffff",
				primaryBorderColor: "#3a3a3a",
				lineColor: "#7a7a7a",
			},
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
							{ text: "Server deployment (Tailscale)", link: "/guide/server" },
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
			// vitepress-plugin-mermaid hardcodes a mermaid 10-era list of
			// optimizeDeps.include entries; mermaid 11 dropped "debug" from its
			// dependency tree, so Vite logs "Failed to resolve dependency: debug"
			// on every dev start. Strip the stale entry after the plugin's config
			// hook runs (enforce: "post" guarantees it runs last).
			plugins: [
				{
					name: "strip-stale-mermaid-optimize-deps",
					enforce: "post",
					config(config) {
						const include = config.optimizeDeps?.include;
						if (include) {
							config.optimizeDeps.include = include.filter(
								(dep) => dep !== "debug",
							);
						}
					},
				},
			],
			optimizeDeps: {
				include: ["mermaid", "fastdom", "fastdom/extensions/fastdom-promised", "strictdom"],
			},
		},
	}),
);
