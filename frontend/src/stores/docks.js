import { defineStore } from "pinia";

/**
 * Dock visibility shared by the Window menu and the App layout.
 */
export const useDocksStore = defineStore("docks", {
	state: () => ({
		left: true,
		right: true,
		bottom: true,
	}),
	actions: {
		toggle(dock) {
			if (dock in this) this[dock] = !this[dock];
		},
	},
});
