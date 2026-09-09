<script setup>
// Hover visual for the "Bring your own model" card: three monospace provider
// rows. Static base highlights OpenRouter; hover cycles the highlight down the
// list via negative animation-delays (CSS-only, resets on mouseleave).
</script>

<template>
	<div class="fmodels" aria-hidden="true">
		<div class="fmodels-row">llama.cpp</div>
		<div class="fmodels-row">Ollama</div>
		<div class="fmodels-row lit">OpenRouter</div>
		<div class="fmodels-row">Cline</div>
	</div>
</template>

<style scoped>
.fmodels {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	width: 100%;
	max-width: 300px;
}

.fmodels-row {
	padding: 0.5rem 0.75rem;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.8rem;
	color: var(--muted);
}

.fmodels-row.lit {
	border-color: color-mix(in srgb, var(--accent) 35%, transparent);
	background: var(--accent-soft);
	color: var(--fg);
}

/* Hover only: the highlight cycles down the rows, ~2.4s stepped, infinite.
   Negative delays phase-shift each row into its quarter of the cycle. */
:global(.feature-card:hover .fmodels-row) {
	animation: fmodels-cycle 2.4s infinite;
}

:global(.feature-card:hover .fmodels-row:nth-child(1)) {
	animation-delay: 0s;
}

:global(.feature-card:hover .fmodels-row:nth-child(2)) {
	animation-delay: -1.8s;
}

:global(.feature-card:hover .fmodels-row:nth-child(3)) {
	animation-delay: -1.2s;
}

:global(.feature-card:hover .fmodels-row:nth-child(4)) {
	animation-delay: -0.6s;
}

@keyframes fmodels-cycle {
	0%,
	25% {
		border-color: color-mix(in srgb, var(--accent) 35%, transparent);
		background: var(--accent-soft);
		color: var(--fg);
	}

	26%,
	100% {
		border-color: var(--border);
		background: transparent;
		color: var(--muted);
	}
}

@media (prefers-reduced-motion: reduce) {
	.fmodels-row {
		animation: none !important;
	}
}
</style>
