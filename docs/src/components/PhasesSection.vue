<script setup>
// The four engagement phases, straight from the product: names and copy are
// condensed from EngagementService.PHASE_NAMES / PHASE_SUMMARIES so the site
// stays consistent with what the harness enforces. Placed right after the
// "Why castiel" bento.
const phases = [
	{
		title: "Planning & Pre-Engagement",
		copy:
			"In this phase, castiel talks scope and rules of engagement with you while gathering passive OSINT. No traffic reaches the target yet.",
		icon: "shield",
	},
	{
		title: "Reconnaissance",
		copy:
			"In this phase, castiel freely interacts with in-scope targets to map the attack surface into network, web and identity documents.",
		icon: "globe",
	},
	{
		title: "Scanning & Vulnerability Assessment",
		copy:
			"In this phase, castiel probes in-scope targets for weaknesses and scores every finding with dual CVSS vectors before it counts.",
		icon: "radar",
	},
	{
		title: "Exploitation",
		copy:
			"In this phase, castiel exploits the vulnerabilities confirmed so far and reports every attempt and its result back to you.",
		icon: "zap",
	},
];
</script>

<template>
	<section class="phases">
		<div class="phases-inner container">
			<ul class="phases-grid">
				<li v-for="phase in phases" :key="phase.title" class="phase">
					<svg class="phase-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
						stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
						<template v-if="phase.icon === 'shield'">
							<!-- Planning: shield; the check re-draws itself on hover -->
							<path class="icon-shield" d="M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6l7-3z" />
							<path class="icon-check" d="M9 12l2 2 4-4" />
						</template>
						<template v-else-if="phase.icon === 'globe'">
							<!-- Recon: globe with a dashed orbit ring that spins on hover -->
							<circle cx="12" cy="12" r="9" />
							<path class="icon-meridians" d="M3 12h18M12 3c2.5 2.6 3.9 5.7 3.9 9S14.5 18.4 12 21c-2.5-2.6-3.9-5.7-3.9-9S9.5 5.6 12 3z" />
							<g class="icon-orbit">
								<circle cx="12" cy="12" r="11.2" stroke-dasharray="2 5.5" />
							</g>
						</template>
						<template v-else-if="phase.icon === 'radar'">
							<!-- Scanning: radar dish; the beam sweeps and the blip pulses on hover -->
							<circle cx="12" cy="12" r="9" />
							<circle cx="12" cy="12" r="4.5" />
							<circle class="icon-blip" cx="15.8" cy="9.5" r="1" fill="currentColor" stroke="none" />
							<g class="icon-sweep">
								<path d="M12 12l7.2-4.1" />
								<path d="M19.2 7.9a8.6 8.6 0 0 0-2.2-2.3" stroke-dasharray="1 2.5" />
							</g>
						</template>
						<template v-else>
							<!-- Exploitation: bolt that flickers on hover -->
							<path class="icon-bolt" d="M13 2L4.5 13.5h6L11 22l8.5-11.5h-6L13 2z" />
						</template>
					</svg>

					<div class="phase-head">
						<h3 class="phase-title">{{ phase.title }}</h3>
						<span class="phase-arrow" aria-hidden="true">&rarr;</span>
					</div>

					<p class="phase-copy">{{ phase.copy }}</p>
				</li>
			</ul>
		</div>
	</section>
</template>

<style scoped>
.phases {
	margin-block: 0 6rem;
	border-block: 1px solid var(--border);
	background: rgba(255, 255, 255, 0.03);
	scroll-margin-top: 5rem;
}

.phases-inner {
	padding-block: 3.5rem;
}

.phases-grid {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 2.5rem 3rem;
	margin: 0;
	padding: 0;
	list-style: none;
}

.phase {
	min-width: 0;
}

.phase-icon {
	width: 22px;
	height: 22px;
	color: var(--fg);
	opacity: 0.9;
	margin-bottom: 1.4rem;
	overflow: visible;
}

.phase-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 1rem;
	margin-bottom: 0.7rem;
}

.phase-title {
	margin: 0;
	font-size: 1.08rem;
	font-weight: 600;
	letter-spacing: -0.01em;
	line-height: 1.3;
	color: var(--fg);
}

.phase-arrow {
	color: var(--muted);
	font-size: 1rem;
	transition: color 0.2s ease, transform 0.2s ease;
}

.phase:hover .phase-arrow {
	color: var(--fg);
	transform: translateX(2px);
}

.phase-copy {
	margin: 0;
	font-size: 0.9rem;
	line-height: 1.65;
	color: var(--muted);
}

/* --- hover animations, one per phase ------------------------------------- */

/* Planning: the check re-draws itself and the shield settles in place. */
.icon-check {
	stroke-dasharray: 10;
	stroke-dashoffset: 0;
}

.phase:hover .icon-check {
	animation: check-draw 0.9s ease forwards;
}

@keyframes check-draw {
	from {
		stroke-dashoffset: 10;
	}
	to {
		stroke-dashoffset: 0;
	}
}

.phase:hover .icon-shield {
	transform-origin: 12px 12px;
	animation: shield-settle 0.9s ease;
}

@keyframes shield-settle {
	0% {
		transform: scale(1);
	}
	40% {
		transform: scale(0.92);
	}
	100% {
		transform: scale(1);
	}
}

/* Recon: the dashed orbit ring spins around the globe. */
.icon-orbit {
	transform-origin: 12px 12px;
}

.phase:hover .icon-orbit {
	animation: orbit-spin 6s linear infinite;
}

@keyframes orbit-spin {
	to {
		transform: rotate(360deg);
	}
}

/* Scanning: the beam sweeps and the detected blip pulses. */
.icon-sweep {
	transform-origin: 12px 12px;
}

.phase:hover .icon-sweep {
	animation: radar-sweep 2.2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes radar-sweep {
	0% {
		transform: rotate(45deg);
	}
	50% {
		transform: rotate(-45deg);
	}
	100% {
		transform: rotate(45deg);
	}
}

.icon-blip {
	transform-origin: 12px 12px;
	transform-box: view-box;
}

.phase:hover .icon-blip {
	animation: blip-pulse 2.2s ease-in-out infinite;
}

@keyframes blip-pulse {
	0%,
	100% {
		opacity: 0.25;
	}
	50% {
		opacity: 1;
	}
}

/* Exploitation: the bolt flickers like a live circuit. */
.phase:hover .icon-bolt {
	transform-origin: 12px 12px;
	animation: bolt-flicker 1.4s steps(1, end) infinite;
}

@keyframes bolt-flicker {
	0%,
	100% {
		opacity: 1;
	}
	15% {
		opacity: 0.35;
	}
	25% {
		opacity: 1;
	}
	55% {
		opacity: 0.55;
	}
	65% {
		opacity: 1;
	}
}

@media (max-width: 1000px) {
	.phases-grid {
		grid-template-columns: repeat(2, 1fr);
	}
}

@media (max-width: 560px) {
	.phases-grid {
		grid-template-columns: 1fr;
		gap: 2rem;
	}
}
</style>