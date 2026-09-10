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
		</div>

		<div class="hero-inner container">
			<div class="hero-left">
				<h1 class="hero-title">castiel</h1>
				<p class="hero-sub">AI assisted penetration testing harness</p>
			</div>

			<div class="hero-right">
				<p class="hero-desc">
					castiel is an AI-assisted penetration testing harness that pairs
					your workspace with the language model of your choice. You can
					download the latest release from GitHub, or search the docs to
					get started.
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
					<a
						class="hero-button ghost"
						href="https://github.com/ISO53/castiel#readme"
						target="_blank"
						rel="noopener"
					>
						Documentation
					</a>
				</div>
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
	padding: 7rem 0 4rem;
	overflow: hidden;
}

.hero-glow {
	position: absolute;
	inset: 0;
}

.hero-inner {
	position: relative;
	display: grid;
	grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
	gap: 4rem;
	align-items: center;
}

.hero-title {
	margin: 0 0 0.75rem;
	font-size: clamp(3.5rem, 9vw, 6.5rem);
	font-weight: 500;
	line-height: 1;
	letter-spacing: -0.04em;
}

.hero-sub {
	margin: 0;
	color: var(--muted);
	font-size: clamp(1rem, 2vw, 1.15rem);
	font-weight: 400;
}

.hero-desc {
	margin: 0 0 1.75rem;
	color: var(--muted);
	font-size: clamp(0.95rem, 1.6vw, 1.05rem);
	line-height: 1.7;
}

.hero-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 0.75rem;
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

@media (max-width: 860px) {
	.hero-inner {
		grid-template-columns: 1fr;
		gap: 2.5rem;
	}
}
</style>

