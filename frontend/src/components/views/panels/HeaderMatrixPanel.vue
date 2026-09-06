<template>
	<div class="flex h-full min-h-0 flex-col gap-4 p-2">
		<section class="flex min-h-0 flex-1 flex-col">
			<h3
				class="mb-2 shrink-0 border-b border-border/60 pb-1 text-xs font-semibold uppercase tracking-widest text-muted-foreground"
			>
				Security Headers
			</h3>
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
		</section>

		<section class="flex min-h-0 flex-1 flex-col">
			<h3
				class="mb-2 shrink-0 border-b border-border/60 pb-1 text-xs font-semibold uppercase tracking-widest text-muted-foreground"
			>
				Cookies
			</h3>
			<EmptyHint
				v-if="cookieRows.length === 0"
				:icon="Cookie"
				message="No cookies observed yet."
				hint="Cookies captured in Set-Cookie responses will be listed here with their flags."
			/>
			<DataTable
				v-else
				:columns="cookieColumns"
				:data="cookieRows"
				search-placeholder="Filter cookies…"
				empty-message="No cookies match the filter."
			/>
		</section>
	</div>
</template>

<script setup>
import { computed } from "vue";
import { Cookie, Globe } from "@lucide/vue";
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

/** Headers are a {name: value} map per site. */
function headerEntries(site) {
	return Object.entries(site.headers ?? {}).map(([name, value]) => ({ name, value: String(value) }));
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
		cell: ({ getValue }) => getValue() || "-",
	},
	{
		id: "present",
		header: "State",
		accessorFn: (row) => (row.present ? 1 : 0),
	},
];

const cookieRows = computed(() => {
	const result = [];
	for (const cookie of props.data?.cookies ?? []) {
		if (!cookie?.name) continue;
		result.push({
			name: cookie.name,
			site: cookie.site ?? "",
			flags: (cookie.flags ?? []).join(", "),
			purpose: cookie.purpose ?? "",
			discoveredBy: cookie.discovered_by ?? "",
		});
	}
	return result;
});

const cookieColumns = [
	{ accessorKey: "name", header: "Name" },
	{ accessorKey: "site", header: "Site" },
	{ accessorKey: "flags", header: "Flags", cell: ({ getValue }) => getValue() || "-" },
	{ accessorKey: "purpose", header: "Purpose", enableSorting: false, cell: ({ getValue }) => getValue() || "-" },
	{
		id: "discoveredBy",
		header: "Found Via",
		enableSorting: false,
		accessorFn: (row) => row.discoveredBy,
		cell: ({ getValue }) => getValue() || "-",
	},
];
</script>
