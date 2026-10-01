import { defineStore } from "pinia";
import { DEFAULT_SETTINGS_ITEM_ID } from "@/settings/registry";

const LAST_ITEM_KEY = "castiel:settingsItem";

/**
 * Open/closed state for the settings dialog, plus the leaf it is showing.
 * Every entry point (menu, onboarding, deep links) drives this store, so the
 * dialog is mounted once in App.vue.
 */
export const useSettingsUiStore = defineStore("settingsUi", {
	state: () => ({
		open: false,
		activeId: localStorage.getItem(LAST_ITEM_KEY) ?? DEFAULT_SETTINGS_ITEM_ID,
		query: "",
	}),
	getters: {
		/** True once a search term is typed; the sidebar then shows results only. */
		searching(state) {
			return state.query.trim().length > 0;
		},
	},
	actions: {
		/** Opens the dialog, optionally jumping straight to a leaf. */
		show(id) {
			if (id) this.select(id);
			this.open = true;
		},
		close() {
			this.open = false;
			this.query = "";
		},
		/** Selects a leaf and remembers it, so the dialog reopens where you left. */
		select(id) {
			this.activeId = id;
			try {
				localStorage.setItem(LAST_ITEM_KEY, id);
			} catch {
				// Private mode or a full quota; the leaf simply is not remembered.
			}
		},
	},
});
