import { defineStore } from "pinia";
import { computed, ref } from "vue";

export const useKaliToolsStore = defineStore("kaliTools", () => {
	const tools = ref([]);
	const categories = ref([]);
	const loading = ref(false);
	const launching = ref(false);
	const selectedIds = ref(new Set());
	const selectedToolsById = ref(new Map());
	const selectedTools = computed(() =>
		Array.from(selectedIds.value, (id) => selectedToolsById.value.get(id)).filter(Boolean)
	);
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
				const fetchedTools = await res.json();
				tools.value = fetchedTools;

				// Refresh metadata for selected tools that are present in this response.
				const nextSelectedTools = new Map(selectedToolsById.value);
				for (const tool of fetchedTools) {
					if (selectedIds.value.has(tool.id)) nextSelectedTools.set(tool.id, tool);
				}
				selectedToolsById.value = nextSelectedTools;
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
		const nextSelectedTools = new Map(selectedToolsById.value);
		if (next.has(id)) {
			next.delete(id);
			nextSelectedTools.delete(id);
		} else {
			next.add(id);
			const tool = tools.value.find((candidate) => candidate.id === id);
			if (tool) nextSelectedTools.set(id, tool);
		}
		selectedIds.value = next;
		selectedToolsById.value = nextSelectedTools;
	}

	function isSelected(id) {
		return selectedIds.value.has(id);
	}

	function clearSelection() {
		selectedIds.value = new Set();
		selectedToolsById.value = new Map();
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
		selectedTools,
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
