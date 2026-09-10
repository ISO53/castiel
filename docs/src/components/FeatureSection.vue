<script setup>
// "Why castiel" bento section. Visual sub-components live in ./features/;
// their motion is CSS-only and triggered by hovering each .feature-card.
import FeatureChat from "@/components/features/FeatureChat.vue";
import FeatureModels from "@/components/features/FeatureModels.vue";
import FeatureFindings from "@/components/features/FeatureFindings.vue";
import FeatureProcesses from "@/components/features/FeatureProcesses.vue";
import FeatureMcp from "@/components/features/FeatureMcp.vue";
import FeatureControl from "@/components/features/FeatureControl.vue";
</script>

<template>
	<section class="features container">
		<h2 class="features-title">Why castiel</h2>

		<div class="features-grid">
			<article class="feature-card chat">
				<div class="feature-visual"><FeatureChat /></div>
				<div class="feature-body">
					<h3 class="feature-title">Chat-driven engagements</h3>
					<p class="feature-desc">
						Open a workspace, connect a model, and run the whole engagement
						through a chat. castiel runs the hands-on technical work and keeps
						everything recorded.
					</p>
				</div>
			</article>

			<article class="feature-card models">
				<div class="feature-visual"><FeatureModels /></div>
				<div class="feature-body">
					<h3 class="feature-title">Bring your own model</h3>
					<p class="feature-desc">
						llama.cpp, Ollama, OpenRouter or Cline. castiel talks to the model of
						your choice. Local or cloud-based.
					</p>
				</div>
			</article>

			<article class="feature-card findings">
				<div class="feature-visual"><FeatureFindings /></div>
				<div class="feature-body">
					<h3 class="feature-title">Structured findings</h3>
					<p class="feature-desc">
						Findings land in structured documents you can review as graphs,
						boards and tables.
					</p>
				</div>
			</article>

			<article class="feature-card processes">
				<div class="feature-visual"><FeatureProcesses /></div>
				<div class="feature-body">
					<h3 class="feature-title">Background processes</h3>
					<p class="feature-desc">
						Long-running scans, started by you or the agent, keep working while the conversation continues.
					</p>
				</div>
			</article>

			<article class="feature-card mcp">
				<div class="feature-visual"><FeatureMcp /></div>
				<div class="feature-body">
					<h3 class="feature-title">MCP-native</h3>
					<p class="feature-desc">
						Connect obscura, Caido or any MCP server and its tools become
						available to the agent.
					</p>
				</div>
			</article>

			<article class="feature-card control">
				<div class="feature-visual"><FeatureControl /></div>
				<div class="feature-body">
					<h3 class="feature-title">You stay in control</h3>
					<p class="feature-desc">
						You define the target and the scope. Decide when the engagement
						moves to the next phase.
					</p>
				</div>
			</article>
		</div>
	</section>
</template>

<style scoped>
.features {
	margin-block: 2rem 6rem;
	scroll-margin-top: 5rem;
}

.features-title {
	margin: 0 0 2.5rem;
	font-size: clamp(2rem, 5vw, 3rem);
	font-weight: 500;
	letter-spacing: -0.03em;
}

.features-grid {
	display: grid;
	grid-template-columns: repeat(12, 1fr);
	grid-template-areas:
		"chat chat chat chat chat chat chat models models models models models"
		"chat chat chat chat chat chat chat findings findings findings findings findings"
		"processes processes processes processes mcp mcp mcp mcp control control control control";
	gap: 1rem;
}

.feature-card.chat {
	grid-area: chat;
}

.feature-card.models {
	grid-area: models;
}

.feature-card.findings {
	grid-area: findings;
}

.feature-card.processes {
	grid-area: processes;
}

.feature-card.mcp {
	grid-area: mcp;
}

.feature-card.control {
	grid-area: control;
}

.feature-card {
	display: flex;
	flex-direction: column;
	padding: 1.5rem;
	border: 1px solid var(--border);
	border-radius: 12px;
	background: var(--surface);
	overflow: hidden;
	transition: border-color 0.15s ease;
}

.feature-card:hover {
	border-color: color-mix(in srgb, var(--fg) 8%, var(--border));
}

.feature-visual {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 160px;
	margin-bottom: 1rem;
	pointer-events: none;
}

.feature-card.chat .feature-visual {
	flex: 1;
	min-height: 180px;
}

.feature-body {
	margin-top: auto;
}

.feature-title {
	margin: 0 0 0.5rem;
	font-size: 1.08rem;
	font-weight: 600;
	color: var(--fg);
}

.feature-desc {
	margin: 0;
	font-size: 0.9rem;
	line-height: 1.6;
	color: var(--muted);
}

@media (max-width: 860px) {
	.features-grid {
		grid-template-columns: 1fr;
		grid-template-areas:
			"chat"
			"models"
			"findings"
			"processes"
			"mcp"
			"control";
	}
}
</style>
