<template>
	<div class="provider-chooser">
		<div class="header-toggle" @click="isOpen = !isOpen">
			<div class="status-indicator">
				<span class="dot"></span>
				<span class="title">AI Provider</span>
			</div>
			<div class="summary">
				<span class="model-badge">{{ config.modelName }}</span>
				<span class="chevron" :class="{ 'is-open': isOpen }">▾</span>
			</div>
		</div>

		<div v-show="isOpen" class="drawer-content">
			<div class="field-group">
				<label class="field-label">Endpoint URL</label>
				<input
					v-model="config.baseUrl"
					type="text"
					class="field-input"
					placeholder="http://localhost:8080/v1"
				/>
			</div>

			<div class="field-group">
				<label class="field-label">Model Name</label>
				<input
					v-model="config.modelName"
					type="text"
					class="field-input"
					placeholder="model identifier"
				/>
			</div>

			<div class="field-group">
				<label class="field-label">System Prompt</label>
				<textarea
					v-model="config.systemPrompt"
					rows="2"
					class="field-input field-textarea"
					placeholder="System instructions..."
				></textarea>
			</div>
		</div>
	</div>
</template>

<script setup>
import { ref } from "vue";

const props = defineProps({
	config: {
		type: Object,
		required: true,
	},
});

const isOpen = ref(false);
</script>

<style scoped>
.provider-chooser {
	border-bottom: 1px solid var(--border-default);
	background: var(--bg-surface);
	font-size: var(--font-size-xs);
}

.header-toggle {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: var(--space-2) var(--space-3);
	cursor: pointer;
	user-select: none;
	transition: background var(--transition-fast, 0.15s ease);
}

.header-toggle:hover {
	background: var(--bg-hover);
}

.status-indicator {
	display: flex;
	align-items: center;
	gap: 6px;
}

.dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: #10b981;
}

.title {
	font-weight: 500;
	color: var(--fg-primary);
}

.summary {
	display: flex;
	align-items: center;
	gap: 6px;
}

.model-badge {
	padding: 2px 6px;
	border-radius: var(--radius-xs);
	background: var(--bg-elevated);
	border: 1px solid var(--border-default);
	color: var(--fg-secondary);
	font-family: var(--font-mono);
	max-width: 140px;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.chevron {
	color: var(--fg-muted);
	transition: transform 0.2s ease;
}

.chevron.is-open {
	transform: rotate(180deg);
}

.drawer-content {
	padding: var(--space-3);
	border-top: 1px solid var(--border-subtle);
	display: flex;
	flex-direction: column;
	gap: var(--space-2);
	background: var(--bg-surface);
}

.field-group {
	display: flex;
	flex-direction: column;
	gap: 4px;
}

.field-label {
	color: var(--fg-muted);
	font-size: 10px;
	text-transform: uppercase;
	letter-spacing: 0.05em;
}

.field-input {
	background: var(--bg-root);
	border: 1px solid var(--border-default);
	border-radius: var(--radius-sm);
	color: var(--fg-primary);
	padding: 5px 8px;
	font-family: var(--font-mono);
	font-size: var(--font-size-xs);
	outline: none;
	transition: border-color 0.15s ease;
}

.field-input:focus {
	border-color: var(--accent-primary);
}

.field-textarea {
	resize: vertical;
	min-height: 44px;
	font-family: var(--font-sans);
}
</style>
