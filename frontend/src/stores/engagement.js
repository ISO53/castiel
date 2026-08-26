import { defineStore } from "pinia";

const API_BASE_URL = "http://localhost:8081/api/engagement";

/** Display metadata for the five engagement phases, in order. */
export const ENGAGEMENT_PHASES = [
	{ value: 1, short: "Planning", label: "Planning & Pre-Engagement" },
	{ value: 2, short: "Recon", label: "Reconnaissance" },
	{ value: 3, short: "Scanning", label: "Scanning & Vulnerability Assessment" },
	{ value: 4, short: "Exploitation", label: "Exploitation" },
	{ value: 5, short: "Wrap-Up", label: "Wrap-Up" },
];

async function readError(response) {
	const body = await response.text();
	try {
		const json = JSON.parse(body);
		return json.message || json.detail || json.error || body || `Request failed with status ${response.status}`;
	} catch {
		return body || `Request failed with status ${response.status}`;
	}
}

export const useEngagementStore = defineStore("engagement", {
	state: () => ({
		phase: null,
		totalPhases: 5,
		loading: false,
		error: "",
	}),
	getters: {
		hasPhase: (state) => typeof state.phase === "number",
	},
	actions: {
		async fetch() {
			this.loading = true;
			this.error = "";
			try {
				const response = await fetch(API_BASE_URL);
				if (!response.ok) throw new Error(await readError(response));
				const data = await response.json();
				this.phase = data.phase ?? null;
				this.totalPhases = data.totalPhases ?? 5;
			} catch (err) {
				this.phase = null;
				this.error = err.message;
			} finally {
				this.loading = false;
			}
		},
		async setPhase(value) {
			const response = await fetch(`${API_BASE_URL}/phase`, {
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ phase: value }),
			});
			if (!response.ok) throw new Error(await readError(response));
			const data = await response.json();
			this.phase = data.phase ?? value;
		},
	},
});