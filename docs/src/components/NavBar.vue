<script setup>
import { onMounted, ref } from "vue";
import { Star, Download } from "@lucide/vue";
import logoUrl from "@/assets/castiel-logo.svg";
import githubUrl from "@/assets/github.svg";

// Live star count from the GitHub API; falls back to a plain "Stars" label
// if the request fails (offline, rate-limited, etc.).
const starCount = ref(null);

function formatCount(value) {
	return new Intl.NumberFormat("en", {
		notation: "compact",
		maximumFractionDigits: 1,
	}).format(value);
}

onMounted(async () => {
	try {
		const res = await fetch("https://api.github.com/repos/ISO53/castiel");
		if (!res.ok) return;
		const data = await res.json();
		if (typeof data.stargazers_count === "number") {
			starCount.value = data.stargazers_count;
		}
	} catch {
		// Network error — the label stays as the fallback.
	}
});
</script>

<template>
	<header class="navbar">
		<nav class="nav-inner container">
			<div class="nav-left">
				<RouterLink to="/" class="nav-brand" aria-label="castiel home">
					<img :src="logoUrl" alt="castiel" class="nav-logo" />
				</RouterLink>
			</div>

			<div class="nav-links">
				<RouterLink to="/docs" class="nav-link">Docs</RouterLink>
				<a
					class="nav-link"
					href="https://github.com/ISO53/castiel/releases"
					target="_blank"
					rel="noopener"
				>
					Releases
				</a>
			</div>

			<div class="nav-actions">
				<a
					class="nav-button ghost"
					href="https://github.com/ISO53/castiel"
					target="_blank"
					rel="noopener"
				>
					<img :src="githubUrl" alt="" class="nav-github-icon" />
					<Star class="nav-star-icon" aria-hidden="true" />
					<span class="nav-star-count">{{ starCount !== null ? formatCount(starCount) : "Stars" }}</span>
				</a>
				<a
					class="nav-button primary"
					href="https://github.com/ISO53/castiel/releases"
					target="_blank"
					rel="noopener"
				>
					<Download class="nav-download-icon" aria-hidden="true" />
					<span>Download</span>
				</a>
			</div>
		</nav>
	</header>
</template>

<style scoped>
.navbar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 50;
	background: rgba(0, 0, 0, 0.55);
	backdrop-filter: blur(14px) saturate(1.2);
	-webkit-backdrop-filter: blur(14px) saturate(1.2);
}

.nav-inner {
	display: flex;
	align-items: center;
	gap: 1rem;
	padding: 0.75rem 0;
}

/* Three equal columns keep the links exactly centered, whatever the
   logo/button widths are: logo hard-left, links center, actions hard-right. */
.nav-left {
	flex: 1;
}

.nav-links {
	flex: 1;
	display: flex;
	justify-content: center;
	gap: 1.4rem;
}

.nav-actions {
	flex: 1;
	display: flex;
	justify-content: flex-end;
	gap: 0.6rem;
}

.nav-brand {
	display: inline-flex;
	align-items: center;
}

.nav-logo {
	display: block;
	height: 1.1rem;
	width: auto;
	/* Grayscale at rest; reveal the brand color on hover. */
	filter: grayscale(1);
	transition: filter 0.15s ease;
}

.nav-brand:hover .nav-logo {
	filter: grayscale(0);
}

.nav-link {
	color: var(--muted);
	font-size: 0.9rem;
	transition: color 0.15s ease;
}

.nav-link:hover {
	color: var(--fg);
}

.nav-button {
	display: inline-flex;
	align-items: center;
	gap: 0.4rem;
	padding: 0.42rem 0.95rem;
	border-radius: 6px;
	font-size: 0.85rem;
	font-weight: 500;
	transition:
		background-color 0.15s ease,
		border-color 0.15s ease,
		color 0.15s ease;
}

.nav-button.primary {
	background: var(--fg);
	color: var(--bg);
}

.nav-button.primary:hover {
	background: #ffffff;
}

.nav-button.ghost {
	border: 1px solid var(--border);
	color: var(--fg);
}

.nav-button.ghost:hover {
	border-color: var(--fg);
}

.nav-github-icon {
	height: 1.2rem;
	width: auto;
}

.nav-star-icon,
.nav-download-icon {
	width: 0.9rem;
	height: 0.9rem;
}

.nav-star-count {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

@media (max-width: 640px) {
	.nav-links {
		display: none;
	}
}
</style>
