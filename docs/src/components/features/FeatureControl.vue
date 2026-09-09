<script setup>
// Hover visual for the "You stay in control" card: a phase stepper.
// Static base has Planning + Recon filled and Exploit soft-accented; hover
// re-fills the done steps left-to-right and pulses the next step — advancing
// is the human's call.
</script>

<template>
	<div class="fctl" aria-hidden="true">
		<div class="fctl-track">
			<div class="fctl-step done">
				<span class="fctl-dot"></span>
				<span class="fctl-label">Planning</span>
			</div>
			<div class="fctl-link"></div>
			<div class="fctl-step done">
				<span class="fctl-dot"></span>
				<span class="fctl-label">Recon</span>
			</div>
			<div class="fctl-link"></div>
			<div class="fctl-step next">
				<span class="fctl-dot"></span>
				<span class="fctl-label">Exploit</span>
			</div>
			<div class="fctl-link"></div>
			<div class="fctl-step todo">
				<span class="fctl-dot"></span>
				<span class="fctl-label">Wrap-Up</span>
			</div>
		</div>
	</div>
</template>

<style scoped>
.fctl {
	width: 100%;
	max-width: 340px;
}

.fctl-track {
	display: flex;
	align-items: flex-start;
	gap: 0.5rem;
}

.fctl-step {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 0.4rem;
}

.fctl-dot {
	width: 0.625rem;
	height: 0.625rem;
	border: 1px solid var(--border);
	border-radius: 999px;
	background: transparent;
}

.fctl-step.done .fctl-dot {
	border-color: var(--fg);
	background: var(--fg);
}

.fctl-step.next .fctl-dot {
	border-color: color-mix(in srgb, var(--accent) 55%, transparent);
	background: var(--accent-soft);
}

.fctl-label {
	font-size: 0.65rem;
	color: var(--muted);
	white-space: nowrap;
}

.fctl-step.done .fctl-label {
	color: var(--fg);
}

.fctl-link {
	flex: 1;
	height: 1px;
	margin-top: 0.31rem;
	background: var(--border);
}

/* Hover only: done steps re-fill left-to-right, the next step pulses. */
:global(.feature-card:hover .fctl-step.done .fctl-dot) {
	animation: fctl-refill 0.4s ease both;
}

:global(.feature-card:hover .fctl-step:nth-child(3) .fctl-dot) {
	animation-delay: 0.18s;
}

:global(.feature-card:hover .fctl-step.next .fctl-dot) {
	animation: fctl-pulse 1.4s ease-in-out infinite;
}

@keyframes fctl-refill {
	from {
		background: transparent;
	}

	to {
		background: var(--fg);
	}
}

@keyframes fctl-pulse {
	0%,
	100% {
		background: var(--accent-soft);
	}

	50% {
		background: var(--accent);
		box-shadow: 0 0 0 3px var(--accent-soft);
	}
}

@media (prefers-reduced-motion: reduce) {
	.fctl-dot {
		animation: none !important;
	}
}
</style>
