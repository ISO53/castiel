<script setup>
import { computed, onMounted, onUnmounted, watch } from "vue";
import { ChevronDown, PanelBottomClose, Trash2 } from "@lucide/vue";
import { Terminal } from "@/components/ai-elements/terminal";
import { Button } from "@/components/ui/button";
import { useDocksStore } from "@/stores/docks";
import { useProcessesStore } from "@/stores/processes";
import { useWorkspaceStore } from "@/stores/workspace";

const store = useProcessesStore();
const docks = useDocksStore();
const workspace = useWorkspaceStore();

onMounted(() => store.startPolling());
onUnmounted(() => store.stopPolling());

watch(
	() => workspace.cwd,
	() => store.reset(),
);

// When a process is selected the table collapses to just that row,
// handing the rest of the dock to the terminal pane.
const displayRows = computed(() => (store.selected ? [store.selected] : store.rows));

function stateDotClass(state) {
	switch (state) {
		case "RUNNING":
			return "bg-emerald-400";
		case "EXITED":
			return "bg-zinc-500";
		case "KILLED":
			return "bg-red-400";
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

function shortCommand(command) {
	const line = (command ?? "").replace(/\s+/g, " ").trim();
	return line.length > 70 ? line.slice(0, 67) + "..." : line;
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
			<Button size="icon" variant="ghost" class="size-6 text-muted-foreground hover:text-foreground"
				title="Hide dock" @click="docks.toggle('bottom')">
				<PanelBottomClose :size="14" />
			</Button>
		</div>

		<div v-if="!store.rows.length" class="flex flex-1 items-center justify-center text-sm text-zinc-600">
			Background processes started by the agent appear here.
		</div>

		<template v-else>
			<div :class="store.selected ? 'shrink-0 border-b border-zinc-800' : 'min-h-[35%] flex-1 overflow-auto'"
				@scroll.prevent>
				<table class="w-full text-left text-xs">
					<thead v-if="!store.selected" class="sticky top-0 bg-zinc-950 text-zinc-500">
						<tr>
							<th class="w-4 px-3 py-1.5"></th>
							<th class="px-3 py-1.5 font-medium">ID</th>
							<th class="px-3 py-1.5 font-medium">PID</th>
							<th class="px-3 py-1.5 font-medium">Runtime</th>
							<th class="px-3 py-1.5 font-medium">Purpose</th>
							<th class="px-3 py-1.5 font-medium">Command</th>
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
								<span class="block max-w-[16ch] truncate" :title="row.id">{{ shortId(row.id) }}</span>
							</td>
							<td class="px-3 py-1.5 font-mono text-zinc-400">{{ row.pid }}</td>
							<td class="whitespace-nowrap px-3 py-1.5 tabular-nums text-zinc-300">{{
								formatRuntime(row.runtimeSeconds) }}</td>
							<td class="max-w-[40ch] truncate px-3 py-1.5 text-zinc-300" :title="row.purpose">
								<span v-if="row.unread"
									class="mr-1 inline-block size-1.5 rounded-full align-middle bg-amber-400" />
								{{ shortCommand(row.purpose) }}
							</td>
							<td class="max-w-[50ch] truncate px-3 py-1.5 font-mono text-zinc-400" :title="row.command">
								{{ shortCommand(row.command) }}
							</td>
							<td class="px-3 py-0">
								<div class="flex h-7 items-center justify-end gap-1">
									<ChevronDown v-if="row.id === store.selectedId" :size="14" class="text-zinc-500" />
									<Button v-if="row.state !== 'RUNNING'" size="icon" variant="ghost"
										class="size-6 text-zinc-500 hover:text-red-400" title="Remove from tracking"
										@click.stop="store.remove(row.id)">
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
					:is-streaming="store.selected.state === 'RUNNING'" @clear="store.clearView" />
			</div>
		</template>
	</div>
</template>
