import { defineStore } from "pinia";

const API = `${window.location.origin}/api/agents`;

/**
 * Live view of the sub-agent run registry for the bottom dock.
 *
 * Mirrors the processes store: the list refreshes on server-sent change pings from
 * /api/agents/events; the selected run's transcript is streamed in via cursor-based
 * delta reads.
 */
export const useAgentsStore = defineStore("agents", {
	state: () => ({
		rows: [],
		selectedId: null,
		texts: {},
		cursors: {},
		source: null,
	}),
	getters: {
		selected() {
			return this.rows.find((row) => row.id === this.selectedId) || null;
		},
		selectedText() {
			return this.texts[this.selectedId] ?? "";
		},
		runningCount() {
			return this.rows.filter((row) => row.state === "RUNNING" || row.state === "QUEUED").length;
		},
	},
	actions: {
		async fetchList() {
			try {
				const response = await fetch(API);
				if (!response.ok) return;
				this.rows = await response.json();
				if (this.selectedId && !this.rows.some((row) => row.id === this.selectedId)) {
					this.selectedId = null;
				}
			} catch {
				// Harness unreachable; keep last known rows.
			}
		},
		/** Listens to the harness SSE feed; each change ping refetches the list. */
		startFeed() {
			if (this.source) return;
			this.fetchList();
			this.source = new EventSource(`${API}/events`);
			this.source.onmessage = () => {
				this.fetchList();
				if (this.selectedId) this.fetchTranscriptDelta();
			};
		},
		stopFeed() {
			this.source?.close();
			this.source = null;
		},
		/**
		 * Toggles row selection: clicking the selected run again closes the transcript
		 * pane and restores the full table.
		 */
		select(id) {
			if (this.selectedId === id) {
				this.selectedId = null;
				return;
			}
			this.selectedId = id;
			if (!(id in this.texts)) this.texts[id] = "";
			this.fetchTranscriptDelta();
		},
		/** Pulls new transcript text since the last read using a cursor-based delta. */
		async fetchTranscriptDelta() {
			const id = this.selectedId;
			if (!id) return;
			try {
				const cursor = this.cursors[id] ?? 0;
				const response = await fetch(
					`${API}/${encodeURIComponent(id)}/output?cursor=${cursor}&max=16384`,
				);
				if (!response.ok) return;
				const data = await response.json();
				this.cursors[data.id] = data.cursor;
				if (data.text) this.texts[data.id] = (this.texts[data.id] ?? "") + data.text;
			} catch {
				// Transient; next tick retries.
			}
		},
		async remove(id) {
			await fetch(`${API}/${encodeURIComponent(id)}`, { method: "DELETE" }).catch(() => {});
			await this.fetchList();
			if (this.selectedId === id && !this.rows.some((row) => row.id === id)) {
				this.selectedId = null;
			}
		},
		/**
		 * Cancels a live sub-agent run on the user's behalf. The row and its transcript
		 * stay in the registry; only remove() takes a row off the list. A 409 means the
		 * run already finished; the refetch below corrects the row.
		 */
		async cancel(id) {
			const response = await fetch(`${API}/${encodeURIComponent(id)}/cancel`, { method: "POST" }).catch(() => {
				throw new Error("Harness is unreachable; try again shortly.");
			});
			if (!response.ok && response.status !== 409) {
				let message = `Could not cancel the run (HTTP ${response.status}).`;
				try {
					const body = await response.json();
					if (body?.error) message = body.error;
				} catch {
					// Non-JSON error body; keep the generic message.
				}
				throw new Error(message);
			}
			await this.fetchList();
		},
		clearView() {
			if (!this.selectedId) return;
			this.texts[this.selectedId] = "";
			this.cursors[this.selectedId] = 0;
		},
		reset() {
			this.stopFeed();
			this.rows = [];
			this.selectedId = null;
			this.texts = {};
			this.cursors = {};
			this.startFeed();
		},
	},
});
