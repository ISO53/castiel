<script setup>
import { Check } from "@lucide/vue";
import { PHASES } from "./data.js";

defineProps({
	phase: { type: Number, default: 1 },
});
</script>

<template>
	<!-- Replica of the app's menu bar with the centered engagement phase stepper
	     (styles ported from frontend/src/views/MenuBar.vue). -->
	<header class="pv-menubar">
		<span class="pv-app-name">Castiel</span>
		<button v-for="menu in ['File', 'Window', 'Help']" :key="menu" type="button" class="pv-menu-item">
			{{ menu }}
		</button>

		<div class="pv-phases">
			<button
				v-for="item in PHASES"
				:key="item.value"
				type="button"
				class="pv-phase"
				:class="{ active: item.value === phase, done: item.value < phase }"
			>
				<span class="pv-phase-step">
					<Check v-if="item.value < phase" class="pv-phase-check" />
					<template v-else>{{ item.value }}</template>
				</span>
				<span class="pv-phase-label">{{ item.short }}</span>
			</button>
		</div>
	</header>
</template>

<style scoped>
.pv-menubar {
	position: relative;
	display: flex;
	align-items: center;
	gap: 0.125rem;
	height: 2.5rem;
	padding: 0 0.75rem;
	background: var(--p-card);
	border-bottom: 1px solid var(--p-border);
}

.pv-app-name {
	margin-right: 0.5rem;
	font-size: 0.8rem;
	font-weight: 600;
	color: var(--p-foreground);
}

.pv-menu-item {
	border: none;
	background: none;
	padding: 0.25rem 0.5rem;
	border-radius: 0.375rem;
	font-size: 0.75rem;
	color: var(--p-muted-foreground);
	cursor: default;
}

.pv-menu-item:hover {
	color: var(--p-foreground);
}

.pv-phases {
	position: absolute;
	left: 50%;
	top: 50%;
	transform: translate(-50%, -50%);
	display: flex;
	align-items: center;
	gap: 0.125rem;
}

.pv-phase {
	display: flex;
	align-items: center;
	gap: 0.375rem;
	height: 1.5rem;
	padding: 0 0.5rem;
	border: none;
	border-radius: 0.375rem;
	background: none;
	font-size: 0.75rem;
	color: var(--p-muted-foreground);
	cursor: default;
	transition: color 0.15s ease, background-color 0.15s ease;
}

.pv-phase:not(.active):hover {
	color: var(--p-foreground);
}

.pv-phase.active {
	background: var(--p-primary);
	color: var(--p-primary-foreground);
}

.pv-phase-step {
	display: grid;
	place-items: center;
	width: 1rem;
	height: 1rem;
	flex-shrink: 0;
	border: 1px solid var(--p-muted-foreground);
	border-radius: 999px;
	font-size: 10px;
	line-height: 1;
	font-weight: 600;
}

.pv-phase:not(.active):not(.done) .pv-phase-step {
	border-color: color-mix(in oklab, var(--p-muted-foreground) 40%, transparent);
}

.pv-phase.active .pv-phase-step {
	border-color: currentColor;
}

.pv-phase-check {
	width: 0.75rem;
	height: 0.75rem;
}

.pv-phase-label {
	white-space: nowrap;
	font-weight: 500;
	line-height: 1;
}
</style>
