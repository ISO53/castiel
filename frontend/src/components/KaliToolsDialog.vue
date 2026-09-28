<template>
	<Dialog :open="open" @update:open="$emit('update:open', $event)">
		<DialogContent class="flex h-[85vh] max-h-[85vh] flex-col p-0 sm:max-w-4xl">
			<!-- Header -->
			<DialogHeader class="p-2.5">
				<div class="flex items-center justify-between pr-6">
					<div class="flex items-center gap-2.5">
						<img :src="kaliLogo" class="size-8 shrink-0" alt="" aria-hidden="true" />
						<div>
							<DialogTitle class="text-base font-semibold text-foreground">Kali Linux Tools</DialogTitle>
							<DialogDescription class="text-xs text-muted-foreground">
								{{ step === 1 ? "Select native tools to run against your engagement targets" : "Review selected tools and provide instructions for the agent" }}
							</DialogDescription>
						</div>
					</div>
				</div>
			</DialogHeader>

			<!-- Step 1: Browse & Select -->
			<div v-if="step === 1" class="flex min-h-0 flex-1 flex-col">
				<!-- Search & Filter Bar -->
				<div class="flex flex-wrap items-center justify-between gap-2 px-2.5 text-xs">
					<div class="relative flex-1 min-w-50">
						<Search class="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
						<input v-model="searchQuery"
							class="h-8 w-full rounded-md border bg-background pl-8 pr-7 text-xs text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none"
							placeholder="Search by tool name, command, keyword, or capability..."
							@input="onSearchInput" />
						<button v-if="searchQuery"
							class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
							@click="clearSearch">
							<X class="size-3" />
						</button>
					</div>

					<div class="flex items-center gap-2">
						<!-- Category Selector -->
						<select v-model="selectedCategory"
							class="h-8 rounded-md border bg-background px-2.5 text-xs text-foreground focus:border-ring focus:outline-none"
							@change="onFilterChange">
							<option value="">All Categories</option>
							<option v-for="cat in store.categories" :key="cat" :value="cat">
								{{ cat }}
							</option>
						</select>

						<!-- Installed Only Filter -->
						<label
							class="flex items-center gap-1.5 cursor-pointer text-xs text-muted-foreground hover:text-foreground select-none">
							<input v-model="installedOnly" type="checkbox"
								class="size-3.5 rounded border-muted accent-primary" @change="onFilterChange" />
							<span>Installed only</span>
						</label>

						<!-- Refresh Button -->
						<Button variant="outline" size="icon-sm" class="size-8"
							title="Refresh installed tools detection" :disabled="refreshing" @click="handleRefresh">
							<RefreshCw class="size-3.5" :class="{ 'animate-spin': refreshing }" />
						</Button>
					</div>
				</div>

				<!-- Tools Table Container -->
				<div class="flex-1 min-h-0 px-2.5 py-2.5 flex flex-col">
					<div class="h-full rounded-md border overflow-hidden flex flex-col bg-card/40">
						<ScrollArea class="h-full">
							<div v-if="store.loading"
								class="flex h-64 items-center justify-center text-xs text-muted-foreground">
								<RefreshCw class="mr-2 size-4 animate-spin text-primary" />
								<span>Searching Kali tools catalog…</span>
							</div>

							<div v-else-if="!filteredTools.length"
								class="flex h-64 flex-col items-center justify-center gap-2 text-xs text-muted-foreground">
								<p>No Kali tools found matching your search criteria.</p>
								<Button v-if="searchQuery || selectedCategory || installedOnly" variant="ghost"
									size="sm" @click="resetFilters">
									Reset Filters
								</Button>
							</div>

							<table v-else class="w-full table-fixed border-collapse text-left text-xs">
								<thead class="sticky top-0 z-10 border-b bg-muted select-none">
									<tr>
										<th class="w-10 px-2.5 py-2.5 text-center">
											<input type="checkbox"
												class="size-3.5 rounded border-muted accent-primary cursor-pointer"
												:checked="isAllVisibleSelected"
												:indeterminate.prop="isSomeVisibleSelected && !isAllVisibleSelected"
												title="Select / deselect all visible installed tools"
												@change="toggleSelectAllVisible" />
										</th>
										<th class="w-10 px-2 py-2 text-center" title="Host Installation Status">
											<HardDrive class="size-3.5 mx-auto text-muted-foreground" />
										</th>
										<th class="w-52 px-3 py-2 font-medium text-muted-foreground">Tool</th>
										<th class="w-40 px-3 py-2 font-medium text-muted-foreground">Categories</th>
										<th class="px-3 py-2 font-medium text-muted-foreground">Summary</th>
									</tr>
								</thead>
								<tbody class="divide-y divide-border/40">
									<tr v-for="tool in filteredTools" :key="tool.id" :class="[
										tool.installed
											? 'hover:bg-muted/30 cursor-pointer'
											: 'opacity-50 cursor-not-allowed bg-muted/10',
										store.isSelected(tool.id) ? 'bg-primary/5' : ''
									]" :title="!tool.installed ? 'This tool is not installed on your system. You can install it (e.g. sudo apt install ' + tool.id + ') and refresh.' : ''"
										@click="tool.installed && store.toggleSelect(tool.id)">
										<!-- Checkbox -->
										<td class="px-3 py-2 text-center" @click.stop>
											<input type="checkbox" class="size-3.5 rounded border-muted accent-primary"
												:disabled="!tool.installed" :checked="store.isSelected(tool.id)"
												:class="tool.installed ? 'cursor-pointer' : 'cursor-not-allowed'"
												@change="store.toggleSelect(tool.id)" />
										</td>

										<!-- Installed Icon -->
										<td class="px-2 py-2 text-center" @click.stop>
											<span v-if="tool.installed" title="Installed on host">
												<CheckCircle2 class="size-3.5 mx-auto text-foreground" />
											</span>
											<span v-else title="Not installed on this system">
												<CircleSlash class="size-3.5 mx-auto text-muted-foreground/40" />
											</span>
										</td>

										<!-- Tool Name & Docs -->
										<td class="px-3 py-2 align-top truncate">
											<div class="flex items-center gap-1.5 font-medium text-foreground">
												<span class="truncate" :title="tool.name">{{ tool.name }}</span>
												<a v-if="tool.docsUrl" :href="tool.docsUrl" target="_blank"
													rel="noopener noreferrer"
													class="text-muted-foreground/60 hover:text-foreground shrink-0"
													title="Open documentation" @click.stop>
													<ExternalLink class="size-3" />
												</a>
											</div>
											<div v-if="tool.commands?.length"
												class="mt-0.5 truncate font-mono text-[10px] text-muted-foreground"
												:title="tool.commands.join(', ')">
												{{ formatCommands(tool.commands) }}
											</div>
										</td>

										<!-- Categories -->
										<td class="px-3 py-2 align-top">
											<div class="flex flex-wrap gap-1">
												<span v-for="cat in tool.categories.slice(0, 2)" :key="cat"
													class="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
													{{ cat }}
												</span>
												<span v-if="tool.categories.length > 2"
													class="text-[10px] text-muted-foreground/60">
													+{{ tool.categories.length - 2 }}
												</span>
											</div>
										</td>

										<!-- Summary -->
										<td class="px-3 py-2 align-top text-xs text-muted-foreground leading-snug cursor-help"
											:title="tool.description || tool.summary">
											<p class="line-clamp-2" :title="tool.description || tool.summary">{{ tool.summary || tool.description }}</p>
										</td>
									</tr>
								</tbody>
							</table>
						</ScrollArea>
					</div>
				</div>

				<!-- Step 1 Footer -->
				<DialogFooter class="flex items-center justify-between px-2.5 pb-2.5">
					<div class="flex items-center gap-2 text-xs text-muted-foreground">
						<span class="font-medium text-foreground">{{ store.selectedIds.size }}</span>
						<span>tool(s) selected</span>
						<Button v-if="store.selectedIds.size > 0" variant="ghost" size="sm"
							class="h-6 px-1.5 text-[11px] text-muted-foreground hover:text-foreground"
							@click="store.clearSelection">
							Clear
						</Button>
					</div>

					<div class="flex items-center gap-2">
						<Button variant="outline" size="sm" @click="$emit('update:open', false)">
							Cancel
						</Button>
						<Button size="sm" :disabled="store.selectedIds.size === 0" @click="step = 2">
							<span>Next</span>
						</Button>
					</div>
				</DialogFooter>
			</div>

			<!-- Step 2: Review & User Notes -->
			<div v-else class="flex min-h-0 flex-1 flex-col px-2.5 pb-2.5 text-xs">
				<div class="mb-4">
					<h3 class="text-xs font-semibold text-foreground tracking-wide">
						Selected Tools ({{ selectedToolsList.length }})
					</h3>
					<p class="text-xs text-muted-foreground mt-0.5">
						These tools will be parameterized by the dispatch agent and launched as managed background
						processes.
					</p>

					<!-- Selected Tools Chips -->
					<div class="mt-2.5 flex flex-wrap gap-2 max-h-36 overflow-y-auto p-1">
						<div v-for="tool in selectedToolsList" :key="tool.id"
							class="flex items-center gap-1.5 rounded-md border bg-muted/30 px-2.5 py-1 text-xs">
							<span class="font-medium text-foreground">{{ tool.name }}</span>
							<span v-if="tool.commands?.length" class="font-mono text-[10px] text-muted-foreground">
								({{ tool.commands[0] }})
							</span>
							<button type="button" class="ml-1 text-muted-foreground hover:text-foreground"
								title="Remove tool" @click="store.toggleSelect(tool.id)">
								<X class="size-3" />
							</button>
						</div>
					</div>
				</div>

				<!-- User Instructions Textarea -->
				<div class="flex flex-1 flex-col min-h-0">
					<label for="kali-user-notes" class="text-xs font-semibold text-foreground tracking-wide">
						Parameters & Instructions (Optional)
					</label>
					<p class="text-xs text-muted-foreground mt-0.5 mb-2">
						Specify target preferences, ports, timing/stealth options, or specific flags. The agent reads
						the engagement scope and applies your notes.
					</p>

					<textarea id="kali-user-notes" v-model="store.userNotes" rows="6"
						class="w-full flex-1 rounded-md border bg-background p-3 font-sans text-xs text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none resize-none"
						placeholder="e.g. Target is 10.10.11.50. For nmap, run a fast SYN scan (-sS -F) and avoid aggressive timing. For gobuster, use common directory wordlists. Prefer stealth where possible." />
				</div>

				<!-- Launch Error Notice -->
				<div v-if="launchError"
					class="mt-3 rounded-md border border-destructive/50 bg-destructive/10 px-3 py-2 text-xs text-destructive">
					{{ launchError }}
				</div>

				<!-- Step 2 Footer -->
				<DialogFooter class="mt-2.5 flex items-center justify-between">
					<Button variant="outline" size="sm" :disabled="store.launching" @click="step = 1">
						<ArrowLeft class="mr-1 size-3.5" />
						<span>Back</span>
					</Button>

					<div class="flex items-center gap-2">
						<Button variant="outline" size="sm" :disabled="store.launching"
							@click="$emit('update:open', false)">
							Cancel
						</Button>
						<Button size="sm" :disabled="selectedToolsList.length === 0 || store.launching"
							@click="handleLaunch">
							<RefreshCw v-if="store.launching" class="mr-1.5 size-3.5 animate-spin" />
							<span>{{ store.launching ? "Dispatching..." : "Launch Background Tools" }}</span>
						</Button>
					</div>
				</DialogFooter>
			</div>
		</DialogContent>
	</Dialog>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import {
	ArrowLeft,
	CheckCircle2,
	CircleSlash,
	ExternalLink,
	HardDrive,
	RefreshCw,
	Search,
	X,
} from "@lucide/vue";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import kaliLogo from "@/assets/kali.svg";
import { useKaliToolsStore } from "@/stores/kaliTools";
import { useDocksStore } from "@/stores/docks";

const props = defineProps({
	open: { type: Boolean, required: true },
});

const emit = defineEmits(["update:open"]);

const store = useKaliToolsStore();
const docks = useDocksStore();

const step = ref(1);
const searchQuery = ref("");
const selectedCategory = ref("");
const installedOnly = ref(false);
const refreshing = ref(false);
const launchError = ref("");

let searchTimer = null;

onMounted(() => {
	store.fetchCategories();
	store.fetchTools();
});

watch(
	() => props.open,
	(isOpen) => {
		if (isOpen) {
			step.value = 1;
			launchError.value = "";
			if (!store.tools.length) {
				store.fetchTools();
			}
		}
	}
);

function onSearchInput() {
	clearTimeout(searchTimer);
	searchTimer = setTimeout(() => {
		store.fetchTools(searchQuery.value, selectedCategory.value, installedOnly.value);
	}, 200);
}

function onFilterChange() {
	store.fetchTools(searchQuery.value, selectedCategory.value, installedOnly.value);
}

function clearSearch() {
	searchQuery.value = "";
	onFilterChange();
}

function resetFilters() {
	searchQuery.value = "";
	selectedCategory.value = "";
	installedOnly.value = false;
	store.fetchTools();
}

function formatCommands(commands) {
	if (!commands || !commands.length) return "";
	if (commands.length <= 3) return commands.join(", ");
	return `${commands.slice(0, 3).join(", ")}, … (+${commands.length - 3})`;
}

async function handleRefresh() {
	refreshing.value = true;
	try {
		await store.refreshInstalled();
	} finally {
		refreshing.value = false;
	}
}

const filteredTools = computed(() => store.tools);
const selectedToolsList = computed(() => store.selectedTools);

const visibleInstalledTools = computed(() => {
	return filteredTools.value.filter((t) => t.installed);
});

const isAllVisibleSelected = computed(() => {
	if (!visibleInstalledTools.value.length) return false;
	return visibleInstalledTools.value.every((t) => store.isSelected(t.id));
});

const isSomeVisibleSelected = computed(() => {
	return visibleInstalledTools.value.some((t) => store.isSelected(t.id));
});

function toggleSelectAllVisible() {
	if (isAllVisibleSelected.value) {
		visibleInstalledTools.value.forEach((t) => {
			if (store.isSelected(t.id)) store.toggleSelect(t.id);
		});
	} else {
		visibleInstalledTools.value.forEach((t) => {
			if (!store.isSelected(t.id)) store.toggleSelect(t.id);
		});
	}
}

async function handleLaunch() {
	launchError.value = "";
	try {
		await store.launchTools(store.selectedIds, store.userNotes);
		emit("update:open", false);
		// Open the background processes dock so user immediately sees live activity
		docks.openView("bottom", "processes");
		store.clearSelection();
	} catch (e) {
		launchError.value = e.message || "Failed to launch tools";
	}
}
</script>
