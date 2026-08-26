<template>
	<EmptyHint
		v-if="sites.length === 0"
		:icon="Globe"
		message="No HTTP security headers collected yet."
		hint="Response headers captured during recon will build this matrix automatically."
	/>
	<DataTable
		v-else
		:columns="columns"
		:data="rows"
		search-placeholder="Filter site or header…"
		empty-message="No entries match the filter."
	>
		<template #cell-present="{ value }">
			<span v-if="value" class="font-semibold text-green-500">✓ present</span>
			<span v-else class="text-muted-foreground/60">✗ missing</span>
		</template>
	</DataTable>
</template>

<script setup>
import { computed } from "vue";
import DataTable from "@/components/views/DataTable.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

const SECURITY_HEADERS = [
	"Strict-Transport-Security",
	"Content-Security-Policy",
	"X-Content-Type-Options",
	"X-Frame-Options",
	"Referrer-Policy",
	"Permissions-Policy",
];

const sites = computed(() => (props.data?.sites ?? []).filter((site) => site?.url));

/** Normalizes header entries that may be {name|key, value} pairs or plain strings. */
function headerEntries(site) {
	return (site.headers ?? [])
		.map((entry) => {
			if (typeof entry === "string") return { name: entry, value: "" };
			return { name: entry.name ?? entry.key ?? "", value: entry.value ?? "" };
		})
		.filter((entry) => entry.name);
}

const rows = computed(() => {
	const result = [];
	for (const site of sites.value) {
		const captured = new Map(headerEntries(site).map((entry) => [entry.name.toLowerCase(), entry.value]));
		for (const header of SECURITY_HEADERS) {
			const value = captured.get(header.toLowerCase());
			result.push({ site: site.url, header, value: value ?? "", present: value !== undefined });
		}
		captured.forEach((value, name) => {
			if (!SECURITY_HEADERS.some((standard) => standard.toLowerCase() === name)) {
				result.push({ site: site.url, header: `${name} (extra)`, value, present: true });
			}
		});
	}
	return result;
});

const columns = [
	{
		id: "site",
		header: "Site",
		accessorFn: (row) => row.site,
	},
	{
		id: "header",
		header: "Header",
		accessorFn: (row) => row.header,
	},
	{
		id: "value",
		header: "Value",
		enableSorting: false,
		accessorFn: (row) => row.value,
		cell: ({ getValue }) => getValue() || "—",
	},
	{
		id: "present",
		header: "State",
		accessorFn: (row) => (row.present ? 1 : 0),
	},
];
</script>