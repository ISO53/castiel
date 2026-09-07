<template>
	<DocumentShell doc-id="vulnerabilities" v-slot="{ data, persist }">
		<div class="flex h-full min-h-0 flex-col">
			<VulnerabilityGaugePanel
				:vulnerabilities="vulnerabilitiesOf(data)"
				:selected-band="filters.band"
				@select-band="toggleBand"
			/>
			<VulnerabilityTablePanel
				class="min-h-0 flex-1 border-t"
				:vulnerabilities="vulnerabilitiesOf(data)"
				:filters="filters"
				@set-filter="setFilter"
				@open="selected = $event"
			/>
			<VulnerabilityDetailDialog :finding="selected" :persist="persist" @close="selected = null" />
		</div>
	</DocumentShell>
</template>

<script setup>
import { ref } from "vue";
import DocumentShell from "@/components/views/DocumentShell.vue";
import VulnerabilityGaugePanel from "@/components/views/panels/VulnerabilityGaugePanel.vue";
import VulnerabilityTablePanel from "@/components/views/panels/VulnerabilityTablePanel.vue";
import VulnerabilityDetailDialog from "@/components/views/panels/VulnerabilityDetailDialog.vue";

const filters = ref({ search: "", proof: "all", band: "all", target: "all" });
const selected = ref(null);

function vulnerabilitiesOf(data) {
	return data?.vulnerabilities ?? [];
}

function toggleBand(bandId) {
	filters.value.band = filters.value.band === bandId ? "all" : bandId;
}

function setFilter({ key, value }) {
	filters.value[key] = value;
}
</script>
