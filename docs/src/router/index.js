import { nextTick } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import HomeView from "@/views/HomeView.vue";

// Matches the scroll-margin-top fallback on the target sections.
const NAVBAR_OFFSET = 80;

const router = createRouter({
	history: createWebHistory(import.meta.env.BASE_URL),
	routes: [
		{
			path: "/",
			name: "home",
			component: HomeView,
		},
	],
	// Scroll to the top on route change. Hash links (e.g. the Install section)
	// center the target section vertically in the viewport; when the section is
	// taller than the viewport the offset clamps back to the navbar margin.
	scrollBehavior(to) {
		if (!to.hash) return { top: 0 };
		return nextTick().then(() => {
			const el = document.querySelector(to.hash);
			if (!el) return { top: 0 };
			const rect = el.getBoundingClientRect();
			const slack = (window.innerHeight - rect.height) / 2;
			const offset = Math.max(NAVBAR_OFFSET, slack);
			return { top: Math.max(0, window.scrollY + rect.top - offset), behavior: "smooth" };
		});
	},
});

export default router;
