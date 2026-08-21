import { defineStore } from "pinia";

/**
 * Shared tab strip state. MenuBar (and anything else) can open a view without
 * owning TabbedView; opening an already-open tab focuses it instead of duplicating.
 */
export const useTabsStore = defineStore("tabs", {
	state: () => ({
		tabs: [{ value: "home", label: "Home", component: "HomeView", closable: true }],
		active: "home",
	}),
	actions: {
		openTab({ value, label, component, closable = true }) {
			const existing = this.tabs.find((tab) => tab.value === value);
			if (existing) {
				this.active = value;
				return;
			}
			this.tabs.push({ value, label, component, closable });
			this.active = value;
		},
		closeTab(value) {
			const index = this.tabs.findIndex((tab) => tab.value === value);
			if (index === -1) return;
			const wasActive = this.active === value;
			this.tabs.splice(index, 1);
			if (wasActive) {
				const next = this.tabs[Math.min(index, this.tabs.length - 1)];
				this.active = next ? next.value : "";
			}
		},
	},
});
