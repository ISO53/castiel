<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { ChevronDown, SquareX, Terminal as TerminalIcon, Trash2 } from "@lucide/vue";
import { Terminal } from "@/components/ai-elements/terminal";
import { Button } from "@/components/ui/button";
import DataTable from "@/components/views/DataTable.vue";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import EmptyState from "@/components/EmptyState.vue";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useProcessesStore } from "@/stores/processes";
import { useWorkspaceStore } from "@/stores/workspace";
import { dotClass, dotTitle } from "@/lib/dock-dots";

const store = useProcessesStore();
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

// When a process is selected the table collapses to just that row,
// handing the rest of the dock to the terminal pane.
const displayRows = computed(() => (store.selected ? [store.selected] : store.rows));

// "Start a background process" dialog state.
const startDialogOpen = ref(false);
const startCommand = ref("");
const startDescription = ref("");
const startError = ref("");
const starting = ref(false);

const canStart = computed(() => startCommand.value.trim().length > 0 && !starting.value);

// Id of the row with a kill request in flight; debounces double-clicks.
const killingId = ref(null);

async function killProcess(row) {
	if (killingId.value) return;
	killingId.value = row.id;
	try {
		await store.kill(row.id);
	} catch {
		// Harness unreachable or the process raced to exit; the change feed
		// refreshes the row either way, so there is nothing to surface here.
	} finally {
		if (killingId.value === row.id) killingId.value = null;
	}
}

function openStartDialog() {
	startCommand.value = "";
	startDescription.value = "";
	startError.value = "";
	startDialogOpen.value = true;
}

async function startUserProcess() {
	if (!canStart.value) return;
	starting.value = true;
	startError.value = "";
	try {
		await store.startProcess(startCommand.value.trim(), startDescription.value.trim());
		startDialogOpen.value = false;
	} catch (error) {
		startError.value = error?.message ?? "Could not start the process.";
	} finally {
		starting.value = false;
	}
}

// Live runs show elapsed time computed from the harness-provided start timestamp
function runtimeDisplay(row) {
	const seconds =
		row.state === "RUNNING" && row.startedAt
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

// Dock columns: no sorting, and the status dot and action buttons keep a fixed width
// so the row always reads the same way. Sizes seed the persisted layout.
const columns = [
	{ id: "dot", header: "", accessorFn: (row) => row.state, enableSorting: false, enableResizing: false, size: 28 },
	{ id: "id", header: "ID", accessorKey: "id", enableSorting: false, size: 120 },
	{ id: "pid", header: "PID", accessorKey: "pid", enableSorting: false, size: 80 },
	{ id: "runtime", header: "Runtime", accessorFn: (row) => row.runtimeSeconds ?? 0, enableSorting: false, size: 100 },
	{ id: "purpose", header: "Purpose", accessorKey: "purpose", enableSorting: false, size: 260 },
	{ id: "command", header: "Command", accessorKey: "command", enableSorting: false, size: 320 },
	{ id: "actions", header: "", accessorFn: () => "", enableSorting: false, enableResizing: false, size: 72 },
];

function shortId(id) {
	const text = id ?? "";
	return text.length > 16 ? text.slice(0, 9) + "…" + text.slice(-5) : text;
}
</script>

<template>
	<div class="flex h-full min-h-0 flex-col">
		<div class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-3">
			<div class="flex min-w-0 items-center gap-1.5">
				<span class="truncate text-xs font-medium text-foreground">Processes</span>
				<span v-if="store.runningCount"
					class="rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs text-emerald-400">
					{{ store.runningCount }} running
				</span>
				<span v-else class="text-xs text-zinc-500">none active</span>
			</div>
			<div class="flex items-center gap-1">
				<Button size="icon" variant="ghost" class="size-6 text-muted-foreground hover:text-foreground"
					aria-label="Start a background process" title="Start a background process"
					@click="openStartDialog">
					<TerminalIcon :size="14" />
				</Button>
			</div>
		</div>

		<EmptyState v-if="!store.rows.length" text="Background processes started by you or the agent appear here.">
			<TerminalIcon />
		</EmptyState>

		<template v-else>
			<DataTable
				:columns="columns"
				:data="displayRows"
				:selected-id="store.selectedId"
				:hide-header="!!store.selected"
				:searchable="false"
				:fill="!store.selected"
				variant="dock"
				table-id="processes"
				:class="store.selected ? 'shrink-0 border-b border-zinc-800' : 'min-h-[35%] flex-1'"
				@row-click="(row) => store.select(row.id)"
			>
				<template #cell-dot="{ row }">
					<span class="inline-block size-1.5 rounded-full" :class="dotClass(row.state)"
						:title="dotTitle(row.state)" />
				</template>
				<template #cell-id="{ value }">
					<span class="block truncate font-mono" :title="value">{{ shortId(value) }}</span>
				</template>
				<template #cell-pid="{ value }">
					<span class="block truncate font-mono text-zinc-400">{{ value }}</span>
				</template>
				<template #cell-runtime="{ row }">
					<span class="block whitespace-nowrap tabular-nums text-zinc-300">{{ runtimeDisplay(row) }}</span>
				</template>
				<template #cell-purpose="{ value }">
					<span class="block truncate text-zinc-300" :title="value">{{ value }}</span>
				</template>
				<template #cell-command="{ value }">
					<span class="block truncate font-mono text-zinc-400" :title="value">{{ value }}</span>
				</template>
				<template #cell-actions="{ row }">
					<div class="flex h-6 items-center justify-end gap-1">
						<ChevronDown v-if="row.id === store.selectedId" :size="14" class="text-zinc-500" />
						<Button v-if="row.state === 'RUNNING'" size="icon" variant="ghost"
							class="size-6 text-zinc-500 hover:text-red-400" title="Kill process"
							:disabled="killingId === row.id" @click.stop="killProcess(row)">
							<SquareX :size="13" />
						</Button>
						<Button v-if="row.state !== 'RUNNING'" size="icon" variant="ghost"
							class="size-6 text-zinc-500 hover:text-red-400" title="Remove from tracking"
							@click.stop="store.remove(row.id)">
							<Trash2 :size="13" />
						</Button>
					</div>
				</template>
			</DataTable>

			<div v-if="store.selected" class="flex min-h-0 flex-1 flex-col overflow-hidden border-t border-zinc-800 p-1 pt-0">
				<Terminal class="min-h-0 flex-1" :output="store.selectedText"
					:is-streaming="store.selected.state === 'RUNNING'" @clear="store.clearView" />
			</div>
		</template>

		<Dialog :open="startDialogOpen" @update:open="startDialogOpen = $event">
			<DialogContent class="sm:max-w-md">
				<DialogHeader>
					<DialogTitle>Start a background process</DialogTitle>
					<DialogDescription>
						Runs in the workspace like any agent-started process: the agent sees it, can read
						its output, and manages it from there.
					</DialogDescription>
				</DialogHeader>
				<form class="flex flex-col gap-3" @submit.prevent="startUserProcess">
					<div class="flex flex-col gap-1.5">
						<Label for="process-command">Command</Label>
						<Input id="process-command" v-model="startCommand" class="font-mono" autocomplete="off"
							spellcheck="false" placeholder="nmap -sS 10.0.0.5" />
					</div>
					<div class="flex flex-col gap-1.5">
						<Label for="process-description">
							Explanation <span class="font-normal text-zinc-500">(optional)</span>
						</Label>
						<Textarea id="process-description" v-model="startDescription" :rows="3"
							placeholder="Why you started it. The agent will read this." />
					</div>
					<p v-if="startError" class="text-xs text-red-400">{{ startError }}</p>
					<DialogFooter>
						<Button type="button" variant="ghost" size="sm" @click="startDialogOpen = false">
							Cancel
						</Button>
						<Button type="submit" size="sm" :disabled="!canStart">
							{{ starting ? "Starting..." : "Start" }}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	</div>
</template>
