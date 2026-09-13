import { defineStore } from "pinia";

/**
 * Dock visibility and per-dock active views, shared by the Window menu,
 * the bottom DockBar and the App layout.
 */
export const useDocksStore = defineStore("docks", {
	state: () => ({
		left: true,
		right: true,
		bottom: true,
		// Active view per dock; each dock hosts one view at a time.
		leftView: "files",
		rightView: "chat",
		bottomView: "processes",
	}),
	actions: {
		toggle(dock) {
			if (dock in this) this[dock] = !this[dock];
		},
		// Opens the dock and makes `view` its active view.
		openView(dock, view) {
			const key = `${dock}View`;
			if (key in this) this[key] = view;
			this[dock] = true;
		},

		// Selecting the view that is already active collapses its dock; selecting it again reopens it.
		toggleView(dock, view) {
			const key = `${dock}View`;
			if (this[dock] && this[key] === view) {
				this[dock] = false;
			} else {
				this.openView(dock, view);
			}
		},
	},
});
