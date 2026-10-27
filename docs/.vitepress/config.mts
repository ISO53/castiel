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
	themeConfig: {
		nav: [{ text: "GitHub", link: "https://github.com/ISO53/castiel" }],
		socialLinks: [{ icon: "github", link: "https://github.com/ISO53/castiel" }],
		// Local full-text search (MiniSearch) — no external service, works on Pages.
		search: {
			provider: "local",
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
