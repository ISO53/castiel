<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import "@fontsource-variable/inter";
import "./preview.css";
import { CANNED_REPLIES, DEMO_SCRIPT, SUB_AGENT_RUNS } from "./data.js";
import PreviewMenuBar from "./PreviewMenuBar.vue";
import PreviewWorkspaceDock from "./PreviewWorkspaceDock.vue";
import PreviewCenter from "./PreviewCenter.vue";
import PreviewAgents from "./PreviewAgents.vue";
import PreviewDockBar from "./PreviewDockBar.vue";
import PreviewChat from "./PreviewChat.vue";

const DEFAULT_SUBTABS = { network: "topology", web: "sitemap" };

const rootEl = ref(null);
const phase = ref(1);
const activeDocId = ref("network");
const subtab = ref("topology");
const docsReady = ref({ network: false, web: false, vulnerabilities: false, evidence: false });
const agents = ref([]);
const messages = ref([]);
const playing = ref(false);
const done = ref(false);

let token = 0;
let uid = 0;
let cannedIndex = 0;
let observer = null;
let loopTimer = null;

const reducedMotion =
	(typeof window !== "undefined" && window.location.search.includes("instant")) ||
	(typeof window !== "undefined" &&
		(window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false));

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
	for (let i = 0; i < text.length; i += 3) {
		if (token !== ticket) return;
		part.text = text.slice(0, i + 3);
		await sleep(6);
	}
	part.text = text;
}

function spawnAgent(key) {
	const run = SUB_AGENT_RUNS[key];
	if (!run) return;
	agents.value.push({
		key,
		id: run.id,
		display: run.display,
		profile: "worker",
		task: run.task,
		state: "RUNNING",
		activity: "Thinking",
		startedAt: Date.now() - run.seed * 1000,
		runtimeSeconds: run.seed,
	});
}

function settleAgent(key) {
	const agent = agents.value.find((candidate) => candidate.key === key);
	if (!agent || agent.state !== "RUNNING") return;
	agent.state = "DONE";
	agent.runtimeSeconds = Math.floor((Date.now() - agent.startedAt) / 1000);
}

function cancelAgent(agent) {
	if (agent.state !== "RUNNING") return;
	agent.state = "DONE";
	agent.activity = "";
	agent.runtimeSeconds = Math.floor((Date.now() - agent.startedAt) / 1000);
}

function removeAgent(agent) {
	agents.value = agents.value.filter((candidate) => candidate !== agent);
}

function applyEffect(effect) {
	if (!effect) return;
	if (effect.agent) spawnAgent(effect.agent);
	if (effect.agentDone) settleAgent(effect.agentDone);
	if (effect.doc) {
		docsReady.value[effect.doc] = true;
		activeDocId.value = effect.doc;
		subtab.value = effect.subtab ?? DEFAULT_SUBTABS[effect.doc] ?? subtab.value;
	}
}

function selectDoc(id) {
	activeDocId.value = id;
	subtab.value = DEFAULT_SUBTABS[id] ?? subtab.value;
}

/** The demo restarts on its own so the landing page keeps showing a live session. */
function scheduleLoop() {
	clearTimeout(loopTimer);
	loopTimer = setTimeout(() => {
		void restart();
	}, 12000);
}

async function runScript() {
	const ticket = ++token;
	playing.value = true;
	done.value = false;

	for (const beat of DEMO_SCRIPT) {
		if (token !== ticket) return;

		if (beat.t === "user") {
			messages.value.push({ id: ++uid, role: "user", text: beat.text });
			if (!(await wait(200))) return;
		} else if (beat.t === "reasoning") {
			const message = lastAssistantMessage();
			const part = { type: "reasoning", streaming: true, open: true, seconds: null, text: beat.text };
			message.parts.push(part);
			if (!(await wait(beat.seconds * 250))) return;
			part.seconds = beat.seconds;
			part.streaming = false;
			if (!(await wait(300))) return;
			part.open = false;
		} else if (beat.t === "text") {
			const message = lastAssistantMessage();
			const part = { type: "text", text: "" };
			message.parts.push(part);
			await typeInto(part, beat.text, ticket);
			applyEffect(beat.effect);
			if (!(await wait(120))) return;
		} else if (beat.t === "tool") {
			const message = lastAssistantMessage();
			const part = {
				type: "tool",
				name: beat.name,
				state: "running",
				input: beat.input ?? {},
				output: null,
				open: Boolean(beat.stayOpen),
			};
			message.parts.push(part);
			if (!(await wait(beat.seconds * 500))) return;
			part.state = "completed";
			part.output = beat.output;
			applyEffect(beat.effect);
			if (!(await wait(150))) return;
		} else if (beat.t === "pause") {
			if (!(await wait(beat.ms * 0.4))) return;
		}
	}

	if (token !== ticket) return;
	playing.value = false;
	done.value = true;
	scheduleLoop();
}

async function cannedReply() {
	playing.value = true;
	const ticket = ++token;
	const message = lastAssistantMessage();
	const part = { type: "reasoning", streaming: true, open: false, seconds: null, text: "Reviewing the workspace state before answering." };
	message.parts.push(part);
	if (!(await wait(300))) return;
	part.seconds = 1;
	part.streaming = false;
	const textPart = { type: "text", text: "" };
	message.parts.push(textPart);
	await typeInto(textPart, CANNED_REPLIES[cannedIndex++ % CANNED_REPLIES.length], ticket);
	playing.value = false;
	scheduleLoop();
}

function handleSubmit(text) {
	messages.value.push({ id: ++uid, role: "user", text });
	if (playing.value || !done.value) return;
	void cannedReply();
}

function restart() {
	clearTimeout(loopTimer);
	token++;
	messages.value = [];
	phase.value = 1;
	activeDocId.value = "network";
	subtab.value = "topology";
	docsReady.value = { network: false, web: false, vulnerabilities: false, evidence: false };
	agents.value = [];
	done.value = false;
	void runScript();
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
	clearTimeout(loopTimer);
	observer?.disconnect();
});
</script>

<template>
	<section ref="rootEl" class="app-preview container preview-section" aria-label="castiel app demo">
		<div class="pv-frame">
			<PreviewMenuBar :phase="phase" />
			<div class="pv-body">
				<PreviewWorkspaceDock :active-doc-id="activeDocId" @select="selectDoc" />
				<div class="pv-mid">
					<PreviewCenter
						:active-doc-id="activeDocId"
						:subtab="subtab"
						:docs-ready="docsReady"
						@select="selectDoc"
						@subtab="subtab = $event"
					/>
					<PreviewAgents :agents="agents" @cancel="cancelAgent" @remove="removeAgent" />
				</div>
				<PreviewChat :messages="messages" :playing="playing" :done="done" @submit="handleSubmit" />
			</div>
			<PreviewDockBar />
		</div>
	</section>
</template>

<style scoped>
.preview-section {
	margin-block: 2rem 6rem;
	scroll-margin-top: 5rem;
}
</style>

