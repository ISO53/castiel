import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vueDevTools from "vite-plugin-vue-devtools";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig({
	plugins: [vue(), vueDevTools(), tailwindcss()],
	resolve: {
		alias: {
			"@": fileURLToPath(new URL("./src", import.meta.url)),
		},
	},
	server: {
		// Forward API calls to the backend running via `mvn spring-boot:run`.
		// changeOrigin must stay false: the browser's Origin header names the dev
		// server, and the backend's Host/Origin gate needs the forwarded Host
		// header to match it. Rewriting Host to the backend makes every proxied
		// request look cross-origin.
		proxy: {
			"/api": {
				target: "http://localhost:8081",
				changeOrigin: false,
			},
		},
	},
});
