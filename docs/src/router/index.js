import { createRouter, createWebHistory } from "vue-router";
import HomeView from "@/views/HomeView.vue";

const router = createRouter({
	history: createWebHistory(import.meta.env.BASE_URL),
	routes: [
		{
			path: "/",
			name: "home",
			component: HomeView,
		},
		{
			path: "/docs",
			name: "docs",
			// Lazy-loaded so the docs bundle is only fetched when visited.
			component: () => import("@/views/DocsView.vue"),
		},
	],
	// Scroll to the top on route change, or to the anchor for hash links
	// (e.g. the Install section on the landing page). The fixed navbar's
	// overlap is handled by scroll-margin-top on the target section.
	scrollBehavior(to) {
		if (to.hash) return { el: to.hash, behavior: "smooth" };
		return { top: 0 };
	},
});

export default router;
