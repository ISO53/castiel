<script setup>
import { Boxes } from "@lucide/vue";
import { TECH_STACK_SECTIONS } from "./data.js";

/** Overlapping diamonds get progressively more opaque, like the app. */
function diamondOpacity(index) {
	return Math.min(0.4 + index * 0.15, 0.95);
}
</script>

<template>
	<!-- Replica of frontend/src/components/views/panels/TechStackPanel.vue. -->
	<div class="pv-stack">
		<h2 class="pv-stack-title">Tech Stack</h2>

		<section v-for="section in TECH_STACK_SECTIONS" :key="section.name" class="pv-stack-section">
			<div class="pv-stack-heading">
				<h3>{{ section.name }}</h3>
			</div>

			<div class="pv-stack-rows">
				<div v-for="(tech, index) in section.techs" :key="tech.name" class="pv-stack-row">
					<svg class="pv-diamond" viewBox="0 0 160 64">
						<polygon
							points="80,2 156,32 80,62 4,32"
							:fill="section.color"
							:fill-opacity="diamondOpacity(index)"
							:stroke="section.color"
							stroke-width="1"
						/>
					</svg>
					<div class="pv-stack-meta">
						<p class="pv-stack-name">{{ tech.name }}</p>
						<p class="pv-stack-desc">{{ tech.desc }}</p>
					</div>
				</div>
			</div>
		</section>
	</div>
</template>

<style scoped>
.pv-stack {
	height: 100%;
	min-height: 0;
	overflow-y: auto;
	padding: 0.75rem;
}

.pv-stack-title {
	margin-bottom: 0.75rem;
	font-size: 0.875rem;
	font-weight: 700;
	text-transform: uppercase;
	letter-spacing: 0.1em;
	color: var(--p-foreground);
}

.pv-stack-section {
	margin-bottom: 1.25rem;
}

.pv-stack-heading {
	margin-bottom: 0.5rem;
	border-bottom: 1px solid color-mix(in oklab, var(--p-border) 60%, transparent);
	padding-bottom: 0.25rem;
}

.pv-stack-heading h3 {
	font-size: 0.75rem;
	font-weight: 600;
	text-transform: uppercase;
	letter-spacing: 0.1em;
	color: var(--p-muted-foreground);
}

.pv-stack-rows {
	min-width: fit-content;
	padding: 0.625rem 0;
}

.pv-stack-row {
	position: relative;
	height: 2.5rem;
}

.pv-diamond {
	position: absolute;
	left: 3.5rem;
	top: -0.625rem;
	width: 9rem;
	height: 3.75rem;
	overflow: visible;
}

.pv-stack-meta {
	position: absolute;
	left: 13rem;
	top: 50%;
	width: 22rem;
	min-width: 0;
	transform: translateY(-50%);
}

.pv-stack-name {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 0.75rem;
	font-weight: 600;
	color: var(--p-foreground);
}

.pv-stack-desc {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 10px;
	line-height: 1.25;
	color: var(--p-muted-foreground);
}
</style>
