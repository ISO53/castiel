<script setup>
// Hover visual for the "Background processes" card: a monospace nmap row with
// a progress bar. Static base sits at ~35%; hover sweeps it to ~85% while a
// shimmer crosses the label (--fg/--muted tokens only).
</script>

<template>
	<div class="fproc" aria-hidden="true">
		<div class="fproc-head">
			<span class="fproc-cmd">nmap -sV 10.10.44.211</span>
			<span class="fproc-state">running</span>
		</div>
		<div class="fproc-bar">
			<div class="fproc-fill"></div>
		</div>
	</div>
</template>

<style scoped>
.fproc {
	width: 100%;
	max-width: 320px;
}

.fproc-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 1rem;
	margin-bottom: 0.625rem;
}

.fproc-cmd {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.75rem;
	color: var(--fg);
}

.fproc-state {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.65rem;
	color: var(--muted);
}

.fproc-bar {
	height: 5px;
	border-radius: 999px;
	background: color-mix(in srgb, var(--muted) 22%, transparent);
	overflow: hidden;
}

.fproc-fill {
	width: 35%;
	height: 100%;
	border-radius: inherit;
	background: var(--fg);
}

/* Hover only: the bar sweeps to ~85% and the label shimmers. */
:global(.feature-card:hover .fproc-fill) {
	animation: fproc-sweep 1.6s ease forwards;
}

:global(.feature-card:hover .fproc-cmd) {
	background: linear-gradient(90deg, var(--muted) 35%, var(--fg) 50%, var(--muted) 65%);
	background-size: 200% 100%;
	-webkit-background-clip: text;
	background-clip: text;
	color: transparent;
	animation: fproc-shimmer 2s linear infinite;
}

@keyframes fproc-sweep {
	to {
		width: 85%;
	}
}

@keyframes fproc-shimmer {
	to {
		background-position: -200% 0;
	}
}

@media (prefers-reduced-motion: reduce) {
	.fproc-fill,
	.fproc-cmd {
		animation: none !important;
	}
}
</style>
