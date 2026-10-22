<script setup>
// Supported model providers, placed directly after the app preview.
// Logos are official brand assets:
//   llama.cpp  — ggml-org/llama.brand (icon, CC BY-NC 4.0 / BRAND-USAGE.md)
//   ollama     — simple-icons/simple-icons "ollama.svg" (monochrome mark)
//   openrouter — openrouter.ai/brand (glyph, "volt" variant for dark surfaces)
//   cline      — cline.bot/brand assets ("Bot" icon, dark variant)
//   z.ai       — z.ai static logo
import llamacppLogo from "@/assets/providers/llamacpp.svg";
import ollamaLogo from "@/assets/providers/ollama.svg";
import openrouterLogo from "@/assets/providers/openrouter.svg";
import clineLogo from "@/assets/providers/cline.svg";
import zaiLogo from "@/assets/providers/zai.svg";

const providers = [
	{ name: "llama.cpp", logo: llamacppLogo },
	{ name: "Ollama", logo: ollamaLogo },
	{ name: "OpenRouter", logo: openrouterLogo },
	{ name: "Cline", logo: clineLogo },
	{ name: "z.ai", logo: zaiLogo },
];
</script>

<template>
	<section class="providers">
		<div class="providers-inner container">
			<div class="providers-text">
				<h2 class="providers-title">Your models, your choice</h2>
				<p class="providers-copy">
					Use your preferred provider with your own subscription or
					API-billed models.
				</p>
			</div>

			<ul class="provider-row">
				<li v-for="provider in providers" :key="provider.name" class="provider-item">
					<img class="provider-logo" :src="provider.logo" :alt="provider.name + ' logo'" />
					<span class="provider-name">{{ provider.name }}</span>
				</li>
			</ul>
		</div>
	</section>
</template>

<style scoped>
.providers {
	margin-block: 0 3rem;
	border-block: 1px solid var(--border);
	background: rgba(255, 255, 255, 0.015);
	scroll-margin-top: 5rem;
}

.providers-inner {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 2.5rem 4rem;
	padding: 3rem clamp(2rem, 6vw, 6rem);
	flex-wrap: wrap;
}

.providers-text {
	max-width: 24rem;
}

.providers-title {
	margin: 0 0 0.6rem;
	font-size: 1.05rem;
	font-weight: 600;
	letter-spacing: -0.01em;
	color: var(--fg);
}

.providers-copy {
	margin: 0;
	font-size: 0.85rem;
	line-height: 1.6;
	color: var(--muted);
}

.provider-row {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 2.25rem 2.75rem;
	margin: 0;
	padding: 0;
	list-style: none;
}

.provider-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 0.55rem;
}

.provider-logo {
	height: 26px;
	width: 26px;
	object-fit: contain;
	opacity: 0.9;
	transition: opacity 0.2s ease;
}

.provider-item:hover .provider-logo {
	opacity: 1;
}

.provider-name {
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--muted);
	transition: color 0.2s ease;
}

.provider-item:hover .provider-name {
	color: var(--fg);
}

@media (max-width: 720px) {
	.providers-inner {
		flex-direction: column;
		align-items: flex-start;
	}
}
</style>