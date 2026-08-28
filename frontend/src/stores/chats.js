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
		async saveChat(session) {
			if (!session || !session.id) return;
			const response = await fetch(`${API_BASE_URL}/${encodeURIComponent(session.id)}`, {
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(session),
			});
			if (!response.ok) {
				throw new Error(await readError(response));
			}
			const saved = await response.json();
			this.activeChatId = saved.id;

			// Update in-memory summary list
			const index = this.chats.findIndex((c) => c.id === saved.id);
			const summary = {
				id: saved.id,
				title: saved.title,
				providerId: saved.providerId,
				modelName: saved.modelName,
				reasoningEffort: saved.reasoningEffort,
				createdAt: saved.createdAt,
				updatedAt: saved.updatedAt,
				messageCount: saved.messages ? saved.messages.length : 0,
			};
			if (index !== -1) {
				this.chats.splice(index, 1, summary);
			} else {
				this.chats.unshift(summary);
			}
			// Keep sorted by updatedAt desc
			this.chats.sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0));
			return saved;
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
