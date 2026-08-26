<template>
	<DocumentShell doc-id="tasks" v-slot="{ data, persist, refresh }">
		<KanbanBoard
			v-if="(data?.tasks ?? []).length > 0"
			:columns="columns"
			:items="data.tasks"
			@change="(task, newStatus) => onMove(data, persist, refresh, task, newStatus)"
		/>
		<EmptyHint
			v-else
			:icon="ListChecks"
			message="No tasks planned yet."
			hint="The agent's engagement plan appears here once tasks.json fills in."
		/>
	</DocumentShell>
</template>

<script setup>
import { ListChecks } from "@lucide/vue";
import DocumentShell from "@/components/views/DocumentShell.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";
import KanbanBoard from "@/components/views/KanbanBoard.vue";

const BASE_STATUSES = [
	{ id: "todo", label: "To Do" },
	{ id: "in_progress", label: "In Progress" },
	{ id: "blocked", label: "Blocked" },
	{ id: "done", label: "Done" },
];

const columns = BASE_STATUSES;

async function onMove(data, persist, refresh, task, newStatus) {
	const previousStatus = task.status;
	task.status = newStatus;
	try {
		await persist();
	} catch (error) {
		task.status = previousStatus;
		console.error("Could not save task status:", error.message);
		refresh();
	}
}
</script>