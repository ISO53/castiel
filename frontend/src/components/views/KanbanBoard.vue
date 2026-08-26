<template>
	<div :key="renderKey" class="flex h-full min-h-0 overflow-x-auto pb-1">
		<section v-for="column in columnsWithItems" :key="column.id"
			class="flex h-full min-h-0 w-56 shrink-0 flex-col border-r bg-card">
			<header class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-2.5">
				<p class="truncate text-xs font-medium text-foreground">{{ column.label }}</p>
				<span class="rounded bg-muted px-1.5 py-0.5 text-[10px] tabular-nums text-muted-foreground">
					{{ column.items.length }}
				</span>
			</header>

			<div :ref="(el) => registerColumn(column.id, el)" :data-column="column.id"
				class="kanban-items min-h-0 flex-1 space-y-1.5 overflow-y-auto p-1.5">
				<article v-for="item in column.items" :key="String(keyOf(item))"
					class="cursor-grab bg-muted p-2 active:cursor-grabbing">
					<slot name="card" :item="item" />
				</article>
			</div>
		</section>
	</div>
</template>

<script setup>
import Sortable from "sortablejs";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

/**
 * Generic kanban board over an engagement document's item array. SortableJS owns DOM
 * movement; on every drop the whole board re-renders from props, so the parent must
 * apply the status change synchronously inside its `change` handler (then persist).
 *
 * Emits `change(item, newStatusId)`.
 */
const props = defineProps({
	columns: { type: Array, required: true }, // [{ id, label }]
	items: { type: Array, required: true },
	statusField: { type: String, default: "status" },
	keyFields: { type: Array, default: () => ["id", "title", "name"] },
});

const emit = defineEmits(["change"]);

const renderKey = ref(0);
const columnRefs = {};
const sortables = [];

function keyOf(item) {
	for (const field of props.keyFields) {
		if (item[field] !== undefined && item[field] !== null && item[field] !== "") return item[field];
	}
	return JSON.stringify(item);
}

const columnsWithItems = computed(() =>
	props.columns.map((column) => ({
		...column,
		items: props.items.filter((item) => (item[props.statusField] ?? props.columns[0]?.id) === column.id),
	})),
);

function registerColumn(columnId, element) {
	if (element) columnRefs[columnId] = element;
	else delete columnRefs[columnId];
}

function destroySortables() {
	while (sortables.length) sortables.pop().destroy();
}

async function mountSortables() {
	await nextTick();
	bindItemReferences();
	destroySortables();
	for (const [columnId, element] of Object.entries(columnRefs)) {
		sortables.push(
			new Sortable(element, {
				group: "engagement-board",
				animation: 150,
				fallbackClass: "opacity-50",
				onEnd: (event) => {
					const from = event.from.dataset.column;
					const to = event.to.dataset.column;
					if (from === to && event.oldIndex === event.newIndex) return;
					const dropped = event.item.__item;
					if (dropped) emit("change", dropped, to);
					renderKey.value += 1; // re-render from props; parent applies the change sync
				},
			}),
		);
	}
}

function bindItemReferences() {
	// Map rendered card DOM nodes back to their item objects for onEnd lookups.
	for (const [columnId, element] of Object.entries(columnRefs)) {
		const column = columnsWithItems.value.find((candidate) => candidate.id === columnId);
		const items = column?.items ?? [];
		element.__items = items;
		for (let index = 0; index < element.children.length; index++) {
			element.children[index].__item = items[index] ?? null;
		}
	}
}

watch(columnsWithItems, bindItemReferences, { flush: "post" });
watch(renderKey, mountSortables);

onMounted(mountSortables);
onBeforeUnmount(destroySortables);
</script>
