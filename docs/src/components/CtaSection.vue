<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";

// Closing CTA. When the section scrolls into view, the word "breaks" plays a
// dropped-frame glitch, then runs a decryption scramble that settles on the
// leetified final form. Runs once; reduced-motion renders the final word
// immediately.
const FINAL_WORD = "br3e@ks";
const CHARSET = "!@#$%&*<>?/|{}[]~+=-_0123456789";
const GLITCH_DURATION = 300;
const SCRAMBLE_DURATION = 900;
const SCRAMBLE_TICK = 40;

const word = ref("breaks");
const glitching = ref(false);
const scrambling = ref(false);
const rootEl = ref(null);

let observer = null;
let glitchTimer = null;
let scrambleTimer = null;

function startEffect() {
	const reduced =
		window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
	if (reduced) {
		word.value = FINAL_WORD;
		return;
	}

	glitching.value = true;
	glitchTimer = setTimeout(() => {
		glitching.value = false;
		startScramble();
	}, GLITCH_DURATION);
}

function startScramble() {
	scrambling.value = true;
	const start = performance.now();
	scrambleTimer = setInterval(() => {
		const elapsed = performance.now() - start;
		if (elapsed >= SCRAMBLE_DURATION) {
			clearInterval(scrambleTimer);
			scrambleTimer = null;
			word.value = FINAL_WORD;
			scrambling.value = false;
			return;
		}
		const progress = elapsed / SCRAMBLE_DURATION;
		word.value = FINAL_WORD.split("")
			.map((char, index) => {
				const lockAt = ((index + 1) / FINAL_WORD.length) * 0.85;
				return progress >= lockAt
					? char
					: CHARSET[Math.floor(Math.random() * CHARSET.length)];
			})
			.join("");
	}, SCRAMBLE_TICK);
}

onMounted(() => {
	observer = new IntersectionObserver(
		([entry]) => {
			if (!entry.isIntersecting) return;
			observer?.disconnect();
			observer = null;
			startEffect();
		},
		{ threshold: 0.55 },
	);
	observer.observe(rootEl.value);
});

onBeforeUnmount(() => {
	observer?.disconnect();
	clearTimeout(glitchTimer);
	clearInterval(scrambleTimer);
});
</script>

<template>
	<section ref="rootEl" class="cta container">
		<div class="cta-glow" aria-hidden="true"></div>

		<div class="cta-inner">
			<h2 class="cta-title">
				Pick a target.
				<br />
				See what
				<span class="cta-word" :class="{ glitch: glitching, scrambling }" aria-hidden="true">{{ word }}</span><span class="cta-sr">breaks</span>
			</h2>

			<div class="cta-actions">
				<a
					class="cta-button primary"
					href="https://github.com/ISO53/castiel/releases"
					target="_blank"
					rel="noopener"
				>
					Download castiel
				</a>
				<a
					class="cta-button ghost"
					href="https://github.com/ISO53/castiel#readme"
					target="_blank"
					rel="noopener"
				>
					Read the docs
				</a>
			</div>

			<p class="cta-note">Free &amp; open source — GPL-3.0.</p>
		</div>
	</section>
</template>

<style scoped>
.cta {
	position: relative;
	padding-block: 7rem;
	margin-block: 0 8rem;
	overflow: hidden;
	text-align: center;
}

.cta-glow {
	position: absolute;
	inset: 0;
	pointer-events: none;
	background: radial-gradient(
		ellipse 60% 55% at 50% 55%,
		color-mix(in srgb, var(--accent) 16%, transparent),
		transparent 70%
	);
}

.cta-inner {
	position: relative;
}

.cta-title {
	margin: 0 0 2.5rem;
	font-size: clamp(2.25rem, 6vw, 4rem);
	font-weight: 500;
	line-height: 1.1;
	letter-spacing: -0.03em;
}

.cta-word {
	display: inline-block;
	color: var(--accent);
}

.cta-word.glitch {
	animation: cta-glitch 0.3s steps(1, end) both;
}

@keyframes cta-glitch {
	0%,
	100% {
		transform: none;
	}

	20% {
		transform: rotate(-2.5deg) skewX(-8deg) translateY(2px);
	}

	50% {
		transform: rotate(2deg) skewX(5deg) translateY(-2px);
	}

	80% {
		transform: rotate(-1deg) skewX(-4deg) translateY(1px);
	}
}

.cta-sr {
	position: absolute;
	width: 1px;
	height: 1px;
	margin: -1px;
	padding: 0;
	border: 0;
	overflow: hidden;
	clip: rect(0 0 0 0);
	white-space: nowrap;
}

.cta-actions {
	display: flex;
	flex-wrap: wrap;
	justify-content: center;
	gap: 0.75rem;
}

.cta-button {
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

.cta-button.primary {
	background: var(--fg);
	color: var(--bg);
}

.cta-button.primary:hover {
	background: #ffffff;
}

.cta-button.ghost {
	border: 1px solid var(--border);
	color: var(--fg);
}

.cta-button.ghost:hover {
	border-color: var(--fg);
}

.cta-note {
	margin: 2rem 0 0;
	font-size: 0.85rem;
	color: var(--faint);
}
</style>
