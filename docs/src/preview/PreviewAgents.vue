<script setup>
import { computed, onUnmounted, ref } from "vue";
import { Bot, SquareX, Trash2 } from "@lucide/vue";

const props = defineProps({
	agents: { type: Array, default: () => [] },
});

const emit = defineEmits(["cancel", "remove"]);

// One-second clock so live rows recompute their runtime without polling.
const nowSeconds = ref(Math.floor(Date.now() / 1000));
const ticker = setInterval(() => {
	nowSeconds.value = Math.floor(Date.now() / 1000);
}, 1000);
onUnmounted(() => clearInterval(ticker));

const runningCount = computed(() => props.agents.filter((agent) => agent.state === "RUNNING").length);

function isLive(agent) {
	return agent.state === "RUNNING";
}

function runtimeDisplay(agent) {
	const seconds = isLive(agent)
		? Math.max(0, nowSeconds.value - Math.floor(agent.startedAt / 1000))
		: agent.runtimeSeconds;
	return formatRuntime(seconds);
}

function formatRuntime(seconds) {
	const h = Math.floor(seconds / 3600);
	const m = Math.floor((seconds % 3600) / 60);
	const s = seconds % 60;
	if (h > 0) return `${h}h ${String(m).padStart(2, "0")}m ${String(s).padStart(2, "0")}s`;
	if (m > 0) return `${m}m ${String(s).padStart(2, "0")}s`;
	return `${s}s`;
}
</script>

<template>
	<!-- Replica of the bottom dock's Agents panel (frontend/src/views/AgentsView.vue). -->
	<section class="pv-agents">
		<div class="pv-agents-header">
			<span class="pv-agents-title">Agents</span>
			<span v-if="runningCount" class="pv-running-pill">{{ runningCount }} running</span>
			<span v-else class="pv-none-active">none active</span>
		</div>

		<div v-if="!agents.length" class="pv-agents-empty">
			<Bot class="pv-empty-icon" />
			<p class="pv-empty-msg">Sub-agents delegated by the agent appear here.</p>
		</div>

		<div v-else class="pv-agents-scroll">
			<table class="pv-table pv-agents-table">
				<colgroup>
					<col style="width: 24px" />
					<col style="width: 108px" />
					<col style="width: 58px" />
					<col style="width: 60px" />
					<col style="width: 70px" />
					<col />
					<col style="width: 30px" />
				</colgroup>
				<thead>
					<tr>
						<th></th>
						<th>ID</th>
						<th>Profile</th>
						<th>Runtime</th>
						<th>Status</th>
						<th>Task</th>
						<th></th>
					</tr>
				</thead>
				<tbody>
					<tr v-for="agent in agents" :key="agent.id + agent.key">
						<td>
							<span class="pv-agent-dot" :class="isLive(agent) ? 'live' : 'done'" />
						</td>
						<td><span class="pv-mono">{{ agent.display }}</span></td>
						<td class="pv-profile">{{ agent.profile }}</td>
						<td class="pv-profile pv-runtime">{{ runtimeDisplay(agent) }}</td>
						<td>
							<span v-if="isLive(agent)" class="pv-shimmer">{{ agent.activity }}</span>
							<span v-else class="pv-done-label">Done</span>
						</td>
						<td class="pv-profile">{{ agent.task }}</td>
						<td>
							<button
								v-if="isLive(agent)"
								type="button"
								class="pv-icon-btn"
								aria-label="Cancel run"
								@click="$emit('cancel', agent)"
							>
								<SquareX />
							</button>
							<button
								v-else
								type="button"
								class="pv-icon-btn"
								aria-label="Remove from tracking"
								@click="$emit('remove', agent)"
							>
								<Trash2 />
							</button>
						</td>
					</tr>
				</tbody>
			</table>
		</div>
	</section>
</template>

<style scoped>
.pv-agents {
	display: flex;
	flex-direction: column;
	height: 10.5rem;
	flex-shrink: 0;
	border-top: 1px solid var(--p-border);
	background: var(--p-background);
}

.pv-agents-header {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	height: 2rem;
	padding: 0 0.75rem;
	border-bottom: 1px solid var(--p-border);
	flex-shrink: 0;
}

.pv-agents-title {
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.pv-running-pill {
	border-radius: 999px;
	background: rgba(16, 185, 129, 0.15);
	padding: 0.125rem 0.5rem;
	font-size: 0.75rem;
	color: #34d399;
}

.pv-none-active {
	font-size: 0.75rem;
	color: #71717a;
}

.pv-agents-empty {
	display: flex;
	flex: 1;
	min-height: 0;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 0.5rem;
	text-align: center;
	padding: 0 1.5rem;
}

.pv-agents-scroll {
	min-height: 0;
	flex: 1;
	overflow-y: auto;
}

.pv-agents-table thead th {
	background: color-mix(in oklab, var(--p-background) 88%, black);
	color: #71717a;
	border-bottom: 1px solid color-mix(in oklab, var(--p-border) 60%, transparent);
	padding: 0.25rem 0.75rem;
}

.pv-agents-table tbody td {
	border-bottom: 1px solid color-mix(in oklab, var(--p-border) 60%, transparent);
	padding: 0.25rem 0.75rem;
	white-space: nowrap;
}

.pv-agent-dot {
	display: inline-block;
	width: 0.375rem;
	height: 0.375rem;
	border-radius: 999px;
}

.pv-agent-dot.live {
	background: #22c55e;
}

.pv-agent-dot.done {
	background: #52525b;
}

.pv-profile {
	color: #d4d4d8;
}

.pv-runtime {
	font-variant-numeric: tabular-nums;
	white-space: nowrap;
}

.pv-done-label {
	color: #a1a1aa;
}

.pv-icon-btn {
	display: grid;
	place-items: center;
	padding: 0.2rem;
	border: none;
	border-radius: 0.25rem;
	background: none;
	color: #71717a;
	cursor: default;
}

.pv-icon-btn svg {
	width: 0.8125rem;
	height: 0.8125rem;
}

.pv-icon-btn:hover {
	color: #f87171;
}
</style>
