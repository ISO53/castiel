import { defineStore } from "pinia";
import { DEFAULT_SETTINGS_ITEM_ID } from "@/settings/registry";

const LAST_ITEM_KEY = "castiel:settingsItem";

/** How long the "Saved" confirmation stays up. */
const FLASH_MS = 1800;

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
		/** Monotonic counter bumped on every successful save; drives the flash. */
		savedAt: 0,
		/** True while the "Saved" confirmation is on screen. */
		savedVisible: false,
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
		/**
		 * Confirms a save. Settings persist the moment they change, so this is
		 * the only feedback the user gets that anything happened. Each new save
		 * restarts the timer, so rapid changes keep the popup up.
		 */
		flashSaved() {
			this.savedAt += 1;
			this.savedVisible = true;
			clearTimeout(this._flashTimer);
			this._flashTimer = setTimeout(() => {
				this.savedVisible = false;
			}, FLASH_MS);
		},
	},
});
