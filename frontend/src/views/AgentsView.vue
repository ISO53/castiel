<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { Bot, ChevronDown, SquareX, Trash2 } from "@lucide/vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { Shimmer } from "@/components/ai-elements/shimmer";
import { Button } from "@/components/ui/button";
import EmptyState from "@/components/EmptyState.vue";
import DataTable from "@/components/views/DataTable.vue";
import { useAgentsStore } from "@/stores/agents";
import { useWorkspaceStore } from "@/stores/workspace";
import { dotClass, dotTitle } from "@/lib/dock-dots";

const store = useAgentsStore();
const workspace = useWorkspaceStore();

// One-second clock so live rows recompute their runtime without server polling.
const nowSeconds = ref(Math.floor(Date.now() / 1000));
let ticker = null;

onMounted(() => {
	store.startFeed();
	ticker = setInterval(() => {
		nowSeconds.value = Math.floor(Date.now() / 1000);
	}, 1000);
});
onUnmounted(() => {
	store.stopFeed();
	clearInterval(ticker);
});

watch(
	() => workspace.cwd,
	() => store.reset(),
);

// When a run is selected the table collapses to just that row, handing the rest of
// the dock to the summary pane; the selected run keeps its row so the user can see
// what they are reading and close it again.
const displayRows = computed(() => (store.selected ? [store.selected] : store.rows));

// The harness's Activity enum, in the words the dock shows. Deliberately phase-only:
// no agent prose and no tool names cross the wire while a run works.
const ACTIVITY_LABELS = {
	WORKING: "Working",
	THINKING: "Thinking",
	STREAMING: "Streaming",
	TOOL_CALLING: "Tool calling",
};

function activityLabel(row) {
	return ACTIVITY_LABELS[row.activity] ?? "Working";
}

// A run is settled once it reaches a terminal state; only then does it have a summary
// worth opening, so a running row is not clickable.
function isLive(row) {
	return row.state === "RUNNING" || row.state === "QUEUED";
}

function isSettled(row) {
	return !isLive(row);
}

// Id of the row with a cancel request in flight; debounces double-clicks.
const cancellingId = ref(null);

async function cancelRun(row) {
	if (cancellingId.value) return;
	cancellingId.value = row.id;
	try {
		await store.cancel(row.id);
	} catch {
		// Harness unreachable or the run raced to finish; the change feed
		// refreshes the row either way, so there is nothing to surface here.
	} finally {
		if (cancellingId.value === row.id) cancellingId.value = null;
	}
}

// Opening the summary is a deliberate act, so a live row ignores the click.
function selectRow(row) {
	if (!isSettled(row)) return;
	store.select(row.id);
}

// Live runs show elapsed time computed from the harness-provided start timestamp
function runtimeDisplay(row) {
	const seconds =
		isLive(row) && row.startedAt
			? Math.max(0, nowSeconds.value - Math.floor(row.startedAt / 1000))
			: row.runtimeSeconds;
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

function shortId(id) {
	const text = id ?? "";
	return text.length > 16 ? text.slice(0, 9) + "…" + text.slice(-5) : text;
}

// Dock columns: no sorting, and the status dot and action buttons keep a fixed width
// so the row always reads the same way. Sizes seed the persisted layout.
const columns = [
	{ id: "dot", header: "", accessorFn: (row) => row.state, enableSorting: false, enableResizing: false, size: 28 },
	{ id: "id", header: "ID", accessorKey: "id", enableSorting: false, size: 110 },
	{ id: "profile", header: "Profile", accessorKey: "profile", enableSorting: false, size: 130 },
	{ id: "runtime", header: "Runtime", accessorFn: (row) => row.runtimeSeconds ?? 0, enableSorting: false, size: 100 },
	{ id: "status", header: "Status", accessorFn: (row) => row.state, enableSorting: false, size: 120 },
	{ id: "task", header: "Task", accessorKey: "task", enableSorting: false, size: 380 },
	{ id: "actions", header: "", accessorFn: () => "", enableSorting: false, enableResizing: false, size: 72 },
];

// The Status column shows a phase while a run works and a terminal state word once
// it settles. DONE reads as "Finished" because that is what it means to the user.
// The summary is not a status; it lives in the summary pane only.
const terminalStates = {
	DONE: "Done",
	FAILED: "Failed",
	CANCELLED: "Cancelled",
};

// The summary is a finished string, not a stream, so it renders through the same
// static path the chat view uses rather than the animated streaming renderer.
const summaryHtml = computed(() =>
	DOMPurify.sanitize(marked.parse(store.selectedSummary, { async: false })),
);
</script>

<template>
	<div class="flex h-full min-h-0 flex-col">
		<div class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-3">
			<div class="flex min-w-0 items-center gap-1.5">
				<span class="truncate text-xs font-medium text-foreground">Agents</span>
				<span v-if="store.runningCount"
					class="rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs text-emerald-400">
					{{ store.runningCount }} running
				</span>
				<span v-else class="text-xs text-zinc-500">none active</span>
			</div>
		</div>

		<EmptyState v-if="!store.rows.length" text="Sub-agents delegated by the agent appear here.">
			<Bot />
		</EmptyState>

		<template v-else>
			<DataTable
				:columns="columns"
				:data="displayRows"
				:selected-id="store.selectedId"
				:hide-header="!!store.selected"
				:searchable="false"
				:row-clickable="isSettled"
				:fill="!store.selected"
				variant="dock"
				table-id="agents"
				:class="store.selected ? 'shrink-0 border-b border-zinc-800' : 'min-h-[35%] flex-1'"
				@row-click="selectRow"
			>
				<template #cell-dot="{ row }">
					<span class="inline-block size-1.5 rounded-full" :class="dotClass(row.state)"
						:title="dotTitle(row.state)" />
				</template>
				<template #cell-id="{ value }">
					<span class="block truncate font-mono" :title="value">{{ shortId(value) }}</span>
				</template>
				<template #cell-profile="{ value }">
					<span class="block truncate text-zinc-300" :title="value">{{ value }}</span>
				</template>
				<template #cell-runtime="{ row }">
					<span class="block whitespace-nowrap tabular-nums text-zinc-300">{{ runtimeDisplay(row) }}</span>
				</template>
				<template #cell-status="{ row }">
					<Shimmer v-if="isLive(row)" as="span" :duration="2">
						{{ activityLabel(row) }}
					</Shimmer>
					<span v-else class="block truncate text-zinc-400">
						{{ terminalStates[row.state] ?? row.state }}
					</span>
				</template>
				<template #cell-task="{ value }">
					<span class="block truncate text-zinc-300" :title="value">{{ value }}</span>
				</template>
				<template #cell-actions="{ row }">
					<div class="flex h-6 items-center justify-end gap-1">
						<ChevronDown v-if="row.id === store.selectedId" :size="14" class="text-zinc-500" />
						<Button v-if="isLive(row)" size="icon" variant="ghost"
							class="size-6 text-zinc-500 hover:text-red-400" title="Cancel run"
							:disabled="cancellingId === row.id" @click.stop="cancelRun(row)">
							<SquareX :size="13" />
						</Button>
						<Button v-if="isSettled(row)" size="icon" variant="ghost"
							class="size-6 text-zinc-500 hover:text-red-400" title="Remove from tracking"
							@click.stop="store.remove(row.id)">
							<Trash2 :size="13" />
						</Button>
					</div>
				</template>
			</DataTable>

			<div v-if="store.selected" class="flex min-h-0 flex-1 flex-col overflow-auto p-3">
				<!-- No text-sm here: typeset-docs already sizes markdown to 0.75rem, and a
				     Tailwind size on the same element overrides it, making this pane drift
				     larger than the chat's. -->
				<div class="typeset typeset-docs min-w-0 flex-1 text-zinc-300" v-html="summaryHtml" />
				<p v-if="store.selected.error" class="mt-3 shrink-0 text-xs text-red-400/90">
					{{ store.selected.error }}
				</p>
			</div>
		</template>
	</div>
</template>
