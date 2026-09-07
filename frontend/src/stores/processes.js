import { defineStore } from "pinia";

const API = `${window.location.origin}/api/processes`;

/**
 * Live view of the harness process registry for the bottom dock.
 *
 * The list refreshes on server-sent change pings from /api/processes/events;
 * the selected row's output stays cursor-based via delta reads.
 */
export const useProcessesStore = defineStore("processes", {
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
			return this.rows.filter((row) => row.state === "RUNNING").length;
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
				if (this.selectedId) this.fetchOutputDelta();
			};
		},
		stopFeed() {
			this.source?.close();
			this.source = null;
		},
		/**
		 * Toggles row selection: clicking the selected row again closes the
		 * terminal pane and restores the full table.
		 */
		select(id) {
			if (this.selectedId === id) {
				this.selectedId = null;
				return;
			}
			this.selectedId = id;
			if (!(id in this.texts)) this.texts[id] = "";
			this.fetchOutputDelta();
		},
		/** Pulls new process output since the last read using a cursor-based delta. */
		async fetchOutputDelta() {
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
		 * Kills a running process on the user's behalf. The row and its output stay in
		 * the registry (the kill marker streams into the terminal pane); only remove()
		 * takes a row off the list. A 409 means the process already exited on its own;
		 * the refetch below corrects the row.
		 */
		async kill(id) {
			const response = await fetch(`${API}/${encodeURIComponent(id)}/kill`, { method: "POST" }).catch(() => {
				throw new Error("Harness is unreachable; try again shortly.");
			});
			if (!response.ok && response.status !== 409) {
				let message = `Could not kill the process (HTTP ${response.status}).`;
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
		/**
		 * Starts a process on the user's behalf from the bottom-dock dialog; the
		 * description is optional. Resolves with the new row and auto-selects it so
		 * the terminal pane opens on it; rejects with a user-facing error message.
		 */
		async startProcess(command, description) {
			const response = await fetch(API, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ command, description }),
			}).catch(() => {
				throw new Error("Harness is unreachable; try again shortly.");
			});
			if (!response.ok) {
				let message = `Could not start the process (HTTP ${response.status}).`;
				try {
					const body = await response.json();
					if (body?.error) message = body.error;
				} catch {
					// Non-JSON error body; keep the generic message.
				}
				throw new Error(message);
			}
			const row = await response.json();
			await this.fetchList();
			if (this.rows.some((r) => r.id === row.id)) this.select(row.id);
			return row;
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
