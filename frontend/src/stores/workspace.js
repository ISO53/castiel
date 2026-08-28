import { defineStore } from "pinia";

const API_BASE_URL = `${window.location.origin}/api/workspace`;

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

export const useWorkspaceStore = defineStore("workspace", {
	state: () => ({
		cwd: null,
	}),
	actions: {
		async fetch() {
			const response = await fetch(API_BASE_URL);
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			const data = await response.json();
			this.cwd = data.cwd ?? null;
			return this.cwd;
		},
		async open(cwd) {
			const response = await fetch(API_BASE_URL, {
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ cwd }),
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			const data = await response.json();
			this.cwd = data.cwd ?? null;
			return this.cwd;
		},
		async create(parentPath, name) {
			const response = await fetch(API_BASE_URL, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ parentPath, name }),
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			const data = await response.json();
			this.cwd = data.cwd ?? null;
			return this.cwd;
		},
	},
});
