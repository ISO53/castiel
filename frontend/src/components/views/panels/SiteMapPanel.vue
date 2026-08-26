<template>
	<EmptyHint
		v-if="rows.length === 0"
		:icon="Globe"
		message="No sites or pages discovered yet."
		hint="Content-discovery results appear here as a table as web.json fills in."
	/>
	<DataTable
		v-else
		:columns="columns"
		:data="rows"
		search-placeholder="Filter URLs…"
		empty-message="No entries match the filter."
	/>
</template>

<script setup>
import { computed } from "vue";
import { Globe } from "@lucide/vue";
import DataTable from "@/components/views/DataTable.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

/** Sites and pages flatten into one row each; sites sort above their pages. */
const rows = computed(() => {
	const result = [];
	for (const site of props.data?.sites ?? []) {
		if (!site?.url) continue;
		result.push({
			kind: "site",
			url: site.url,
			status: "",
			info: [site.title, site.description, site.notes].filter(Boolean).join(" — "),
			discovered_by: site.discovered_by ?? "",
		});
	}
	for (const page of props.data?.pages ?? []) {
		if (!page?.url) continue;
		result.push({
			kind: "page",
			url: page.url,
			status: page.status ?? "",
			info: [page.purpose, page.title, page.notes].filter(Boolean).join(" — "),
			discovered_by: page.discovered_by ?? "",
		});
	}
	return result;
});

const columns = [
	{
		id: "kind",
		header: "Type",
		accessorFn: (row) => row.kind,
		cell: ({ getValue }) => getValue(),
		sortingFn: (a, b) => (a.original.kind === "site" ? -1 : 1) - (b.original.kind === "site" ? -1 : 1),
	},
	{
		id: "url",
		header: "URL",
		accessorFn: (row) => row.url,
		cell: ({ getValue }) => getValue(),
	},
	{
		id: "status",
		header: "Status",
		accessorFn: (row) => row.status,
		cell: ({ getValue }) => getValue() || "—",
	},
	{
		id: "info",
		header: "Info",
		enableSorting: false,
		accessorFn: (row) => row.info,
		cell: ({ getValue }) => getValue() || "—",
	},
	{
		id: "discoveredBy",
		header: "Found Via",
		enableSorting: false,
		accessorFn: (row) => row.discovered_by,
		cell: ({ getValue }) => getValue() || "—",
	},
];
</script>