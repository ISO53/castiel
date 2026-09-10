<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import "@fontsource-variable/inter";
import "./preview.css";
import { CANNED_REPLIES, DEMO_SCRIPT } from "./data.js";
import PreviewMenuBar from "./PreviewMenuBar.vue";
import PreviewWorkspaceDock from "./PreviewWorkspaceDock.vue";
import PreviewCenter from "./PreviewCenter.vue";
import PreviewProcesses from "./PreviewProcesses.vue";
import PreviewChat from "./PreviewChat.vue";

const rootEl = ref(null);
const phase = ref(1);
const topologyReady = ref(false);
const processes = ref([]);
const activeDocId = ref("network");
const messages = ref([]);
const playing = ref(false);
const done = ref(false);

let token = 0;
let uid = 0;
let cannedIndex = 0;
let observer = null;

const reducedMotion =
	typeof window !== "undefined" &&
	(window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false);

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Sleeps unless a new run superseded this one (replay/unmount). */
async function wait(ms) {
	const ticket = token;
	await sleep(reducedMotion ? 0 : ms);
	return token === ticket;
}

function lastAssistantMessage() {
	const last = messages.value[messages.value.length - 1];
	if (last && last.role === "assistant") return last;
	const message = { id: ++uid, role: "assistant", parts: [] };
	messages.value.push(message);
	return message;
}

async function typeInto(part, text, ticket) {
	if (reducedMotion) {
		part.text = text;
		return;
	}
	for (let i = 0; i < text.length; i += 2) {
		if (token !== ticket) return;
		part.text = text.slice(0, i + 2);
		await sleep(14);
	}
	part.text = text;
}

async function runScript() {
	const ticket = ++token;
	playing.value = true;
	done.value = false;

	for (const beat of DEMO_SCRIPT) {
		if (token !== ticket) return;

		if (beat.t === "user") {
			messages.value.push({ id: ++uid, role: "user", text: beat.text });
			if (!(await wait(400))) return;
		} else if (beat.t === "reasoning") {
			const message = lastAssistantMessage();
			const part = { type: "reasoning", streaming: true, open: true, seconds: null, text: beat.text };
			message.parts.push(part);
			if (!(await wait(beat.seconds * 600))) return;
			part.seconds = beat.seconds;
			part.streaming = false;
			if (!(await wait(900))) return;
			part.open = false;
		} else if (beat.t === "text") {
			const message = lastAssistantMessage();
			const part = { type: "text", text: "" };
			message.parts.push(part);
			await typeInto(part, beat.text, ticket);
			if (!(await wait(250))) return;
		} else if (beat.t === "tool") {
			const message = lastAssistantMessage();
			const part = { type: "tool", name: beat.name, state: "running", input: beat.input, output: null, open: false };
			message.parts.push(part);
			if (beat.effect?.process) processes.value.push({ id: ++uid, name: beat.effect.process.name });
			if (!(await wait(beat.seconds * 1000))) return;
			part.state = "completed";
			part.output = beat.output;
			if (beat.effect?.topology) topologyReady.value = true;
			if (beat.effect?.clearProcesses) processes.value = [];
			if (!(await wait(350))) return;
		} else if (beat.t === "effect") {
			if (beat.phase) phase.value = beat.phase;
			if (beat.clearProcesses) processes.value = [];
			if (!(await wait(400))) return;
		} else if (beat.t === "pause") {
			if (!(await wait(beat.ms))) return;
		}
	}

	if (token !== ticket) return;
	playing.value = false;
	done.value = true;
}

async function cannedReply() {
	playing.value = true;
	const ticket = ++token;
	const message = lastAssistantMessage();
	const part = { type: "reasoning", streaming: true, open: false, seconds: null, text: "Demo mode — replies here are canned." };
	message.parts.push(part);
	if (!(await wait(900))) return;
	part.seconds = 1;
	part.streaming = false;
	const textPart = { type: "text", text: "" };
	message.parts.push(textPart);
	await typeInto(textPart, CANNED_REPLIES[cannedIndex++ % CANNED_REPLIES.length], ticket);
	playing.value = false;
}

function handleSubmit(text) {
	messages.value.push({ id: ++uid, role: "user", text });
	if (playing.value || !done.value) return;
	void cannedReply();
}

function restart() {
	token++;
	messages.value = [];
	phase.value = 1;
	topologyReady.value = false;
	processes.value = [];
	done.value = false;
	void runScript();
}

function killProcess(id) {
	processes.value = processes.value.filter((proc) => proc.id !== id);
}

onMounted(() => {
	observer = new IntersectionObserver(
		([entry]) => {
			if (!entry.isIntersecting) return;
			observer?.disconnect();
			observer = null;
			void runScript();
		},
		{ threshold: 0.3 },
	);
	observer.observe(rootEl.value);
});

onBeforeUnmount(() => {
	token++;
	observer?.disconnect();
});
</script>

<template>
	<section ref="rootEl" class="app-preview container preview-section" aria-label="castiel app demo">
		<div class="pv-frame">
			<PreviewMenuBar :phase="phase" />
			<div class="pv-body">
				<PreviewWorkspaceDock :active-doc-id="activeDocId" @select="activeDocId = $event" />
				<div class="pv-mid">
					<PreviewCenter :active-doc-id="activeDocId" :topology-ready="topologyReady" />
					<PreviewProcesses :processes="processes" @kill="killProcess" />
				</div>
				<PreviewChat
					:messages="messages"
					:playing="playing"
					:done="done"
					@submit="handleSubmit"
					@restart="restart"
				/>
			</div>
		</div>
	</section>
</template>

<style scoped>
.preview-section {
	margin-block: 2rem 6rem;
	scroll-margin-top: 5rem;
}
</style>
