<script setup>
import logoUrl from "@/assets/castiel-logo.svg";

const year = new Date().getFullYear();
</script>

<template>
	<footer class="footer">
		<div class="footer-inner container">
			<img :src="logoUrl" alt="" class="footer-logo" aria-hidden="true" />

			<p class="footer-title">castiel</p>

			<div class="footer-group website">
				<p class="footer-heading">Website</p>
				<a
					class="footer-link"
					href="https://github.com/ISO53/castiel#readme"
					target="_blank"
					rel="noopener"
				>
					Docs
				</a>
				<RouterLink to="/#install" class="footer-link">Install</RouterLink>
			</div>

			<div class="footer-group github">
				<p class="footer-heading">GitHub</p>
				<a
					class="footer-link"
					href="https://github.com/ISO53/castiel"
					target="_blank"
					rel="noopener"
				>
					Repo
				</a>
				<a
					class="footer-link"
					href="https://github.com/ISO53/castiel/issues"
					target="_blank"
					rel="noopener"
				>
					Issues
				</a>
				<a
					class="footer-link"
					href="https://github.com/ISO53/castiel/releases"
					target="_blank"
					rel="noopener"
				>
					Releases
				</a>
				<a
					class="footer-link"
					href="https://github.com/ISO53/castiel/blob/main/LICENSE"
					target="_blank"
					rel="noopener"
				>
					License
				</a>
			</div>

			<p class="footer-copy">© {{ year }} iso53 — GPL-3.0</p>
		</div>
	</footer>
</template>

<style scoped>
.footer {
	min-height: 40vh;
	border-top: 1px solid var(--border);
	overflow: hidden;
}

/* Three evenly distributed columns: wing | website | github,
   with the wordmark and copyright spanning the right two. */
.footer-inner {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	gap: 2rem;
	align-items: center;
	min-height: 40vh;
	padding-block: 4rem;
}

/* Huge, grayscale, faded — purely decorative, no hover, no transitions. */
.footer-logo {
	grid-column: 1;
	grid-row: 1 / 4;
	justify-self: center;
	display: block;
	height: clamp(14rem, 34vh, 26rem);
	max-width: 100%;
	width: auto;
	filter: grayscale(1);
	opacity: 0.22;
}

.footer-title {
	grid-column: 2 / 4;
	align-self: end;
	margin: 0;
	font-size: clamp(3.5rem, 9vw, 6rem);
	font-weight: 500;
	line-height: 1;
	letter-spacing: -0.04em;
}

.footer-group.website {
	grid-column: 2;
	grid-row: 2;
	align-self: start;
}

.footer-group.github {
	grid-column: 3;
	grid-row: 2;
	align-self: start;
}

.footer-heading {
	margin: 0 0 0.9rem;
	font-size: 0.7rem;
	font-weight: 500;
	letter-spacing: 0.08em;
	text-transform: uppercase;
	color: var(--faint);
}

.footer-group {
	display: flex;
	flex-direction: column;
	gap: 0.55rem;
}

.footer-link {
	width: fit-content;
	color: var(--muted);
	font-size: 0.9rem;
	transition: color 0.15s ease;
}

.footer-link:hover {
	color: var(--fg);
}

.footer-copy {
	grid-column: 2 / 4;
	align-self: start;
	margin: 0;
	font-size: 0.8rem;
	color: var(--faint);
}

@media (max-width: 860px) {
	.footer-inner {
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 2rem;
		padding-block: 3rem;
	}

	.footer-logo {
		grid-column: 1 / -1;
		grid-row: auto;
		justify-self: start;
		height: 11rem;
	}

	.footer-title {
		grid-column: 1 / -1;
		grid-row: auto;
		align-self: auto;
	}

	.footer-group.website {
		grid-column: 1;
		grid-row: auto;
	}

	.footer-group.github {
		grid-column: 2;
		grid-row: auto;
	}

	.footer-copy {
		grid-column: 1 / -1;
		grid-row: auto;
		margin-top: 1rem;
	}
}
</style>
