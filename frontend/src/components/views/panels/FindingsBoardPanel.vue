<template>
	<EmptyHint
		v-if="findings.length === 0"
		:icon="Bug"
		message="No findings recorded yet."
		hint="Vulnerabilities confirmed during assessment will board here, draggable between stages."
	/>
	<KanbanBoard
		v-else
		:columns="columns"
		:items="findings"
		status-field="status"
		@change="onMove"
	>
		<template #card="{ item }">
			<div class="flex items-start justify-between gap-1.5">
				<p class="min-w-0 flex-1 text-xs font-medium break-words text-foreground">
					{{ item.title ?? item.name ?? "Untitled finding" }}
				</p>
				<span
					class="shrink-0 rounded px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide"
					:class="severityClass(item.severity)"
				>
					{{ item.severity ?? "?" }}
				</span>
			</div>
			<p v-if="affectedOf(item)" class="mt-1 truncate font-mono text-[10px] text-muted-foreground">
				{{ affectedOf(item) }}
			</p>
		</template>
	</KanbanBoard>
</template>

<script setup>
import { computed } from "vue";
import { Bug } from "@lucide/vue";
import KanbanBoard from "@/components/views/KanbanBoard.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({
	data: { type: Object, default: null },
	persist: { type: Function, required: true },
	refresh: { type: Function, required: true },
});

const BASE_STATUSES = [
	{ id: "new", label: "New" },
	{ id: "triaged", label: "Triaged" },
	{ id: "confirmed", label: "Confirmed" },
	{ id: "exploited", label: "Exploited" },
	{ id: "mitigated", label: "Mitigated" },
];

const findings = computed(() => props.data?.findings ?? []);

/** Statuses agents actually used appear even when they are outside the standard set. */
const columns = computed(() => {
	const used = new Set(findings.value.map((finding) => finding.status).filter(Boolean));
	for (const base of BASE_STATUSES) used.delete(base.id);
	return [
		...BASE_STATUSES,
		...[...used].map((status) => ({ id: status, label: status })),
	];
});

function affectedOf(finding) {
	return finding.target ?? finding.affected ?? finding.url ?? finding.host ?? "";
}

function severityClass(severity) {
	switch (String(severity).toLowerCase()) {
		case "critical":
			return "bg-red-500/15 text-red-400";
		case "high":
			return "bg-orange-500/15 text-orange-400";
		case "medium":
			return "bg-yellow-500/15 text-yellow-500";
		case "low":
			return "bg-sky-500/15 text-sky-400";
		default:
			return "bg-zinc-500/15 text-zinc-400";
	}
}

async function onMove(finding, newStatus) {
	const previousStatus = finding.status;
	finding.status = newStatus;
	try {
		await props.persist();
	} catch (error) {
		finding.status = previousStatus;
		console.error("Could not save finding status:", error.message);
		props.refresh();
	}
}
</script>