<script setup>
import { nextTick, ref, watch } from "vue";
import {
	ArrowDown,
	Brain,
	ChevronDown,
	CircleCheck,
	Clock,
	CornerDownLeft,
	History,
	PanelRightClose,
	Plus,
	RotateCcw,
	Wrench,
} from "@lucide/vue";
import { INITIAL_PROMPT_HEADER } from "./data.js";

const props = defineProps({
	messages: { type: Array, default: () => [] },
	playing: { type: Boolean, default: false },
	done: { type: Boolean, default: false },
});

const emit = defineEmits(["submit", "restart"]);

const draft = ref("");
const scroller = ref(null);
const atBottom = ref(true);

function onScroll() {
	const el = scroller.value;
	if (!el) return;
	atBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 48;
}

function scrollToBottom() {
	const el = scroller.value;
	if (el) el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
}

watch(
	() => props.messages,
	() => {
		nextTick(() => {
			const el = scroller.value;
			if (el && atBottom.value) el.scrollTop = el.scrollHeight;
		});
	},
	{ deep: true },
);

function send() {
	const text = draft.value.trim();
	if (!text) return;
	draft.value = "";
	emit("submit", text);
}

function escapeHtml(value) {
	return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** Renders **bold** inline markup like the app's markdown renderer would. */
function renderInline(text) {
	return escapeHtml(text).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
}
</script>

<template>
	<section class="pv-chat">
		<header class="pv-chat-header">
			<p class="pv-chat-title">{{ INITIAL_PROMPT_HEADER }}</p>
			<div class="pv-chat-actions">
				<button class="pv-icon-btn" type="button" aria-label="Replay the demo" title="Replay demo" @click="$emit('restart')">
					<RotateCcw />
				</button>
				<button class="pv-icon-btn" type="button" aria-label="Chat history"><History /></button>
				<button class="pv-icon-btn" type="button" aria-label="New chat"><Plus /></button>
				<button class="pv-icon-btn" type="button" aria-label="Hide dock"><PanelRightClose /></button>
			</div>
		</header>

		<div ref="scroller" class="pv-chat-scroll" @scroll="onScroll">
			<div class="pv-chat-list">
				<template v-for="message in messages" :key="message.id">
					<div v-if="message.role === 'user'" class="pv-msg-user">{{ message.text }}</div>

					<template v-else>
						<template v-for="(part, partIndex) in message.parts" :key="partIndex">
							<button
								v-if="part.type === 'reasoning'"
								type="button"
								class="pv-reasoning"
								@click="part.open = !part.open"
							>
								<Brain class="pv-inline-icon" />
								<span v-if="part.streaming" class="pv-shimmer">Thinking…</span>
								<span v-else>Thought for {{ part.seconds }} seconds</span>
								<ChevronDown class="pv-chev" :class="{ open: part.open }" />
							</button>

							<div v-if="part.type === 'reasoning' && part.open && !part.streaming" class="pv-reasoning-content">
								{{ part.text }}
							</div>

							<div v-else-if="part.type === 'tool'" class="pv-tool">
								<button type="button" class="pv-tool-header" @click="part.open = !part.open">
									<Wrench class="pv-inline-icon" />
									<span class="pv-tool-name">{{ part.name }}</span>
									<span class="pv-badge">
										<Clock v-if="part.state === 'running'" class="pv-badge-icon running" />
										<CircleCheck v-else class="pv-badge-icon completed" />
										{{ part.state === "running" ? "Running" : "Completed" }}
									</span>
									<ChevronDown class="pv-chev" :class="{ open: part.open }" />
								</button>
								<div v-if="part.open" class="pv-tool-content">
									<p class="pv-tool-label">Input</p>
									<pre class="pv-tool-pre">{{ part.input }}</pre>
									<template v-if="part.output">
										<p class="pv-tool-label">Output</p>
										<pre class="pv-tool-pre">{{ part.output }}</pre>
									</template>
								</div>
							</div>

							<div
								v-else-if="part.type === 'text'"
								class="pv-msg-assistant"
								v-html="renderInline(part.text)"
							></div>
						</template>
					</template>
				</template>

				<div v-if="playing" class="pv-loader"><span /><span /><span /></div>
			</div>

			<button v-if="!atBottom" type="button" class="pv-scroll-btn" aria-label="Scroll to bottom" @click="scrollToBottom">
				<ArrowDown />
			</button>
		</div>

		<div class="pv-composer">
			<textarea
				v-model="draft"
				class="pv-textarea"
				placeholder="Message Castiel..."
				rows="2"
				@keydown.enter.exact.prevent="send"
			></textarea>
			<div class="pv-composer-footer">
				<span class="pv-context" title="Context window usage">
					<span class="pv-context-pct">6.1%</span>
					<svg viewBox="0 0 16 16" class="pv-context-ring" aria-hidden="true">
						<circle cx="8" cy="8" r="6.5" fill="none" stroke="var(--p-input)" stroke-width="2" />
						<circle
							cx="8"
							cy="8"
							r="6.5"
							fill="none"
							stroke="var(--p-muted-foreground)"
							stroke-width="2"
							stroke-dasharray="2.5 38.5"
							stroke-linecap="round"
							transform="rotate(-90 8 8)"
						/>
					</svg>
				</span>
				<button type="button" class="pv-chip">
					<Brain class="pv-inline-icon" />
					Reasoning: Medium
				</button>
				<button type="button" class="pv-chip pv-chip-model">nvidia/nemotron-3-ult…</button>
				<button type="button" class="pv-send" :disabled="!draft.trim()" aria-label="Send message" @click="send">
					<CornerDownLeft />
				</button>
			</div>
		</div>
	</section>
</template>

<style scoped>
.pv-chat {
	display: flex;
	flex-direction: column;
	min-width: 0;
	min-height: 0;
	background: var(--p-background);
}

.pv-chat-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 0.5rem;
	height: 2rem;
	padding: 0 0.75rem;
	border-bottom: 1px solid var(--p-border);
	flex-shrink: 0;
}

.pv-chat-title {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.pv-chat-actions {
	display: flex;
	align-items: center;
	gap: 0.125rem;
	flex-shrink: 0;
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

.pv-chat-scroll {
	position: relative;
	flex: 1;
	min-height: 0;
	overflow-y: auto;
	scrollbar-width: thin;
	scrollbar-color: var(--p-muted) transparent;
}

.pv-chat-list {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	padding: 1rem 0.75rem;
}

.pv-msg-user {
	align-self: flex-end;
	max-width: 90%;
	padding: 0.5rem 0.75rem;
	border-radius: 0.5rem;
	background: var(--p-primary);
	color: var(--p-primary-foreground);
	font-size: 0.75rem;
	line-height: 1.625;
	white-space: pre-wrap;
}

.pv-msg-assistant {
	align-self: start;
	max-width: 90%;
	font-size: 0.75rem;
	line-height: 1.625;
	color: var(--p-foreground);
}

.pv-msg-assistant :deep(strong) {
	font-weight: 600;
}

.pv-inline-icon {
	width: 0.875rem;
	height: 0.875rem;
	flex-shrink: 0;
	color: var(--p-muted-foreground);
}

.pv-reasoning {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	width: 100%;
	padding: 0;
	border: none;
	background: none;
	font-size: 0.75rem;
	color: var(--p-muted-foreground);
	text-align: left;
	cursor: default;
	transition: color 0.15s ease;
}

.pv-reasoning:hover {
	color: var(--p-foreground);
}

.pv-reasoning-content {
	padding: 0.25rem 0 0 1.375rem;
	font-size: 0.75rem;
	line-height: 1.625;
	opacity: 0.6;
	color: var(--p-foreground);
}

.pv-chev {
	width: 1rem;
	height: 1rem;
	margin-left: auto;
	color: var(--p-muted-foreground);
	transition: transform 0.15s ease;
}

.pv-chev.open {
	transform: rotate(180deg);
}

.pv-tool {
	width: 100%;
	align-self: start;
	border: 1px solid var(--p-border);
	border-radius: 0.5rem;
	background: var(--p-card);
	overflow: hidden;
}

.pv-tool-header {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	width: 100%;
	padding: 0.75rem;
	border: none;
	background: none;
	text-align: left;
	cursor: default;
}

.pv-tool-name {
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.pv-badge {
	display: inline-flex;
	align-items: center;
	gap: 0.375rem;
	padding: 0.125rem 0.5rem;
	border-radius: 999px;
	background: var(--p-secondary);
	color: var(--p-secondary-foreground);
	font-size: 0.75rem;
}

.pv-badge-icon {
	width: 1rem;
	height: 1rem;
}

.pv-badge-icon.running {
	animation: pv-pulse 1.2s ease-in-out infinite;
}

.pv-badge-icon.completed {
	color: #16a34a;
}

@keyframes pv-pulse {
	0%,
	100% {
		opacity: 1;
	}
	50% {
		opacity: 0.35;
	}
}

.pv-tool-content {
	margin: 0 0.75rem 0.75rem;
	padding-top: 0.625rem;
	border-top: 1px solid var(--p-border);
}

.pv-tool-label {
	margin: 0.375rem 0 0.25rem;
	font-size: 0.6875rem;
	font-weight: 500;
	color: var(--p-muted-foreground);
}

.pv-tool-pre {
	margin: 0;
	padding: 0.5rem;
	border-radius: 0.375rem;
	background: var(--p-muted);
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.6875rem;
	line-height: 1.5;
	color: var(--p-foreground);
	white-space: pre-wrap;
	word-break: break-word;
}

.pv-scroll-btn {
	position: sticky;
	bottom: 0.75rem;
	margin-left: calc(50% - 1rem);
	display: grid;
	place-items: center;
	width: 2rem;
	height: 2rem;
	border: 1px solid var(--p-border);
	border-radius: 999px;
	background: var(--p-card);
	color: var(--p-foreground);
	cursor: default;
}

.pv-scroll-btn svg {
	width: 0.875rem;
	height: 0.875rem;
}

.pv-composer {
	flex-shrink: 0;
	border-top: 1px solid var(--p-border);
	padding: 0.75rem;
}

.pv-textarea {
	width: 100%;
	min-height: 3.5rem;
	padding: 0.5rem 0.625rem;
	border: 1px solid var(--p-input);
	border-radius: 0.5rem;
	background: var(--p-card);
	color: var(--p-foreground);
	font-family: inherit;
	font-size: 0.75rem;
	line-height: 1.5;
	resize: none;
}

.pv-textarea::placeholder {
	color: var(--p-muted-foreground);
}

.pv-textarea:focus {
	outline: none;
	border-color: var(--p-muted-foreground);
}

.pv-composer-footer {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	margin-top: 0.5rem;
}

.pv-context {
	display: inline-flex;
	align-items: center;
	gap: 0.25rem;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.6875rem;
	color: var(--p-muted-foreground);
}

.pv-context-ring {
	width: 0.875rem;
	height: 0.875rem;
}

.pv-chip {
	display: inline-flex;
	align-items: center;
	gap: 0.375rem;
	height: 1.75rem;
	padding: 0 0.625rem;
	border: 1px solid var(--p-border);
	border-radius: 0.375rem;
	background: none;
	font-size: 0.6875rem;
	color: var(--p-muted-foreground);
	cursor: default;
	white-space: nowrap;
}

.pv-chip:hover {
	color: var(--p-foreground);
}

.pv-chip-model {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	margin-left: auto;
}

.pv-send {
	display: grid;
	place-items: center;
	width: 2rem;
	height: 2rem;
	flex-shrink: 0;
	border: none;
	border-radius: 0.375rem;
	background: var(--p-primary);
	color: var(--p-primary-foreground);
	cursor: default;
}

.pv-send:disabled {
	opacity: 0.5;
}

.pv-send svg {
	width: 0.875rem;
	height: 0.875rem;
}
</style>
