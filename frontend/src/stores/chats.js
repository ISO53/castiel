import { defineStore } from "pinia";

const API_BASE_URL = `${window.location.origin}/api/chats`;

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

export const useChatsStore = defineStore("chats", {
	state: () => ({
		chats: [],
		activeChatId: null,
		historyOpen: false,
		selectedSession: null,
		loading: false,
	}),
	actions: {
		toggleHistory() {
			this.historyOpen = !this.historyOpen;
			if (this.historyOpen) {
				this.fetchList().catch(() => {});
			}
		},
		async fetchList() {
			this.loading = true;
			try {
				const response = await fetch(API_BASE_URL);
				if (!response.ok) {
					throw new Error(await readError(response));
				}
				this.chats = await response.json();
				return this.chats;
			} finally {
				this.loading = false;
			}
		},
		async loadChat(id) {
			const response = await fetch(`${API_BASE_URL}/${encodeURIComponent(id)}`);
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			const session = await response.json();
			this.activeChatId = session.id;
			this.selectedSession = session;
			return session;
		},
		async selectChat(id) {
			const session = await this.loadChat(id);
			this.historyOpen = false;
			return session;
		},
		async deleteChat(id) {
			const response = await fetch(`${API_BASE_URL}/${encodeURIComponent(id)}`, {
				method: "DELETE",
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			this.chats = this.chats.filter((c) => c.id !== id);
			if (this.activeChatId === id) {
				this.activeChatId = null;
			}
		},
	},
});
