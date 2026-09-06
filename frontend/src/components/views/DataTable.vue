<template>
	<div class="flex h-full min-h-0 flex-col gap-1.5">
		<div v-if="searchable" class="relative shrink-0">
			<Search class="absolute left-2 top-1/2 size-3 -translate-y-1/2 text-muted-foreground" />
			<input v-model="globalFilter"
				class="h-7 w-full border-b bg-card pl-7 pr-2 text-xs text-foreground outline-none placeholder:text-muted-foreground focus:border-ring"
				:placeholder="searchPlaceholder" />
		</div>

		<ScrollArea class="min-h-0 flex-1">
			<table class="w-full border-collapse text-xs">
				<thead class="sticky top-0 z-10 bg-muted">
					<tr>
						<th v-for="header in headerGroup?.headers ?? []" :key="header.id"
							class="whitespace-nowrap border-b px-2.5 py-1.5 text-left font-medium text-muted-foreground select-none"
							:class="header.column.getCanSort() ? 'cursor-pointer hover:text-foreground' : ''"
							@click="header.column.getToggleSortingHandler()?.($event)">
							<span class="inline-flex items-center gap-1">
								<FlexRender :render="header.column.columnDef.header" :props="header.getContext()" />
								<component :is="sortIcon(header.column)" v-if="header.column.getCanSort()"
									class="size-3" />
							</span>
						</th>
					</tr>
				</thead>
				<tbody>
					<tr v-for="row in table.getRowModel().rows" :key="row.id" class="hover:bg-muted/40">
						<td v-for="cell in row.getVisibleCells()" :key="cell.id"
							class="border-b border-border/40 px-2.5 py-1.5 align-top text-foreground">
							<slot v-if="$slots['cell-' + cell.column.id]" :name="'cell-' + cell.column.id"
								:row="row.original" :value="cell.getValue()" />
							<FlexRender v-else :render="cell.column.columnDef.cell" :props="cell.getContext()" />
						</td>
					</tr>
					<tr v-if="table.getRowModel().rows.length === 0">
						<td :colspan="columns.length" class="px-2.5 py-6 text-center text-muted-foreground">
							{{ emptyMessage }}
						</td>
					</tr>
				</tbody>
			</table>
		</ScrollArea>
	</div>
</template>

<script setup>
import {
	FlexRender,
	getCoreRowModel,
	getFilteredRowModel,
	getSortedRowModel,
	useVueTable,
} from "@tanstack/vue-table";
import { ArrowDown, ArrowUp, ArrowUpDown, Search } from "@lucide/vue";
import { computed, ref } from "vue";
import { ScrollArea } from "@/components/ui/scroll-area";

/**
 * Headless TanStack Table rendered with app styling: sortable headers plus an optional
 * global filter box. Custom cells come from `#cell-<columnId>` slots.
 */
const props = defineProps({
	columns: { type: Array, required: true },
	data: { type: Array, required: true },
	searchable: { type: Boolean, default: true },
	searchPlaceholder: { type: String, default: "Filter…" },
	emptyMessage: { type: String, default: "No entries yet." },
});

const sorting = ref([]);
const globalFilter = ref("");

const table = useVueTable({
	get data() {
		return props.data;
	},
	columns: props.columns,
	getCoreRowModel: getCoreRowModel(),
	getSortedRowModel: getSortedRowModel(),
	getFilteredRowModel: getFilteredRowModel(),
	state: {
		get sorting() {
			return sorting.value;
		},
		get globalFilter() {
			return globalFilter.value;
		},
	},
	onSortingChange: (updater) => {
		sorting.value = typeof updater === "function" ? updater(sorting.value) : updater;
	},
	onGlobalFilterChange: (value) => {
		globalFilter.value = value;
	},
});

const headerGroup = computed(() => table.getHeaderGroups()[0] ?? null);

function sortIcon(column) {
	const direction = column.getIsSorted();
	if (direction === "asc") return ArrowUp;
	if (direction === "desc") return ArrowDown;
	return ArrowUpDown;
}
</script>
