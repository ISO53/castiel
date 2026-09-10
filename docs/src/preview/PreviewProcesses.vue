<script setup>
import { LoaderCircle, Terminal, AppWindow, X } from "@lucide/vue";

defineProps({
	processes: { type: Array, default: () => [] },
});

defineEmits(["kill"]);
</script>

<template>
	<!-- Replica of the bottom processes panel (empty + active states). -->
	<section class="pv-processes">
		<div class="pv-processes-header">
			<span class="pv-processes-title">Processes</span>
			<span class="pv-processes-state">
				{{ processes.length ? `${processes.length} running` : "none active" }}
			</span>
			<span class="pv-processes-actions">
				<button class="pv-icon-btn" type="button" aria-label="Terminal output"><Terminal /></button>
				<button class="pv-icon-btn" type="button" aria-label="Windowed output"><AppWindow /></button>
			</span>
		</div>

		<div v-if="!processes.length" class="pv-empty">
			<Terminal class="pv-empty-icon" />
			<p class="pv-empty-msg">Background processes started by you or the agent appear here.</p>
		</div>

		<ul v-else class="pv-process-list">
			<li v-for="proc in processes" :key="proc.id" class="pv-process-row">
				<LoaderCircle class="pv-process-spinner" />
				<span class="pv-process-name">{{ proc.name }}</span>
				<button class="pv-icon-btn" type="button" aria-label="Kill process" @click="$emit('kill', proc.id)">
					<X />
				</button>
			</li>
		</ul>
	</section>
</template>

<style scoped>
.pv-processes {
	display: flex;
	flex-direction: column;
	height: 8.5rem;
	flex-shrink: 0;
	border-top: 1px solid var(--p-border);
	background: var(--p-background);
}

.pv-processes-header {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	height: 2rem;
	padding: 0 0.75rem;
	border-bottom: 1px solid var(--p-border);
	flex-shrink: 0;
}

.pv-processes-title {
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.pv-processes-state {
	font-size: 0.6875rem;
	color: var(--p-muted-foreground);
}

.pv-processes-actions {
	margin-left: auto;
	display: flex;
	align-items: center;
	gap: 0.25rem;
}

.pv-icon-btn {
	display: grid;
	place-items: center;
	padding: 0.2rem;
	border: none;
	border-radius: 0.25rem;
	background: none;
	color: var(--p-muted-foreground);
	cursor: default;
}

.pv-icon-btn svg {
	width: 0.875rem;
	height: 0.875rem;
}

.pv-icon-btn:hover {
	color: var(--p-foreground);
}

.pv-process-list {
	list-style: none;
	margin: 0;
	padding: 0.5rem;
	display: flex;
	flex-direction: column;
	gap: 0.25rem;
	overflow-y: auto;
}

.pv-process-row {
	display: flex;
	align-items: center;
	gap: 0.625rem;
	padding: 0.4375rem 0.625rem;
	border: 1px solid var(--p-border);
	border-radius: 0.375rem;
	background: var(--p-card);
}

.pv-process-spinner {
	width: 0.875rem;
	height: 0.875rem;
	color: var(--p-muted-foreground);
	animation: pv-spin 1s linear infinite;
	flex-shrink: 0;
}

@keyframes pv-spin {
	to {
		transform: rotate(360deg);
	}
}

.pv-process-name {
	flex: 1;
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.6875rem;
	color: var(--p-foreground);
}
</style>
