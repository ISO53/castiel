<template>
	<DocumentShell doc-id="tasks" v-slot="{ data, persist, refresh }">
		<KanbanBoard v-if="normalizeTasks(data).length > 0" :columns="columnsFor(data)" :items="normalizeTasks(data)"
			status-field="status" @change="(task, newStatus) => onMove(data, persist, refresh, task, newStatus)">
			<template #card="{ item }">
				<p class="text-xs font-medium wrap-break-word text-foreground">{{ item.title ?? item.name ?? "Untitled task" }}</p>
				<p v-if="item.notes" class="mt-1 line-clamp-2 text-[10px] leading-snug text-muted-foreground">{{
					item.notes }}</p>
			</template>
		</KanbanBoard>
		<EmptyHint v-else :icon="ListChecks" message="No tasks planned yet."
			hint="The agent's engagement plan appears here once tasks.json fills in." />
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

/** Agents write free-form statuses ("complete", "open", …); map them onto the board. */
const STATUS_ALIASES = {
	new: "todo",
	planned: "todo",
	pending: "todo",
	open: "todo",
	doing: "in_progress",
	active: "in_progress",
	complete: "done",
	completed: "done",
	finished: "done",
	closed: "done",
};

function canonicalStatus(status) {
	const key = String(status ?? "").trim().toLowerCase().replace(/[\s-]+/g, "_");
	if (STATUS_ALIASES[key]) return STATUS_ALIASES[key];
	if (BASE_STATUSES.some((column) => column.id === key)) return key;
	return key || "todo";
}

/**
 * Normalizes statuses in place (idempotent) so every task lands on a column.
 * Called against the slot-provided document. Never through props.
 */
function normalizeTasks(data) {
	const list = data?.tasks ?? [];
	for (const task of list) {
		task.status = canonicalStatus(task.status);
	}
	return list;
}

/** Base columns plus any non-standard statuses agents came up with. */
function columnsFor(data) {
	const used = new Set(normalizeTasks(data).map((task) => task.status));
	const extras = [...used]
		.filter((status) => !BASE_STATUSES.some((column) => column.id === status))
		.map((status) => ({ id: status, label: status }));
	return [...BASE_STATUSES, ...extras];
}

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
