<script setup>
import MeshGradient from "@/components/MeshGradient.vue";
import heroPreset from "@/studio/hero-preset.json";

// The MeshDoc designed in zoxilsi studio ({ app, version, doc } project file).
const heroDoc = heroPreset.doc;
</script>

<template>
	<section class="hero">
		<div class="hero-glow">
			<MeshGradient :doc="heroDoc" />
			<!-- Keeps the glow from washing out the copy. -->
			<div class="hero-shade"></div>
		</div>

		<div class="hero-content">
			<h1 class="hero-title">castiel</h1>
			<p class="hero-sub">AI assisted penetration testing harness</p>

			<div class="hero-actions">
				<a
					class="hero-button primary"
					href="https://github.com/ISO53/castiel/releases"
					target="_blank"
					rel="noopener"
				>
					Download
				</a>
				<RouterLink class="hero-button ghost" to="/docs">Documentation</RouterLink>
			</div>
		</div>
	</section>
</template>

<style scoped>
.hero {
	position: relative;
	display: grid;
	place-items: center;
	min-height: 100svh;
	padding: 4rem 1.5rem;
	overflow: hidden;
}

.hero-glow {
	position: absolute;
	inset: 0;
}

.hero-content {
	position: relative;
	max-width: 46rem;
	text-align: center;
}

.hero-title {
	margin: 0 0 1.25rem;
	font-size: clamp(3.5rem, 11vw, 7.5rem);
	font-weight: 800;
	line-height: 1;
	letter-spacing: -0.04em;
}

.hero-sub {
	margin: 0 auto 2.5rem;
	color: var(--muted);
	font-size: clamp(1rem, 2.2vw, 1.2rem);
	font-weight: 400;
}

.hero-actions {
	display: flex;
	justify-content: center;
	gap: 0.75rem;
	flex-wrap: wrap;
}

.hero-button {
	display: inline-block;
	padding: 0.75rem 1.6rem;
	border-radius: 8px;
	font-size: 0.95rem;
	font-weight: 600;
	transition:
		background-color 0.15s ease,
		border-color 0.15s ease,
		color 0.15s ease;
}

.hero-button.primary {
	background: var(--fg);
	color: var(--bg);
}

.hero-button.primary:hover {
	background: #ffffff;
}

.hero-button.ghost {
	border: 1px solid var(--border);
	color: var(--fg);
}

.hero-button.ghost:hover {
	border-color: var(--fg);
}
</style>
