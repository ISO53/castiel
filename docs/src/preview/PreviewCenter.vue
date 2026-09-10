<script setup>
import { computed, ref, watch } from "vue";
import { Network as NetworkIcon, Globe, Bug, Image, ListChecks, Server } from "@lucide/vue";
import { DOCUMENTS } from "./data.js";

const props = defineProps({
	activeDocId: { type: String, default: "network" },
	topologyReady: { type: Boolean, default: false },
});

const DOC_ICONS = { network: NetworkIcon, web: Globe, vulnerabilities: Bug, evidence: Image, tasks: ListChecks };

const SUBTABS = [
	{ id: "topology", label: "Topology" },
	{ id: "ports", label: "Ports & Services" },
];

const subTab = ref("topology");

watch(
	() => props.activeDocId,
	() => {
		subTab.value = "topology";
	},
);

const doc = computed(() => DOCUMENTS.find((d) => d.id === props.activeDocId) ?? DOCUMENTS[0]);
</script>

<template>
	<div class="pv-center">
		<div class="pv-tabs">
			<div class="pv-tab">
				<component :is="DOC_ICONS[doc.id]" class="pv-tab-icon" />
				{{ doc.label }}
			</div>
		</div>

		<div v-if="doc.id === 'network'" class="pv-subtabs">
			<button
				v-for="tab in SUBTABS"
				:key="tab.id"
				type="button"
				class="pv-subtab"
				:class="{ active: subTab === tab.id }"
				@click="subTab = tab.id"
			>
				{{ tab.label }}
			</button>
		</div>

		<div class="pv-view">
			<!-- Network · Topology -->
			<template v-if="doc.id === 'network' && subTab === 'topology'">
				<div v-if="topologyReady" class="pv-topo">
					<svg viewBox="0 0 640 300" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Network topology">
						<g class="pv-topo-edges">
							<line x1="320" y1="88" x2="140" y2="196" />
							<line x1="320" y1="88" x2="320" y2="196" />
							<line x1="320" y1="88" x2="500" y2="196" />
						</g>
						<text x="320" y="30" class="pv-topo-segment">10.0.0.0/24</text>
						<g class="pv-topo-node gateway">
							<rect x="250" y="40" width="140" height="48" rx="8" />
							<text x="320" y="61">10.0.0.1</text>
							<text x="320" y="76" class="muted">gateway · gw-01</text>
						</g>
						<g class="pv-topo-node">
							<rect x="70" y="196" width="140" height="48" rx="8" />
							<text x="140" y="217">10.0.0.15</text>
							<text x="140" y="232" class="muted">web-01 · 80, 443</text>
						</g>
						<g class="pv-topo-node">
							<rect x="250" y="196" width="140" height="48" rx="8" />
							<text x="320" y="217">10.0.0.23</text>
							<text x="320" y="232" class="muted">db-01 · 3306</text>
						</g>
						<g class="pv-topo-node">
							<rect x="430" y="196" width="140" height="48" rx="8" />
							<text x="500" y="217">10.0.0.42</text>
							<text x="500" y="232" class="muted">ops-01 · 22</text>
						</g>
					</svg>
				</div>
				<div v-else class="pv-empty">
					<NetworkIcon class="pv-empty-icon" />
					<p class="pv-empty-msg">{{ doc.empty.message }}</p>
					<p class="pv-empty-hint">{{ doc.empty.hint }}</p>
				</div>
			</template>

			<!-- Network · Ports & Services -->
			<div v-else-if="doc.id === 'network'" class="pv-empty">
				<Server class="pv-empty-icon" />
				<p class="pv-empty-msg">No services discovered yet.</p>
				<p class="pv-empty-hint">As scanning fills network.json, discovered ports and services will be listed here.</p>
			</div>

			<!-- Other documents: static empty states -->
			<div v-else class="pv-empty">
				<component :is="DOC_ICONS[doc.id]" class="pv-empty-icon" />
				<p class="pv-empty-msg">{{ doc.empty.message }}</p>
				<p class="pv-empty-hint">{{ doc.empty.hint }}</p>
			</div>
		</div>
	</div>
</template>

<style scoped>
.pv-center {
	display: flex;
	flex-direction: column;
	min-width: 0;
	min-height: 0;
	background: var(--p-background);
}

.pv-tabs {
	display: flex;
	height: 2rem;
	flex-shrink: 0;
	border-bottom: 1px solid var(--p-border);
	background: var(--p-card);
}

.pv-tab {
	display: flex;
	align-items: center;
	gap: 0.375rem;
	padding: 0 1rem;
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
	border-right: 1px solid var(--p-border);
}

.pv-tab-icon {
	width: 0.875rem;
	height: 0.875rem;
	color: var(--p-muted-foreground);
}

.pv-subtabs {
	display: flex;
	align-items: center;
	gap: 0.375rem;
	height: 2.5rem;
	padding: 0 0.75rem;
	border-bottom: 1px solid var(--p-border);
	flex-shrink: 0;
}

.pv-subtab {
	height: 1.75rem;
	padding: 0 0.75rem;
	border: 1px solid var(--p-border);
	border-radius: 0.375rem;
	background: none;
	font-size: 0.75rem;
	color: var(--p-muted-foreground);
	cursor: default;
	transition: color 0.15s ease;
}

.pv-subtab:hover {
	color: var(--p-foreground);
}

.pv-subtab.active {
	border-color: var(--p-border);
	background: var(--p-card);
	color: var(--p-foreground);
	font-weight: 500;
}

.pv-view {
	position: relative;
	flex: 1;
	min-height: 0;
}

.pv-topo {
	position: absolute;
 inset: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 1rem;
	animation: pv-fade 0.6s ease;
}

@keyframes pv-fade {
	from {
		opacity: 0;
		transform: translateY(6px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}

.pv-topo svg {
	width: 100%;
	height: 100%;
}

.pv-topo-edges line {
	stroke: var(--p-border);
	stroke-width: 1.5;
}

.pv-topo-node rect {
	fill: var(--p-card);
	stroke: var(--p-border);
}

.pv-topo-node.gateway rect {
	stroke: var(--p-primary);
}

.pv-topo-node text {
	fill: var(--p-foreground);
	font-size: 11px;
	text-anchor: middle;
	font-family: inherit;
}

.pv-topo-node text.muted {
	fill: var(--p-muted-foreground);
	font-size: 10px;
}

.pv-topo-segment {
	fill: var(--p-muted-foreground);
	font-size: 10px;
	text-anchor: middle;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
