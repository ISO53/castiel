<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { Bot, ChevronDown, SquareX, Trash2 } from "@lucide/vue";
import { Terminal } from "@/components/ai-elements/terminal";
import { Button } from "@/components/ui/button";
import EmptyState from "@/components/EmptyState.vue";
import { useAgentsStore } from "@/stores/agents";
import { useWorkspaceStore } from "@/stores/workspace";

const store = useAgentsStore();
const workspace = useWorkspaceStore();

onMounted(() => store.startFeed());
onUnmounted(() => store.stopFeed());

watch(
	() => workspace.cwd,
	() => store.reset(),
);

// When a run is selected the table collapses to just that row,
// handing the rest of the dock to the transcript pane.
const displayRows = computed(() => (store.selected ? [store.selected] : store.rows));

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

function stateDotClass(state) {
	switch (state) {
		case "RUNNING":
			return "bg-emerald-400";
		case "QUEUED":
			return "bg-amber-400";
		case "DONE":
			return "bg-zinc-500";
		case "FAILED":
			return "bg-red-400";
		case "CANCELLED":
			return "bg-orange-400";
		default:
			return "bg-zinc-600";
	}
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

function shortTask(task) {
	const line = (task ?? "").replace(/\s+/g, " ").trim();
	return line.length > 70 ? line.slice(0, 67) + "..." : line;
}

function formatTokens(tokens) {
	return tokens === null || tokens === undefined ? "—" : String(tokens);
}
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
			<div :class="store.selected ? 'shrink-0 border-b border-zinc-800' : 'min-h-[35%] flex-1 overflow-auto'"
				@scroll.prevent>
				<table class="w-full text-left text-xs">
					<thead v-if="!store.selected" class="sticky top-0 bg-zinc-950 text-zinc-500">
						<tr>
							<th class="w-4 px-3 py-1.5"></th>
							<th class="px-3 py-1.5 font-medium">ID</th>
							<th class="px-3 py-1.5 font-medium">Profile</th>
							<th class="px-3 py-1.5 font-medium">Runtime</th>
							<th class="px-3 py-1.5 font-medium">Tokens</th>
							<th class="px-3 py-1.5 font-medium">Task</th>
							<th class="px-3 py-1.5"></th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="row in displayRows" :key="row.id"
							class="h-7 cursor-pointer border-t border-zinc-900 hover:bg-zinc-900/60"
							:class="row.id === store.selectedId ? 'bg-zinc-900' : ''"
							:title="row.id === store.selectedId ? 'Click to close' : ''" @click="store.select(row.id)">
							<td class="w-4 px-3 py-1.5">
								<span class="inline-block size-1.5 rounded-full" :class="stateDotClass(row.state)"
									:title="row.state.toLowerCase()" />
							</td>
							<td class="px-3 py-1.5 font-mono">
								<span class="block max-w-[14ch] truncate" :title="row.id">{{ shortId(row.id) }}</span>
							</td>
							<td class="px-3 py-1.5 text-zinc-300">{{ row.profile }}</td>
							<td class="whitespace-nowrap px-3 py-1.5 tabular-nums text-zinc-300">{{
								formatRuntime(row.runtimeSeconds) }}</td>
							<td class="px-3 py-1.5 tabular-nums text-zinc-400">{{ formatTokens(row.tokens) }}</td>
							<td class="max-w-[60ch] truncate px-3 py-1.5 text-zinc-300" :title="row.task">
								{{ shortTask(row.task) }}
							</td>
							<td class="px-3 py-0">
								<div class="flex h-7 items-center justify-end gap-1">
									<ChevronDown v-if="row.id === store.selectedId" :size="14" class="text-zinc-500" />
									<Button v-if="row.state === 'RUNNING' || row.state === 'QUEUED'" size="icon"
										variant="ghost" class="size-6 text-zinc-500 hover:text-red-400" title="Cancel run"
										:disabled="cancellingId === row.id" @click.stop="cancelRun(row)">
										<SquareX :size="13" />
									</Button>
									<Button v-if="row.state !== 'RUNNING' && row.state !== 'QUEUED'" size="icon"
										variant="ghost" class="size-6 text-zinc-500 hover:text-red-400"
										title="Remove from tracking" @click.stop="store.remove(row.id)">
										<Trash2 :size="13" />
									</Button>
								</div>
							</td>
						</tr>
					</tbody>
				</table>
			</div>

			<div v-if="store.selected" class="flex min-h-0 flex-1 flex-col p-1 pt-0">
				<Terminal class="min-h-0 flex-1" :output="store.selectedText"
					:is-streaming="store.selected.state === 'RUNNING' || store.selected.state === 'QUEUED'"
					@clear="store.clearView" />
			</div>
		</template>
	</div>
</template>
