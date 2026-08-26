import { defineStore } from "pinia";

/**
 * Dock visibility shared by the Window menu and the App layout.
 * Toggling removes the dock's panel (and its splitter handle) entirely, giving
 * the remaining docks the freed space.
 */
export const useDocksStore = defineStore("docks", {
	state: () => ({
		left: true,
		right: true,
		bottom: false,
	}),
	actions: {
		toggle(dock) {
			if (dock in this) this[dock] = !this[dock];
		},
	},
});