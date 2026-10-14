<template>
	<Dialog :open="open" @update:open="$emit('update:open', $event)">
		<DialogContent class="flex h-[85vh] max-h-[85vh] flex-col p-0 sm:max-w-5xl">
			<!-- Header -->
			<DialogHeader class="p-2.5">
				<div class="flex items-center justify-between pr-6">
					<div class="flex items-center gap-2.5">
						<MitreAttackLogo class="h-11 w-auto shrink-0 text-foreground" aria-hidden="true" />
						<div>
							<DialogTitle class="text-base font-semibold text-foreground">MITRE ATT&CK</DialogTitle>
							<DialogDescription class="text-xs text-muted-foreground">
								Browse adversary techniques. This is a read-only reference;
								the agent queries the same catalog.
								Bundled from <a href="https://attack.mitre.org">MITRE ATT&CK®</a>
							</DialogDescription>
						</div>
					</div>
				</div>
			</DialogHeader>

			<div class="flex min-h-0 flex-1 flex-col px-2.5 pb-2.5 text-xs">
				<!-- Search & filter bar -->
				<div class="flex flex-wrap items-center gap-2">
					<div class="relative min-w-50 flex-1">
						<Search class="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
						<input v-model="searchQuery"
							class="h-8 w-full rounded-md border bg-background pl-8 pr-7 text-xs text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none"
							placeholder="Search by technique id, name, keyword, or software..." @input="onSearchInput" />
						<button v-if="searchQuery"
							class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
							@click="clearSearch">
							<X class="size-3" />
						</button>
					</div>

					<select v-model="selectedTactic"
						class="h-8 rounded-md border bg-background px-2.5 text-xs text-foreground focus:border-ring focus:outline-none"
						@change="onFilterChange">
						<option value="">All Tactics</option>
						<option v-for="tactic in store.tactics" :key="tactic.shortname" :value="tactic.shortname">
							{{ tactic.name }}
						</option>
					</select>
				</div>

				<!-- Results + detail panes -->
				<div class="mt-2.5 flex min-h-0 flex-1">
					<!-- Results table -->
					<div class="flex w-100 shrink-0 flex-col overflow-hidden rounded-l-md border bg-card/40">
						<ScrollArea class="min-h-0 flex-1">
							<div v-if="store.loading"
								class="flex h-64 items-center justify-center text-xs text-muted-foreground">
								<RefreshCw class="mr-2 size-4 animate-spin text-primary" />
								<span>Searching the ATT&CK catalog…</span>
							</div>

							<div v-else-if="!store.techniques.length"
								class="flex h-64 flex-col items-center justify-center gap-2 text-xs text-muted-foreground">
								<p>No techniques found.</p>
								<Button v-if="searchQuery || selectedTactic" variant="ghost" size="sm" @click="resetFilters">
									Reset Filters
								</Button>
							</div>

							<table v-else class="w-full table-fixed border-collapse text-left text-xs">
								<thead class="sticky top-0 z-10 border-b bg-muted select-none">
									<tr>
										<th class="h-9 w-24 px-3 font-medium text-muted-foreground">ID</th>
										<th class="h-9 px-3 font-medium text-muted-foreground">Technique</th>
									</tr>
								</thead>
								<tbody class="divide-y divide-border/40">
									<tr v-for="technique in store.techniques" :key="technique.id" :class="[
										'cursor-pointer hover:bg-muted/30',
										store.selected?.id === technique.id ? 'bg-primary/5' : '',
									]" @click="store.select(technique.id)">
										<td class="px-3 py-2 font-mono text-muted-foreground">
											{{ technique.id }}
										</td>
										<td class="px-3 py-2">
											<div class="truncate font-medium text-foreground" :title="technique.name">
												{{ technique.name }}
											</div>
										</td>
									</tr>
								</tbody>
							</table>
						</ScrollArea>
					</div>

					<!-- Detail pane -->
					<div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-r-md border border-l-0 bg-card/40">
						<div v-if="store.selected" class="flex h-9 select-none items-center bg-muted px-3">
							<h3 class="text-xs font-medium text-muted-foreground">Summary</h3>
						</div>

						<div v-if="store.detailLoading" class="flex flex-1 items-center justify-center text-muted-foreground">
							<Spinner class="size-4" />
						</div>

						<div v-else-if="!store.selected"
							class="flex flex-1 items-center justify-center px-4 text-center text-muted-foreground">
							Select a technique to see its real-world procedures, associated tooling and detection
							notes.
						</div>

						<ScrollArea v-else class="min-h-0 flex-1">
							<div class="space-y-3 p-3 pr-2 leading-relaxed">
								<div class="flex items-center gap-2">
									<h3 class="min-w-0 truncate text-sm font-semibold text-foreground" :title="store.selected.name">
										{{ store.selected.name }}
									</h3>
									<a v-if="store.selected.url" :href="store.selected.url" target="_blank"
										rel="noopener noreferrer" title="Open on attack.mitre.org"
										class="shrink-0 text-muted-foreground/60 hover:text-foreground">
										<ExternalLink class="size-3" />
									</a>
								</div>
								<div class="flex flex-wrap gap-1">
									<span v-for="tactic in store.selected.tactics" :key="tactic"
										class="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
										{{ tacticName(tactic) }}
									</span>
									<span v-for="platform in store.selected.platforms" :key="platform"
										class="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
										{{ platform }}
									</span>
								</div>
								<div v-if="store.selected.permissionsRequired?.length" class="text-muted-foreground">
									Permissions required: {{ store.selected.permissionsRequired.join(", ") }}
								</div>
								<div v-if="store.selected.remoteSupport != null" class="text-muted-foreground">
									Remote execution: {{ store.selected.remoteSupport ? "yes" : "no" }}
								</div>

								<p v-if="store.selected.summary" class="text-foreground/90">{{ store.selected.summary }}</p>

								<div v-if="store.selected.procedures?.length">
									<h4 class="font-semibold tracking-wide text-foreground">Real-world procedures</h4>
									<ul class="mt-1 space-y-1.5">
										<li v-for="(procedure, index) in store.selected.procedures" :key="index"
											class="text-muted-foreground">
											<span class="font-medium text-foreground">{{ procedure.source }}:</span>
											{{ procedure.text }}
										</li>
									</ul>
								</div>

								<div v-if="store.selected.software?.length">
									<h4 class="font-semibold tracking-wide text-foreground">Associated software</h4>
									<div class="mt-1 flex flex-wrap gap-1">
										<span v-for="item in store.selected.software" :key="item"
											class="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
											{{ item }}
										</span>
									</div>
								</div>

								<div v-if="store.selected.detection">
									<h4 class="font-semibold tracking-wide text-foreground">Detection</h4>
									<p class="mt-1 text-muted-foreground">{{ store.selected.detection }}</p>
								</div>
							</div>
						</ScrollArea>
					</div>
				</div>
			</div>
		</DialogContent>
	</Dialog>
</template>


<script setup>
import { onMounted, ref, watch } from "vue";
import { ExternalLink, RefreshCw, Search, X } from "@lucide/vue";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Spinner } from "@/components/ui/spinner";
import MitreAttackLogo from "@/components/logos/MitreAttackLogo.vue";
import { useAttackStore } from "@/stores/attack";

const props = defineProps({ open: { type: Boolean, required: true } });
defineEmits(["update:open"]);

const store = useAttackStore();
const searchQuery = ref("");
const selectedTactic = ref("");
let searchTimer = null;

onMounted(() => {
	store.fetchTactics();
	store.fetchTechniques();
});

watch(
	() => props.open,
	(isOpen) => {
		if (isOpen && !store.techniques.length) {
			store.fetchTechniques();
		}
	}
);

function onSearchInput() {
	clearTimeout(searchTimer);
	searchTimer = setTimeout(() => store.fetchTechniques(searchQuery.value, selectedTactic.value), 200);
}

function onFilterChange() {
	store.fetchTechniques(searchQuery.value, selectedTactic.value);
}

function clearSearch() {
	searchQuery.value = "";
	onFilterChange();
}

function resetFilters() {
	searchQuery.value = "";
	selectedTactic.value = "";
	store.fetchTechniques();
}

function tacticName(shortname) {
	const tactic = store.tactics.find((candidate) => candidate.shortname === shortname);
	return tactic ? tactic.name : shortname;
}
</script>
