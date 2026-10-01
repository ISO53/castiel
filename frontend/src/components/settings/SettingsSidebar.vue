<template>
	<nav class="flex w-56 shrink-0 flex-col border-r" aria-label="Settings sections">
		<!-- Search -->
		<div class="shrink-0 px-3 py-2.5">
			<InputGroup class="h-7">
				<InputGroupAddon>
					<Search class="size-3.5" />
				</InputGroupAddon>
				<InputGroupInput
					ref="searchInput"
					v-model="ui.query"
					placeholder="Search settings..."
					aria-label="Search settings"
					@keydown.down.prevent="move(1)"
					@keydown.up.prevent="move(-1)"
					@keydown.enter.prevent="selectCursor()"
				/>
				<InputGroupAddon v-if="ui.query" align="inline-end">
					<InputGroupButton aria-label="Clear search" @click="ui.query = ''">
						<X />
					</InputGroupButton>
				</InputGroupAddon>
			</InputGroup>
		</div>

		<!-- Navigation -->
		<ScrollArea class="min-h-0 flex-1">
			<div class="flex flex-col gap-0.5 px-2 pb-3">
				<template v-if="searching">
					<p class="px-2.5 py-1.5 text-[11px] text-muted-foreground">
						{{ results.length }} {{ results.length === 1 ? "match" : "matches" }}
					</p>
					<button
						v-for="(result, index) in results"
						:key="result.item.id"
						type="button"
						:ref="(el) => setRow(el, result.item.id)"
						class="flex flex-col items-start gap-0.5 rounded-md px-2.5 py-1.5 text-left text-xs transition-colors"
						:class="rowClass(result.item.id, index)"
						:aria-current="ui.activeId === result.item.id ? 'page' : undefined"
						@click="ui.select(result.item.id)"
						@mouseenter="cursor = index"
					>
						<span class="w-full truncate font-medium text-foreground">
							<span v-for="(part, i) in highlight(result.item.title, ui.query)" :key="i"
								:class="part.hit ? 'text-primary' : ''">{{ part.text }}</span>
						</span>
						<span v-if="result.item.parent" class="w-full truncate text-[11px] text-muted-foreground">
							<span v-for="(part, i) in highlight(result.item.parent.title, ui.query)" :key="i"
								:class="part.hit ? 'text-primary' : ''">{{ part.text }}</span>
						</span>
					</button>
					<EmptyState v-if="!results.length" text="No settings match your search.">
						<SearchX />
					</EmptyState>
				</template>

				<template v-else>
					<template v-for="node in SETTINGS_TREE" :key="node.id">
						<!-- Groups collapse to reveal their leaves; the rest are pages. -->
						<Collapsible v-if="node.children" v-model:open="openHeaders[node.id]">
							<CollapsibleTrigger as-child>
								<button type="button" class="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-xs font-medium text-foreground transition-colors hover:bg-muted">
									<component :is="node.icon" class="size-3.5 shrink-0" />
									<span class="truncate">{{ node.title }}</span>
										<ChevronRight
											class="ml-auto size-3.5 shrink-0 text-muted-foreground transition-transform duration-200"
											:class="openHeaders[node.id] ? 'rotate-90' : ''"
										/>
								</button>
							</CollapsibleTrigger>
							<CollapsibleContent>
								<div class="ml-3 flex flex-col gap-0.5 border-l pl-2">
									<button
										v-for="child in node.children"
										:key="child.id"
										type="button"
										:ref="(el) => setRow(el, child.id)"
										class="flex items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-xs transition-colors"
										:class="rowClass(child.id)"
										:aria-current="ui.activeId === child.id ? 'page' : undefined"
										@click="ui.select(child.id)"
										@mouseenter="cursor = flatRows.indexOf(child.id)"
									>
										<img v-if="logoOf(child)" :src="logoOf(child)" class="size-3.5 shrink-0" alt="" aria-hidden="true" />
										<span class="truncate">{{ child.title }}</span>
									</button>
								</div>
							</CollapsibleContent>
						</Collapsible>

						<button
							v-else
							type="button"
							:ref="(el) => setRow(el, node.id)"
							class="flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-xs font-medium text-foreground transition-colors"
							:class="rowClass(node.id)"
							:aria-current="ui.activeId === node.id ? 'page' : undefined"
							@click="ui.select(node.id)"
							@mouseenter="cursor = flatRows.indexOf(node.id)"
						>
							<component :is="node.icon" class="size-3.5 shrink-0" />
							<span class="truncate">{{ node.title }}</span>
						</button>
					</template>
			</template>
		</div>
	</ScrollArea>
	</nav>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ChevronRight, Search, SearchX, X } from "@lucide/vue";
import EmptyState from "@/components/EmptyState.vue";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group";
import { ScrollArea } from "@/components/ui/scroll-area";
import { highlight, searchSettings } from "@/lib/settings-search";
import { providerLogo } from "@/lib/provider-logos";
import { SETTINGS_ROWS, SETTINGS_TREE } from "@/settings/registry";
import { useSettingsUiStore } from "@/stores/settingsUi";

const ui = useSettingsUiStore();

const searching = computed(() => ui.searching);
const results = computed(() => (searching.value ? searchSettings(SETTINGS_ROWS, ui.query) : []));

/** Which grouped headers are expanded. Only headers that group leaves. */
const openHeaders = reactive(
	Object.fromEntries(SETTINGS_TREE.filter((node) => node.children).map((node) => [node.id, false])),
);

/** Every selectable row in the current mode, in visual order. */
const flatRows = computed(() => {
	if (searching.value) return results.value.map((result) => result.item.id);
	return SETTINGS_TREE.flatMap((node) =>
		node.children
			? openHeaders[node.id]
				? node.children.map((child) => child.id)
				: []
			: [node.id],
	);
});
const cursor = ref(0);
const searchInput = ref(null);
const rows = new Map();

// The dialog is the only thing that mounts this, so this is its open hook. The
// frame delay lands after the dialog's own focus handling, which is what we want.
onMounted(() => requestAnimationFrame(() => searchInput.value?.$el?.focus()));

// A leaf is only reachable through its header, so reveal it whenever it is active.
watch(
	() => ui.activeId,
	(id) => {
		const parent = SETTINGS_ROWS.find((row) => row.id === id)?.parent;
		if (parent) openHeaders[parent.id] = true;
	},
	{ immediate: true },
);

// The top hit takes the content pane as soon as the user types.
watch(results, (hits) => {
	cursor.value = 0;
	if (hits.length) ui.select(hits[0].item.id);
});

function logoOf(node) {
	return node.logo ? providerLogo(node.logo) : null;
}

function rowClass(id, index) {
	const active = ui.activeId === id;
	return [
		active ? "bg-accent text-accent-foreground" : "hover:bg-muted",
		searching.value && !active && index === cursor.value ? "bg-muted" : "",
	];
}

function setRow(el, id) {
	if (el) rows.set(id, el);
	else rows.delete(id);
}

function move(delta) {
	if (!flatRows.value.length) return;
	cursor.value = (cursor.value + delta + flatRows.value.length) % flatRows.value.length;
	rows.get(flatRows.value[cursor.value])?.scrollIntoView({ block: "nearest" });
}

function selectCursor() {
	const id = flatRows.value[cursor.value];
	if (id) ui.select(id);
}
</script>

