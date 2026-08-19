<template>
	<div class="prompt-input-container">
		<div class="input-wrapper">
			<textarea
				ref="textareaRef"
				v-model="promptText"
				class="prompt-textarea"
				placeholder="Send a prompt to AI... (Enter to send, Shift+Enter for newline)"
				:disabled="isStreaming"
				rows="2"
				@keydown="handleKeydown"
			></textarea>
		</div>

		<div class="actions-row">
			<span class="hint-text">Shift + Enter for new line</span>
			<div class="buttons">
				<button
					v-if="isStreaming"
					type="button"
					class="btn btn--stop"
					@click="$emit('stop')"
				>
					Stop
				</button>
				<button
					v-else
					type="button"
					class="btn btn--send"
					:disabled="!promptText.trim()"
					@click="submit"
				>
					Send ↵
				</button>
			</div>
		</div>
	</div>
</template>

<script setup>
import { ref } from "vue";

const props = defineProps({
	isStreaming: {
		type: Boolean,
		default: false,
	},
});

const emit = defineEmits(["send", "stop"]);

const promptText = ref("");
const textareaRef = ref(null);

function handleKeydown(e) {
	if (e.key === "Enter" && !e.shiftKey) {
		e.preventDefault();
		submit();
	}
}

function submit() {
	if (!promptText.value.trim() || props.isStreaming) return;
	emit("send", promptText.value);
	promptText.value = "";
}
</script>

<style scoped>
.prompt-input-container {
	display: flex;
	flex-direction: column;
	background: var(--bg-surface);
	border-top: 1px solid var(--border-default);
	padding: var(--space-2) var(--space-3);
	gap: var(--space-2);
}

.input-wrapper {
	width: 100%;
}

.prompt-textarea {
	width: 100%;
	background: var(--bg-root);
	border: 1px solid var(--border-default);
	border-radius: var(--radius-sm);
	color: var(--fg-primary);
	font-family: var(--font-sans);
	font-size: var(--font-size-sm);
	padding: 8px 10px;
	outline: none;
	resize: none;
	line-height: 1.4;
	transition: border-color 0.15s ease;
}

.prompt-textarea:focus {
	border-color: var(--accent-primary);
}

.actions-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.hint-text {
	font-size: 10px;
	color: var(--fg-muted);
}

.buttons {
	display: flex;
	gap: var(--space-2);
}

.btn {
	padding: 4px 12px;
	border-radius: var(--radius-sm);
	font-size: var(--font-size-xs);
	font-weight: 500;
	cursor: pointer;
	border: 1px solid transparent;
	transition: background 0.15s ease, opacity 0.15s ease;
}

.btn--send {
	background: var(--accent-primary);
	color: var(--fg-inverse);
	border-color: var(--accent-primary);
}

.btn--send:hover:not(:disabled) {
	background: var(--accent-hover);
}

.btn--send:disabled {
	opacity: 0.35;
	cursor: not-allowed;
}

.btn--stop {
	background: #dc2626;
	color: #ffffff;
	border-color: #ef4444;
}

.btn--stop:hover {
	background: #b91c1c;
}
</style>
