<template>
	<div class="flex h-full min-h-0 flex-col gap-1.5">
		<div v-if="searchable" class="relative shrink-0">
			<Search class="absolute left-2 top-1/2 size-3 -translate-y-1/2 text-muted-foreground" />
			<input v-model="globalFilter"
				class="h-7 w-full border-b bg-card pl-7 pr-2 text-xs text-foreground outline-none placeholder:text-muted-foreground focus:border-ring"
				:placeholder="searchPlaceholder" />
		</div>

		<ScrollArea class="min-h-0 flex-1">
			<!-- table-fixed plus explicit column widths keeps the layout stable while a
			     column is being dragged. min-w-full fills the panel; a larger total lets
			     the table grow past the edge and scroll sideways. -->
			<table class="table-fixed border-collapse text-xs" :style="tableStyle">
				<colgroup>
					<col v-for="column in leafColumns" :key="column.id" :style="{ width: `${column.getSize()}px` }" />
				</colgroup>
				<thead class="sticky top-0 z-10 bg-muted">
					<tr>
						<th v-for="header in headerGroup?.headers ?? []" :key="header.id"
							class="border-b px-2.5 py-1.5 text-left font-medium text-muted-foreground select-none"
							:class="header.column.getCanSort() ? 'cursor-pointer hover:text-foreground' : ''"
							@click="header.column.getToggleSortingHandler()?.($event)">
							<div class="relative flex items-center gap-1">
								<span class="inline-flex min-w-0 items-center gap-1">
									<FlexRender :render="header.column.columnDef.header" :props="header.getContext()" />
									<component :is="sortIcon(header.column)" v-if="header.column.getCanSort()" class="size-3 shrink-0" />
								</span>
								<span v-if="header.column.getCanResize()"
									class="absolute inset-y-0 -right-2.5 z-10 w-3 cursor-col-resize touch-none select-none after:absolute after:inset-y-0 after:left-1.5 after:w-px after:bg-transparent after:content-[''] hover:after:bg-foreground/40"
									:title="`Drag to resize ${columnLabel(header.column.id)}; double-click to reset`"
									@pointerdown.stop="header.getResizeHandler()($event)"
									@click.stop
									@dblclick.stop="resetColumn(header.column)" />
							</div>
						</th>
					</tr>
				</thead>
				<tbody>
					<tr v-for="row in table.getRowModel().rows" :key="row.id" class="hover:bg-muted/40" @click="emit('row-click', row.original)">
						<td v-for="cell in row.getVisibleCells()" :key="cell.id"
							class="border-b border-border/40 px-2.5 py-1.5 align-top text-foreground"
							:class="cell.column.getCanResize() ? 'overflow-hidden' : ''">
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
import { computed, ref, watch } from "vue";
import { ScrollArea } from "@/components/ui/scroll-area";

/**
 * Headless TanStack Table rendered with app styling: sortable and resizable headers plus
 * an optional global filter box. Custom cells come from `#cell-<columnId>` slots.
 *
 * Columns are sized in pixels and remember their widths per table, keyed by `tableId`.
 * A table fills its panel by default, and grows past the edge once the user widens
 * something, so the layout stays predictable without trapping widths in place.
 */
const props = defineProps({
	columns: { type: Array, required: true },
	data: { type: Array, required: true },
	/**
	 * Namespaces this table's saved column widths, so a widened column in one table does
	 * not distort another. Optional: without it, widths are simply not persisted rather
	 * than the component failing. Pass a stable id to get per-table widths back.
	 */
	tableId: { type: String, default: "" },
	/** Initial TanStack sorting state, e.g. [{ id: 'score', desc: true }]. */
	defaultSorting: { type: Array, default: () => [] },
	searchable: { type: Boolean, default: true },
	searchPlaceholder: { type: String, default: "Filter…" },
	emptyMessage: { type: String, default: "No entries yet." },
});

const emit = defineEmits(["row-click"]);

// Widths are saved per table so a widened column in one view does not distort another.
// With no tableId there is nothing to namespace against, so nothing is written.
const sizingKey = () => (props.tableId ? `castiel.tables.${props.tableId}` : "");

function loadSizing() {
	const key = sizingKey();
	if (!key) return {};
	try {
		const saved = JSON.parse(localStorage.getItem(key) ?? "{}");
		return saved && typeof saved === "object" ? saved : {};
	} catch {
		return {};
	}
}

const sorting = ref([...props.defaultSorting]);
const globalFilter = ref("");
const columnSizing = ref(loadSizing());
const columnSizingInfo = ref({
	columnSizingStart: [],
	deltaOffset: null,
	deltaPercentage: null,
	isResizingColumn: false,
	startOffset: null,
	startSize: null,
});

const table = useVueTable({
	get data() {
		return props.data;
	},
	columns: props.columns,
	getCoreRowModel: getCoreRowModel(),
	getSortedRowModel: getSortedRowModel(),
	getFilteredRowModel: getFilteredRowModel(),
	// onChange resizes live while dragging; onEnd would only apply the final width.
	columnResizeMode: "onChange",
	state: {
		get sorting() {
			return sorting.value;
		},
		get globalFilter() {
			return globalFilter.value;
		},
		get columnSizing() {
			return columnSizing.value;
		},
		get columnSizingInfo() {
			return columnSizingInfo.value;
		},
	},
	onSortingChange: (updater) => {
		sorting.value = typeof updater === "function" ? updater(sorting.value) : updater;
	},
	onGlobalFilterChange: (value) => {
		globalFilter.value = value;
	},
	onColumnSizingChange: (updater) => {
		columnSizing.value = typeof updater === "function" ? updater(columnSizing.value) : updater;
	},
	onColumnSizingInfoChange: (updater) => {
		columnSizingInfo.value = typeof updater === "function" ? updater(columnSizingInfo.value) : updater;
	},
});

// Widths update on every pointer move while dragging; only the settled size is worth saving.
watch(
	() => columnSizingInfo.value.isResizingColumn,
	(isResizing, wasResizing) => {
		if (wasResizing && !isResizing) persistSizing();
	},
);

const headerGroup = computed(() => table.getHeaderGroups()[0] ?? null);

// The rendered columns, in order; one <col> per leaf column drives the fixed layout.
const leafColumns = computed(() => headerGroup.value?.headers.map((header) => header.column) ?? []);

// The table is exactly as wide as its columns add up to, so dragging one column can
// only change that column. Deliberately no minWidth:100% here — a table narrower than
// its panel would otherwise be stretched, and the browser would hand the surplus to the
// other columns, so widening one shrank the rest.
const tableStyle = computed(() => ({ width: `${Math.max(table.getCenterTotalSize(), 0)}px` }));

function columnLabel(id) {
	const column = table.getColumn(id);
	const header = column?.columnDef?.header;
	return typeof header === "string" ? header : (id ?? "column");
}

function resetColumn(column) {
	column.resetSize();
	persistSizing();
}

function sortIcon(column) {
	const direction = column.getIsSorted();
	if (direction === "asc") return ArrowUp;
	if (direction === "desc") return ArrowDown;
	return ArrowUpDown;
}

function persistSizing() {
	const key = sizingKey();
	if (!key) return;
	try {
		localStorage.setItem(key, JSON.stringify(columnSizing.value));
	} catch {
		// Private mode or a full quota; widths simply do not survive a reload.
	}
}
</script>
