<script setup>
// Hover visual for the "Structured findings" card: a mini findings graph
// (5 nodes, 5 edges, one accent node). Static base is fully drawn with faint
// strokes; hover replays the edge stroke-draw and pops the nodes in.
</script>

<template>
	<svg class="ffind" viewBox="0 0 160 120" width="160" height="120" aria-hidden="true">
		<g class="ffind-edges">
			<line class="ffind-edge" x1="80" y1="22" x2="44" y2="66" pathLength="1" />
			<line class="ffind-edge" x1="80" y1="22" x2="116" y2="66" pathLength="1" />
			<line class="ffind-edge" x1="44" y1="66" x2="26" y2="104" pathLength="1" />
			<line class="ffind-edge" x1="116" y1="66" x2="94" y2="104" pathLength="1" />
			<line class="ffind-edge" x1="44" y1="66" x2="116" y2="66" pathLength="1" />
		</g>
		<g class="ffind-nodes">
			<circle class="ffind-node" cx="80" cy="22" r="7" />
			<circle class="ffind-node" cx="44" cy="66" r="7" />
			<circle class="ffind-node accent" cx="116" cy="66" r="7" />
			<circle class="ffind-node" cx="26" cy="104" r="7" />
			<circle class="ffind-node" cx="94" cy="104" r="7" />
		</g>
	</svg>
</template>

<style scoped>
.ffind {
	display: block;
}

.ffind-edge {
	stroke: var(--faint);
	stroke-width: 1;
	opacity: 0.45;
	stroke-dasharray: 1;
	stroke-dashoffset: 0;
}

.ffind-node {
	fill: var(--surface);
	stroke: var(--faint);
	stroke-width: 1;
	transform-box: fill-box;
	transform-origin: center;
}

.ffind-node.accent {
	fill: var(--accent);
	stroke: none;
}

/* Hover only: edges replay the stroke-draw, nodes pop in staggered. */
:global(.feature-card:hover .ffind-edge) {
	animation: ffind-draw 0.9s ease forwards;
}

:global(.feature-card:hover .ffind-edge:nth-of-type(2)) {
	animation-delay: 0.08s;
}

:global(.feature-card:hover .ffind-edge:nth-of-type(3)) {
	animation-delay: 0.16s;
}

:global(.feature-card:hover .ffind-edge:nth-of-type(4)) {
	animation-delay: 0.24s;
}

:global(.feature-card:hover .ffind-edge:nth-of-type(5)) {
	animation-delay: 0.32s;
}

:global(.feature-card:hover .ffind-node) {
	animation: ffind-pop 0.35s ease both;
}

:global(.feature-card:hover .ffind-node:nth-of-type(2)) {
	animation-delay: 0.15s;
}

:global(.feature-card:hover .ffind-node:nth-of-type(3)) {
	animation-delay: 0.25s;
}

:global(.feature-card:hover .ffind-node:nth-of-type(4)) {
	animation-delay: 0.35s;
}

:global(.feature-card:hover .ffind-node:nth-of-type(5)) {
	animation-delay: 0.45s;
}

@keyframes ffind-draw {
	from {
		stroke-dashoffset: 1;
	}

	to {
		stroke-dashoffset: 0;
	}
}

@keyframes ffind-pop {
	from {
		opacity: 0.3;
		transform: scale(0.6);
	}

	to {
		opacity: 1;
		transform: scale(1);
	}
}

@media (prefers-reduced-motion: reduce) {
	.ffind-edge,
	.ffind-node {
		animation: none !important;
	}
}
</style>
