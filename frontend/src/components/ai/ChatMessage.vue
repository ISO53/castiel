<template>
	<div class="chat-message" :class="`chat-message--${message.role}`">
		<div class="message-header">
			<span class="role-badge">{{ message.role === 'user' ? 'YOU' : 'ASSISTANT' }}</span>
			<span class="timestamp">{{ formatTime(message.timestamp) }}</span>
		</div>
		<div class="message-body">
			<pre class="message-text">{{ message.content }}<span v-if="isStreaming && isLast && message.role === 'assistant'" class="streaming-cursor">▊</span></pre>
		</div>
	</div>
</template>

<script setup>
const props = defineProps({
	message: {
		type: Object,
		required: true,
	},
	isStreaming: {
		type: Boolean,
		default: false,
	},
	isLast: {
		type: Boolean,
		default: false,
	},
});

function formatTime(date) {
	if (!date) return "";
	const d = new Date(date);
	return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}
</script>

<style scoped>
.chat-message {
	display: flex;
	flex-direction: column;
	gap: 4px;
	padding: var(--space-2) var(--space-3);
	border-radius: var(--radius-sm);
	font-size: var(--font-size-sm);
	line-height: 1.5;
	word-break: break-word;
}

.chat-message--user {
	background: var(--bg-surface);
	border: 1px solid var(--border-default);
}

.chat-message--assistant {
	background: transparent;
	border-left: 2px solid var(--accent-primary);
	padding-left: var(--space-3);
}

.message-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	font-size: 10px;
}

.role-badge {
	font-weight: 600;
	letter-spacing: 0.05em;
	color: var(--fg-secondary);
}

.chat-message--assistant .role-badge {
	color: var(--accent-primary);
}

.timestamp {
	color: var(--fg-faint);
	font-family: var(--font-mono);
}

.message-body {
	color: var(--fg-primary);
}

.message-text {
	margin: 0;
	white-space: pre-wrap;
	word-wrap: break-word;
	font-family: var(--font-mono);
	font-size: 12px;
}

.streaming-cursor {
	display: inline-block;
	color: var(--accent-primary);
	animation: blink 0.9s infinite;
	margin-left: 2px;
}

@keyframes blink {
	0%, 50% { opacity: 1; }
	51%, 100% { opacity: 0; }
}
</style>
