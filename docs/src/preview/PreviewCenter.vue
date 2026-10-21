<script setup>
import { computed } from "vue";
import { Boxes, Bug, Image as ImageIcon, Network as NetworkIcon, Server } from "@lucide/vue";
import { DOCUMENTS } from "./data.js";
import PreviewTopology from "./PreviewTopology.vue";
import PreviewTechStack from "./PreviewTechStack.vue";
import PreviewVulns from "./PreviewVulns.vue";
import PreviewEvidence from "./PreviewEvidence.vue";

const props = defineProps({
	activeDocId: { type: String, default: "network" },
	subtab: { type: String, default: "topology" },
	docsReady: { type: Object, default: () => ({}) },
});

const emit = defineEmits(["select", "subtab"]);

const SUBTABS = {
	network: [
		{ id: "topology", label: "Topology" },
		{ id: "ports", label: "Ports & Services" },
	],
	web: [
		{ id: "sitemap", label: "Site Map" },
		{ id: "entrypoints", label: "Entry Points" },
		{ id: "endpoints", label: "Endpoints" },
		{ id: "tech", label: "Tech Stack" },
		{ id: "headers", label: "Headers" },
	],
};

const doc = computed(() => DOCUMENTS.find((d) => d.id === props.activeDocId) ?? DOCUMENTS[0]);
const subtabs = computed(() => SUBTABS[doc.value.id] ?? null);
</script>

<template>
	<div class="pv-center">
		<!-- The center is a tabbed view: one tab per engagement document. -->
		<div class="pv-tabs">
			<button
				v-for="item in DOCUMENTS"
				:key="item.id"
				type="button"
				class="pv-tab"
				:class="{ active: item.id === activeDocId }"
				@click="$emit('select', item.id)"
			>
				{{ item.label }}
			</button>
		</div>

		<div v-if="subtabs" class="pv-subtabs">
			<button
				v-for="tab in subtabs"
				:key="tab.id"
				type="button"
				class="pv-subtab"
				:class="{ active: subtab === tab.id }"
				@click="$emit('subtab', tab.id)"
			>
				{{ tab.label }}
			</button>
		</div>

		<div class="pv-view">
			<!-- Network · Topology -->
			<template v-if="doc.id === 'network' && subtab === 'topology'">
				<PreviewTopology v-if="docsReady.network" />
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

			<!-- Web App · Tech Stack -->
			<template v-else-if="doc.id === 'web' && subtab === 'tech'">
				<PreviewTechStack v-if="docsReady.web" />
				<div v-else class="pv-empty">
					<Boxes class="pv-empty-icon" />
					<p class="pv-empty-msg">No technologies fingerprinted yet.</p>
					<p class="pv-empty-hint">The service stack will assemble here. Frontend on top, infrastructure below.</p>
				</div>
			</template>

			<!-- Web App · other subtabs -->
			<div v-else-if="doc.id === 'web'" class="pv-empty">
				<Boxes class="pv-empty-icon" />
				<p class="pv-empty-msg">No sites or pages discovered yet.</p>
				<p class="pv-empty-hint">As recon fills web.json, the sitemap and tech stack will grow here.</p>
			</div>

			<!-- Vulnerabilities -->
			<template v-else-if="doc.id === 'vulnerabilities'">
				<PreviewVulns v-if="docsReady.vulnerabilities" />
				<div v-else class="pv-empty">
					<Bug class="pv-empty-icon" />
					<p class="pv-empty-msg">{{ doc.empty.message }}</p>
					<p class="pv-empty-hint">{{ doc.empty.hint }}</p>
				</div>
			</template>

			<!-- Evidence -->
			<template v-else>
				<PreviewEvidence v-if="docsReady.evidence" />
				<div v-else class="pv-empty">
					<ImageIcon class="pv-empty-icon" />
					<p class="pv-empty-msg">{{ doc.empty.message }}</p>
					<p class="pv-empty-hint">{{ doc.empty.hint }}</p>
				</div>
			</template>
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
	background: var(--p-background);
}

.pv-tab {
	display: flex;
	align-items: center;
	flex-shrink: 0;
	padding: 0 1.25rem;
	border: none;
	border-right: 1px solid var(--p-border);
	background: none;
	font-size: 0.75rem;
	white-space: nowrap;
	color: var(--p-muted-foreground);
	cursor: default;
	transition: color 0.15s ease, background-color 0.15s ease;
}

.pv-tab:hover {
	color: var(--p-foreground);
}

.pv-tab.active {
	background: var(--p-card);
	color: var(--p-foreground);
	font-weight: 500;
}

.pv-subtabs {
	display: flex;
	align-items: stretch;
	height: 2rem;
	flex-shrink: 0;
	border-bottom: 1px solid var(--p-border);
	background: var(--p-background);
}

.pv-subtab {
	display: flex;
	align-items: center;
	flex-shrink: 0;
	padding: 0 1.25rem;
	border: none;
	border-right: 1px solid var(--p-border);
	background: none;
	font-size: 0.75rem;
	white-space: nowrap;
	color: var(--p-muted-foreground);
	cursor: default;
	transition: color 0.15s ease, background-color 0.15s ease;
}

.pv-subtab:hover {
	color: var(--p-foreground);
}

.pv-subtab.active {
	background: var(--p-card);
	color: var(--p-foreground);
	font-weight: 500;
}

.pv-view {
	position: relative;
	flex: 1;
	min-height: 0;
}
</style>
