import { defineStore } from "pinia";
import { ref } from "vue";

/**
 * MITRE ATT&CK catalog behind the read-only explore dialog. The agent-facing
 * `search_attack` / `attack_detail` tools are served by the same backend catalog.
 */
export const useAttackStore = defineStore("attack", () => {
	const techniques = ref([]);
	const tactics = ref([]);
	const loading = ref(false);
	const detailLoading = ref(false);
	const selected = ref(null);

	async function fetchTactics() {
		try {
			const res = await fetch("/api/attack/tactics");
			if (res.ok) tactics.value = await res.json();
		} catch (e) {
			console.error("Failed to fetch ATT&CK tactics", e);
		}
	}

	async function fetchTechniques(query = "", tactic = "") {
		loading.value = true;
		try {
			const params = new URLSearchParams();
			if (query && query.trim()) params.set("query", query.trim());
			if (tactic && tactic.trim()) params.set("tactic", tactic.trim());
			const res = await fetch(`/api/attack?${params.toString()}`);
			if (res.ok) techniques.value = await res.json();
		} catch (e) {
			console.error("Failed to fetch ATT&CK techniques", e);
		} finally {
			loading.value = false;
		}
	}

	async function fetchDetail(id) {
		detailLoading.value = true;
		try {
			const res = await fetch(`/api/attack/techniques/${encodeURIComponent(id)}`);
			if (res.ok) selected.value = await res.json();
		} catch (e) {
			console.error("Failed to fetch ATT&CK technique detail", e);
		} finally {
			detailLoading.value = false;
		}
	}

	function select(id) {
		if (selected.value?.id === id) return;
		selected.value = null;
		fetchDetail(id);
	}

	return {
		techniques,
		tactics,
		loading,
		detailLoading,
		selected,
		fetchTactics,
		fetchTechniques,
		fetchDetail,
		select,
	};
});