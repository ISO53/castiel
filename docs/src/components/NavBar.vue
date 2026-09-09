<script setup>
import logoUrl from "@/assets/castiel-logo.svg";
</script>

<template>
	<header class="navbar">
		<nav class="nav-inner container">
			<RouterLink to="/" class="nav-brand" aria-label="castiel home">
				<img :src="logoUrl" alt="castiel" class="nav-logo" />
			</RouterLink>

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
					GitHub
				</a>
				<a
					class="nav-button primary"
					href="https://github.com/ISO53/castiel/releases"
					target="_blank"
					rel="noopener"
				>
					Download
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
	gap: 2rem;
	padding: 0.75rem 0;
}

.nav-logo {
	display: block;
	height: 1.1rem;
	width: auto;
}

.nav-links {
	display: flex;
	flex: 1;
	gap: 1.4rem;
}

.nav-link {
	color: var(--muted);
	font-size: 0.9rem;
	transition: color 0.15s ease;
}

.nav-link:hover {
	color: var(--fg);
}

.nav-actions {
	display: flex;
	align-items: center;
	gap: 0.6rem;
}

.nav-button {
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

@media (max-width: 640px) {
	.nav-links {
		display: none;
	}
}
</style>
