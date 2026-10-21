<script setup>
import { nextTick, ref, watch } from "vue";
import {
	ArrowDown,
	ArrowUp,
	Bot,
	Brain,
	ChevronDown,
	FilePlus,
	History,
	LockOpen,
	Plus,
	Square,
	SquareTerminal,
} from "@lucide/vue";
import { CONTEXT_USED, INITIAL_PROMPT_HEADER, MODEL_LABEL } from "./data.js";

const props = defineProps({
	messages: { type: Array, default: () => [] },
	playing: { type: Boolean, default: false },
	done: { type: Boolean, default: false },
});

const emit = defineEmits(["submit"]);

const draft = ref("");
const scroller = ref(null);
const atBottom = ref(true);

// Mirrors frontend/src/lib/tool-icons.js for the names the demo uses.
const TOOL_ICONS = {
	bash: SquareTerminal,
	bg_start: SquareTerminal,
	sub_agent: Bot,
	agent_list: Bot,
	agent_read: Bot,
	agent_kill: Bot,
	write_file: FilePlus,
};

function toolIcon(name) {
	return TOOL_ICONS[name] ?? Bot;
}

/** `name(key: "value", …)` — the same summary ToolCall.vue renders. */
function argsSummary(part) {
	const entries = Object.entries(part.input ?? {});
	if (!entries.length) return "";
	return `(${entries.map(([key, value]) => `${key}: ${JSON.stringify(value)}`).join(", ")})`;
}

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

/** Renders **bold** and `code` inline markup like the app's markdown renderer. */
function renderInline(text) {
	return escapeHtml(text)
		.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
		.replace(/`([^`]+)`/g, '<code class="pv-inline-code">$1</code>');
}
</script>

<template>
	<section class="pv-chat">
		<header class="pv-chat-header">
			<p class="pv-chat-title">{{ INITIAL_PROMPT_HEADER }}</p>
			<div class="pv-chat-actions">
				<button class="pv-icon-btn" type="button" aria-label="Chat history"><History /></button>
				<button class="pv-icon-btn" type="button" aria-label="New chat"><Plus /></button>
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

							<!-- Tool call — mirrors components/ai-elements/tool/ToolCall.vue -->
							<div v-else-if="part.type === 'tool'" class="pv-tool">
								<button type="button" class="pv-tool-header" @click="part.open = !part.open">
									<component :is="toolIcon(part.name)" class="pv-inline-icon" />
									<span class="pv-tool-summary">
										<span v-if="part.state === 'running'" class="pv-shimmer">{{ part.name }}{{ argsSummary(part) }}</span>
										<template v-else>
											<span class="pv-tool-name">{{ part.name }}</span><span>{{ argsSummary(part) }}</span>
										</template>
									</span>
									<ChevronDown class="pv-chev" :class="{ open: part.open }" />
								</button>

								<div v-if="part.open && part.state !== 'running'" class="pv-tool-content">
									<div v-for="(value, key) in part.input" :key="key" class="pv-tool-param">
										<span class="pv-tool-key">{{ key }}:</span>
										<span class="pv-tool-value">{{ typeof value === "string" ? JSON.stringify(value) : value }}</span>
									</div>
									<pre v-if="part.output" class="pv-tool-result">{{ part.output }}</pre>
								</div>
							</div>

							<!-- Assistant text segment -->
							<div v-else-if="part.type === 'text'" class="pv-msg-text" v-html="renderInline(part.text)" />
						</template>
					</template>
				</template>
			</div>

			<button v-if="!atBottom" type="button" class="pv-scroll-btn" aria-label="Scroll to bottom" @click="scrollToBottom">
				<ArrowDown />
			</button>
		</div>

		<div class="pv-composer">
			<textarea
				v-model="draft"
				class="pv-textarea"
				rows="3"
				spellcheck="false"
				placeholder="Message castiel…"
				@keydown.enter.exact.prevent="send"
			/>
			<div class="pv-composer-footer">
				<span class="pv-context">
					{{ CONTEXT_USED.toFixed(1) }}%
					<svg viewBox="0 0 16 16" class="pv-context-ring">
						<circle cx="8" cy="8" r="6.5" class="pv-ring-track" />
						<circle
							cx="8"
							cy="8"
							r="6.5"
							class="pv-ring-value"
							:stroke-dasharray="`${2 * Math.PI * 6.5 * (CONTEXT_USED / 100)} 999`"
						/>
					</svg>
				</span>
				<button class="pv-chip" type="button">
					<Brain class="pv-chip-icon" />
					Reasoning: Default
				</button>
				<button class="pv-chip pv-chip-model" type="button">
					<Bot class="pv-chip-icon" />
					<span class="pv-chip-model-name">{{ MODEL_LABEL }}</span>
				</button>
				<button class="pv-chip" type="button">
					<LockOpen class="pv-chip-icon" />
					Loop
				</button>
				<button class="pv-send" type="button" :disabled="playing" @click="!playing && send()">
					<Square v-if="playing" class="pv-stop-icon" />
					<ArrowUp v-else />
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
	gap: 0.5rem;
	height: 2rem;
	padding: 0 0.75rem;
	border-bottom: 1px solid var(--p-border);
	flex-shrink: 0;
}

.pv-chat-title {
	flex: 1;
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
	gap: 0.25rem;
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
	white-space: pre-wrap;
	border-radius: 0.5rem;
	background: var(--p-primary);
	padding: 0.5rem 0.75rem;
	font-size: 0.75rem;
	line-height: 1.625;
	color: var(--p-primary-foreground);
}

.pv-msg-text {
	min-width: 0;
	max-width: 95%;
	align-self: flex-start;
	font-size: 0.75rem;
	line-height: 1.65;
	color: var(--p-foreground);
	word-break: break-word;
}

:deep(.pv-inline-code) {
	border: 1px solid var(--p-border);
	border-radius: 0.25rem;
	background: var(--p-muted);
	padding: 0 0.25rem;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 11px;
	color: var(--p-foreground);
}

.pv-inline-icon {
	width: 0.875rem;
	height: 0.875rem;
	flex-shrink: 0;
}

.pv-reasoning {
	display: flex;
	align-items: center;
	gap: 0.375rem;
	border: none;
	background: none;
	padding: 0;
	font-size: 0.75rem;
	color: var(--p-muted-foreground);
	cursor: default;
	align-self: flex-start;
}

.pv-reasoning:hover {
	color: var(--p-foreground);
}

.pv-reasoning-content {
	max-width: 95%;
	font-size: 0.75rem;
	line-height: 1.625;
	color: var(--p-muted-foreground);
}

.pv-chev {
	width: 0.75rem;
	height: 0.75rem;
	transition: transform 0.15s ease;
}

.pv-chev.open {
	transform: rotate(180deg);
}

.pv-tool {
	width: 100%;
	min-width: 0;
	align-self: flex-start;
}

.pv-tool-header {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	width: 100%;
	min-width: 0;
	border: none;
	background: none;
	padding: 0;
	text-align: left;
	font-size: 0.75rem;
	color: var(--p-muted-foreground);
	cursor: default;
}

.pv-tool-header:hover {
	color: var(--p-foreground);
}

.pv-tool-summary {
	min-width: 0;
	flex: 1;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.pv-tool-name {
	font-weight: 700;
	color: color-mix(in oklab, var(--p-foreground) 80%, var(--p-muted-foreground));
}
</style>

<style scoped>
.pv-tool-content {
	display: flex;
	flex-direction: column;
	gap: 0.25rem;
	padding: 0.375rem 0.25rem;
}

.pv-tool-param {
	display: flex;
	gap: 0.375rem;
	min-width: 0;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.6875rem;
	line-height: 1.5;
	color: color-mix(in oklab, var(--p-muted-foreground) 70%, transparent);
}

.pv-tool-key {
	flex-shrink: 0;
}

.pv-tool-value {
	min-width: 0;
	white-space: pre-wrap;
	word-break: break-word;
}

.pv-tool-result {
	margin: 0;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.6875rem;
	line-height: 1.5;
	color: color-mix(in oklab, var(--p-muted-foreground) 70%, transparent);
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
	flex-wrap: wrap;
	align-items: center;
	gap: 0.375rem;
	row-gap: 0.375rem;
	margin-top: 0.5rem;
}

.pv-context {
	display: inline-flex;
	align-items: center;
	gap: 0.25rem;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.6875rem;
	color: var(--p-muted-foreground);
	flex-shrink: 0;
}

.pv-context-ring {
	width: 0.875rem;
	height: 0.875rem;
	transform: rotate(-90deg);
}

.pv-ring-track {
	fill: none;
	stroke: var(--p-muted);
	stroke-width: 2.5;
}

.pv-ring-value {
	fill: none;
	stroke: var(--p-muted-foreground);
	stroke-width: 2.5;
	stroke-linecap: round;
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
	min-width: 0;
	overflow: hidden;
	flex-shrink: 1;
}

.pv-chip:hover {
	color: var(--p-foreground);
}

.pv-chip-icon {
	width: 0.875rem;
	height: 0.875rem;
	flex-shrink: 0;
}

.pv-chip-model {
	min-width: 0;
	margin-left: auto;
	overflow: hidden;
}

.pv-chip-model-name {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
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
	opacity: 0.75;
}

.pv-send svg {
	width: 0.875rem;
	height: 0.875rem;
}

.pv-stop-icon {
	fill: currentColor;
}
</style>



