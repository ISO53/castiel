<script setup>
// Hover visual for the "Chat-driven engagements" card: a static mini chat
// echoing the app-preview look. All motion is CSS-only, triggered by hovering
// the parent .feature-card, and resets instantly on mouseleave.
</script>

<template>
	<div class="fchat" aria-hidden="true">
		<div class="fchat-panel">
			<p class="fchat-msg user">Start a pentest on 10.0.0.0/24 — recon first, keep everything documented.</p>
			<p class="fchat-msg assistant">Scope recorded in <strong>tasks.json</strong> — sweeping the segment for live hosts now.</p>
			<div class="fchat-tool">
				<span class="fchat-tool-name">http request</span>
				<span class="fchat-tool-badge">completed</span>
			</div>
		</div>
	</div>
</template>

<style scoped>
.fchat {
	/* Mock-app tokens copied verbatim from preview.css. */
	--p-background: oklch(0.145 0 0);
	--p-foreground: oklch(0.985 0 0);
	--p-card: oklch(0.205 0 0);
	--p-primary: oklch(0.444 0.177 26.899);
	--p-primary-foreground: oklch(0.971 0.013 17.38);
	--p-secondary: oklch(0.274 0.006 286.033);
	--p-muted-foreground: oklch(0.708 0 0);
	--p-border: oklch(1 0 0 / 10%);
	width: 100%;
	max-width: 340px;
	font-family: "Inter Variable", ui-sans-serif, system-ui, sans-serif;
}

.fchat-panel {
	display: flex;
	flex-direction: column;
	gap: 0.625rem;
	width: 100%;
	padding: 0.875rem;
	border: 1px solid var(--p-border);
	border-radius: 0.5rem;
	background: var(--p-background);
}

.fchat-msg {
	margin: 0;
	max-width: 88%;
	padding: 0.4rem 0.6rem;
	border-radius: 0.5rem;
	font-size: 0.72rem;
	line-height: 1.625;
}

.fchat-msg.user {
	align-self: flex-end;
	background: var(--p-primary);
	color: var(--p-primary-foreground);
}

.fchat-msg.assistant {
	align-self: flex-start;
	color: var(--p-foreground);
}

.fchat-msg.assistant strong {
	font-weight: 600;
}

.fchat-tool {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 0.5rem;
	align-self: flex-start;
	width: 88%;
	padding: 0.4rem 0.6rem;
	border: 1px solid var(--p-border);
	border-radius: 0.5rem;
	background: var(--p-card);
}

.fchat-tool-name {
	font-size: 0.72rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.fchat-tool-badge {
	padding: 0.05rem 0.45rem;
	border-radius: 999px;
	background: var(--p-secondary);
	font-size: 0.65rem;
	font-weight: 500;
	color: #16a34a;
}

/* Hover only: bubbles pop in sequentially, then the tool chip flashes. */
:global(.feature-card:hover .fchat-msg.user) {
	animation: fchat-pop 0.35s ease both;
}

:global(.feature-card:hover .fchat-msg.assistant) {
	animation: fchat-pop 0.35s ease 0.12s both;
}

:global(.feature-card:hover .fchat-tool) {
	animation:
		fchat-pop 0.35s ease 0.24s both,
		fchat-flash 0.7s ease 0.55s both;
}

@keyframes fchat-pop {
	from {
		opacity: 0;
		transform: scale(0.92) translateY(4px);
	}

	to {
		opacity: 1;
		transform: none;
	}
}

@keyframes fchat-flash {
	0%,
	100% {
		border-color: var(--p-border);
		background: var(--p-card);
	}

	35% {
		border-color: var(--accent);
		background: var(--accent-soft);
	}
}

@media (prefers-reduced-motion: reduce) {
	.fchat-msg.user,
	.fchat-msg.assistant,
	.fchat-tool {
		animation: none !important;
	}
}
</style>
