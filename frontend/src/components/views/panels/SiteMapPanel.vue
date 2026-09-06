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

/** Sites, pages and directories flatten into one row each; sites sort above their pages. */
const rows = computed(() => {
	const result = [];
	for (const site of props.data?.sites ?? []) {
		if (!site?.url) continue;
		result.push({
			kind: site.kind ?? "site",
			url: site.url,
			status: site.status ?? "",
			auth: site.auth_scheme ?? "",
			info: [site.title, site.description, site.summary, site.notes].filter(Boolean).join(" — "),
			discoveredBy: site.discovered_by ?? "",
		});
	}
	for (const page of props.data?.pages ?? []) {
		if (!page?.url) continue;
		result.push({
			kind: "page",
			url: page.url,
			status: page.status != null ? String(page.status) : "",
			auth: page.auth ?? "",
			info: [page.purpose, page.title, page.notes].filter(Boolean).join(" — "),
			discoveredBy: page.discovered_by ?? "",
		});
	}
	for (const directory of props.data?.directories ?? []) {
		if (!directory?.path) continue;
		result.push({
			kind: "directory",
			url: directory.path,
			status: "",
			auth: "",
			info: [directory.type, directory.notes].filter(Boolean).join(" — "),
			discoveredBy: directory.discovered_by ?? "",
		});
	}
	return result;
});

const columns = [
	{
		id: "kind",
		header: "Type",
		accessorFn: (row) => row.kind,
	},
	{
		id: "url",
		header: "URL / Path",
		accessorFn: (row) => row.url,
	},
	{
		id: "status",
		header: "Status",
		accessorFn: (row) => row.status,
		cell: ({ getValue }) => getValue() || "—",
	},
	{
		id: "auth",
		header: "Auth",
		accessorFn: (row) => row.auth,
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
		accessorFn: (row) => row.discoveredBy,
		cell: ({ getValue }) => getValue() || "—",
	},
];
</script>