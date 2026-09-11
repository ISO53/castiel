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
 * MCP servers declared in the mcp.json config file, with their live connection
 * state. Servers are added and removed by editing the file; the harness watches it.
 */
export const useMcpStore = defineStore("mcp", {
	state: () => ({
		servers: [],
		loaded: false,
		source: null,
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
		/** Listens to the harness SSE feed; each reachability ping refetches the list. */
		startFeed() {
			if (this.source) return;
			this.source = new EventSource(`${API_BASE_URL}/events`);
			this.source.onopen = () => this.fetch();
			this.source.onmessage = () => this.fetch();
		},
		/** Config file path and last parse error, for the settings view. */
		async fetchConfig() {
			const response = await fetch(`${API_BASE_URL}/config`);
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			return response.json();
		},
		/** Retries every unreachable server and adopts the refreshed statuses. */
		async reconnectDisconnected() {
			const response = await fetch(`${API_BASE_URL}/servers/reconnect`, {
				method: "POST",
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			this.servers = await response.json();
		},
		/** Toggles one server; the flag is persisted in mcp.json and applies live. */
		async setEnabled(id, enabled) {
			const response = await fetch(
				`${API_BASE_URL}/servers/${encodeURIComponent(id)}/enabled`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ enabled }),
				},
			);
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			this.servers = await response.json();
		},
	},
});
