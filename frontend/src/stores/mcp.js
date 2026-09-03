import { defineStore } from "pinia";

const API_BASE_URL = `${window.location.origin}/api/mcp`;

async function readError(response) {
	const body = await response.text();
	try {
		const json = JSON.parse(body);
		return (
			json.message ||
			json.detail ||
			json.error ||
			body ||
			`Request failed with status ${response.status}`
		);
	} catch {
		return body || `Request failed with status ${response.status}`;
	}
}

/**
 * Registered MCP servers and their live connection state. The frontend only
 * displays this; adding and removing servers goes through the settings view.
 */
export const useMcpStore = defineStore("mcp", {
	state: () => ({
		servers: [],
		loaded: false,
		pollTimer: null,
	}),
	actions: {
		async fetch() {
			const response = await fetch(`${API_BASE_URL}/servers`);
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			this.servers = await response.json();
			this.loaded = true;
		},
		startPolling() {
			if (this.pollTimer) return;
			this.fetch();
			this.pollTimer = setInterval(() => {
				this.fetch();
			}, 15000);
		},
		stopPolling() {
			if (this.pollTimer) {
				clearInterval(this.pollTimer);
				this.pollTimer = null;
			}
		},
		/**
		 * Handshakes with an unregistered server; the UI shows the result for confirmation.
		 * @returns {{ id: string, name: string, connected: boolean, tools: string[] }}
		 */
		async probe(config) {
			const response = await fetch(`${API_BASE_URL}/probe`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(config),
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			return response.json();
		},
		async add(config) {
			const response = await fetch(`${API_BASE_URL}/servers`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(config),
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			await this.fetch();
		},
		async remove(id) {
			const response = await fetch(`${API_BASE_URL}/servers/${encodeURIComponent(id)}`, {
				method: "DELETE",
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			await this.fetch();
		},
	},
});
