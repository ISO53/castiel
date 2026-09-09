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
			<p class="hero-eyebrow">
				<span class="hero-eyebrow-dot"></span>
				AI-assisted penetration testing harness
			</p>

			<h1 class="hero-title">castiel</h1>

			<p class="hero-tagline">
				Open a workspace, connect a language model of your choice, and work
				together through a chat. You define the target and the scope —
				castiel does the hands-on technical work and keeps every finding
				recorded.
			</p>

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

.hero-shade {
	position: absolute;
	inset: 0;
	/* Gentle darkening where the copy sits + fade into the page background. */
	background:
		radial-gradient(ellipse 70% 55% at 50% 46%, rgba(0, 0, 0, 0.42), transparent 70%),
		linear-gradient(to bottom, transparent 62%, var(--bg) 98%);
}

.hero-content {
	position: relative;
	max-width: 46rem;
	text-align: center;
}

.hero-eyebrow {
	display: inline-flex;
	align-items: center;
	gap: 0.55rem;
	margin: 0 0 1.5rem;
	padding: 0.4rem 0.9rem;
	border: 1px solid var(--border);
	border-radius: 999px;
	background: rgba(0, 0, 0, 0.55);
	color: var(--muted);
	font-size: 0.8rem;
	letter-spacing: 0.08em;
	text-transform: uppercase;
	backdrop-filter: blur(8px);
}

.hero-eyebrow-dot {
	width: 0.5rem;
	height: 0.5rem;
	border-radius: 999px;
	background: var(--accent);
	box-shadow: 0 0 10px var(--accent-soft);
}

.hero-title {
	margin: 0 0 1.25rem;
	font-size: clamp(3.5rem, 11vw, 7.5rem);
	font-weight: 800;
	line-height: 1;
	letter-spacing: -0.04em;
}

.hero-tagline {
	margin: 0 auto 2.5rem;
	max-width: 38rem;
	color: var(--muted);
	font-size: clamp(1rem, 2.2vw, 1.2rem);
	line-height: 1.65;
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
