<script setup>
// Hover visual for the "MCP-native" card: castiel connected to obscura, Caido
// and a dashed "any MCP…" node. Static base is connected with faint lines;
// hover pulses the links and adds a soft accent glow to the core.
</script>

<template>
	<svg class="fmcp" viewBox="0 0 160 120" width="160" height="120" aria-hidden="true">
		<g class="fmcp-links">
			<line class="fmcp-link" x1="44" y1="31" x2="62" y2="46" />
			<line class="fmcp-link" x1="116" y1="31" x2="98" y2="46" />
			<line class="fmcp-link" x1="94" y1="70" x2="126" y2="88" />
		</g>

		<g class="fmcp-node">
			<rect x="8" y="14" width="48" height="17" rx="5" />
			<text class="fmcp-label" x="32" y="25.5" text-anchor="middle">obscura</text>
		</g>
		<g class="fmcp-node">
			<rect x="104" y="14" width="48" height="17" rx="5" />
			<text class="fmcp-label" x="128" y="25.5" text-anchor="middle">Caido</text>
		</g>
		<g class="fmcp-node dashed">
			<rect x="100" y="88" width="52" height="17" rx="5" />
			<text class="fmcp-label" x="126" y="99.5" text-anchor="middle">any MCP…</text>
		</g>

		<g class="fmcp-core">
			<rect x="53" y="46" width="54" height="24" rx="6" />
			<text class="fmcp-core-label" x="80" y="61.5" text-anchor="middle">castiel</text>
		</g>
	</svg>
</template>

<style scoped>
.fmcp {
	display: block;
}

.fmcp-link {
	stroke: var(--faint);
	stroke-width: 1;
	opacity: 0.35;
}

.fmcp-node rect {
	fill: var(--surface);
	stroke: var(--faint);
	stroke-width: 1;
}

.fmcp-node.dashed rect {
	stroke-dasharray: 3 2;
}

.fmcp-label {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 7px;
	fill: var(--muted);
}

.fmcp-core rect {
	fill: var(--surface);
	stroke: var(--faint);
	stroke-width: 1;
	transition:
		stroke 0.15s ease,
		filter 0.15s ease;
}

.fmcp-core-label {
	font-size: 8px;
	font-weight: 500;
	fill: var(--fg);
}

/* Hover only: links pulse in a staggered loop, the core glows accent. */
:global(.feature-card:hover .fmcp-link) {
	animation: fmcp-pulse 1.6s ease-in-out infinite;
}

:global(.feature-card:hover .fmcp-link:nth-of-type(2)) {
	animation-delay: 0.3s;
}

:global(.feature-card:hover .fmcp-link:nth-of-type(3)) {
	animation-delay: 0.6s;
}

:global(.feature-card:hover .fmcp-core rect) {
	stroke: color-mix(in srgb, var(--accent) 45%, var(--faint));
	filter: drop-shadow(0 0 7px color-mix(in srgb, var(--accent) 40%, transparent));
}

@keyframes fmcp-pulse {
	0%,
	100% {
		opacity: 0.25;
	}

	50% {
		opacity: 1;
	}
}

@media (prefers-reduced-motion: reduce) {
	.fmcp-link {
		animation: none !important;
	}
}
</style>
