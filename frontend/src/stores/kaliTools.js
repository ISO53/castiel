import { defineStore } from "pinia";
import { ref } from "vue";

export const useKaliToolsStore = defineStore("kaliTools", () => {
	const tools = ref([]);
	const categories = ref([]);
	const loading = ref(false);
	const launching = ref(false);
	const selectedIds = ref(new Set());
	const userNotes = ref("");

	async function fetchCategories() {
		try {
			const res = await fetch("/api/tools/kali/categories");
			if (res.ok) {
				categories.value = await res.json();
			}
		} catch (e) {
			console.error("Failed to fetch Kali categories", e);
		}
	}

	async function fetchTools(query = "", category = "", installedOnly = false) {
		loading.value = true;
		try {
			const params = new URLSearchParams();
			if (query && query.trim()) params.set("query", query.trim());
			if (category && category.trim()) params.set("category", category.trim());
			if (installedOnly) params.set("installedOnly", "true");
			params.set("limit", "150");

			const res = await fetch(`/api/tools/kali?${params.toString()}`);
			if (res.ok) {
				tools.value = await res.json();
			}
		} catch (e) {
			console.error("Failed to fetch Kali tools", e);
		} finally {
			loading.value = false;
		}
	}

	async function refreshInstalled() {
		try {
			await fetch("/api/tools/kali/refresh", { method: "POST" });
			await fetchTools();
		} catch (e) {
			console.error("Failed to refresh installed tools", e);
		}
	}

	function toggleSelect(id) {
		const next = new Set(selectedIds.value);
		if (next.has(id)) {
			next.delete(id);
		} else {
			next.add(id);
		}
		selectedIds.value = next;
	}

	function isSelected(id) {
		return selectedIds.value.has(id);
	}

	function clearSelection() {
		selectedIds.value = new Set();
		userNotes.value = "";
	}

	async function launchTools(toolIds, notes) {
		launching.value = true;
		try {
			const res = await fetch("/api/tools/kali/launch", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({
					toolIds: Array.from(toolIds),
					userNotes: notes,
				}),
			});
			if (!res.ok) {
				const error = await res.json().catch(() => ({}));
				throw new Error(error.error || "Failed to launch tools");
			}
			return await res.json();
		} finally {
			launching.value = false;
		}
	}

	return {
		tools,
		categories,
		loading,
		launching,
		selectedIds,
		userNotes,
		fetchCategories,
		fetchTools,
		refreshInstalled,
		toggleSelect,
		isSelected,
		clearSelection,
		launchTools,
	};
});
