import { defineStore } from "pinia";

const API = `${window.location.origin}/api/processes`;
const POLL_INTERVAL_MS = 1200;

/**
 * Live view of the harness process registry for the bottom dock.
 *
 * Polls list + delta output of the selected row; cursors per id let the
 * appended view continue seamlessly across polls.
 */
export const useProcessesStore = defineStore("processes", {
	state: () => ({
		rows: [],
		selectedId: null,
		texts: {},
		cursors: {},
		timer: null,
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
		startPolling() {
			if (this.timer) return;
			this.fetchList();
			this.timer = setInterval(() => {
				this.fetchList();
				if (this.selectedId) this.pollOutput();
			}, POLL_INTERVAL_MS);
		},
		stopPolling() {
			clearInterval(this.timer);
			this.timer = null;
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
			this.pollOutput();
		},
		async pollOutput() {
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
		clearView() {
			if (!this.selectedId) return;
			this.texts[this.selectedId] = "";
			this.cursors[this.selectedId] = 0;
		},
		reset() {
			this.stopPolling();
			this.rows = [];
			this.selectedId = null;
			this.texts = {};
			this.cursors = {};
			this.startPolling();
		},
	},
});
