<template>
	<div class="chat-container">
		<!-- Provider Settings Toggle -->
		<ProviderChooser :config="providerConfig" />

		<!-- Messages Stream Area -->
		<div ref="messagesBoxRef" class="messages-area">
			<div v-if="messages.length === 0" class="empty-state">
				<div class="empty-icon">⚡</div>
				<div class="empty-title">AI Assistant</div>
				<div class="empty-subtitle">
					Ready to stream from {{ providerConfig.baseUrl }} ({{ providerConfig.modelName }})
				</div>
			</div>

			<div v-else class="messages-list">
				<ChatMessage
					v-for="(msg, idx) in messages"
					:key="msg.id"
					:message="msg"
					:is-streaming="isStreaming"
					:is-last="idx === messages.length - 1"
				/>
			</div>
		</div>

		<!-- Bottom Prompt Input -->
		<PromptInput
			:is-streaming="isStreaming"
			@send="handleSend"
			@stop="stopStreaming"
		/>
	</div>
</template>

<script setup>
import { ref, nextTick, watch } from "vue";
import { useAiChat } from "./useAiChat.js";
import ProviderChooser from "./ProviderChooser.vue";
import ChatMessage from "./ChatMessage.vue";
import PromptInput from "./PromptInput.vue";

const {
	isStreaming,
	messages,
	providerConfig,
	sendMessage,
	stopStreaming,
	clearMessages,
} = useAiChat();

const messagesBoxRef = ref(null);

async function handleSend(text) {
	await sendMessage(text);
}

// Auto-scroll on new message / streaming chunks
watch(
	() => messages.value.map((m) => m.content).join(""),
	() => {
		nextTick(() => {
			if (messagesBoxRef.value) {
				messagesBoxRef.value.scrollTop = messagesBoxRef.value.scrollHeight;
			}
		});
	}
);
</script>

<style scoped>
.chat-container {
	display: flex;
	flex-direction: column;
	height: 100%;
	width: 100%;
	background: var(--bg-root);
	overflow: hidden;
}

.messages-area {
	flex: 1;
	min-height: 0;
	overflow-y: auto;
	padding: var(--space-3);
}

.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	height: 100%;
	text-align: center;
	color: var(--fg-muted);
	gap: var(--space-2);
	user-select: none;
}

.empty-icon {
	font-size: 24px;
	opacity: 0.8;
}

.empty-title {
	font-size: var(--font-size-md);
	font-weight: 500;
	color: var(--fg-primary);
}

.empty-subtitle {
	font-size: var(--font-size-xs);
	max-width: 240px;
	line-height: 1.4;
}

.messages-list {
	display: flex;
	flex-direction: column;
	gap: var(--space-3);
}
</style>
