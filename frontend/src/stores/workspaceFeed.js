import { defineStore } from "pinia";

const API_BASE_URL = `${window.location.origin}/api/workspace`;

/**
 * Server-sent file-change pings for the active workspace. The payload is the
 * changed file's name, or "*" when the writer is unknown (shell commands).
 */
export const useWorkspaceFeedStore = defineStore("workspaceFeed", {
	state: () => ({
		source: null,
		listeners: new Set(),
	}),
	actions: {
		start() {
			if (this.source) return;
			const source = new EventSource(`${API_BASE_URL}/events`);
			source.onmessage = (event) => {
				for (const listener of this.listeners) listener(event.data);
			};
			this.source = source;
		},
		/** Registers a change listener; returns its unsubscribe function. */
		subscribe(listener) {
			this.start();
			this.listeners.add(listener);
			return () => this.listeners.delete(listener);
		},
	},
});