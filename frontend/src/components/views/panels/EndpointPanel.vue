<template>
	<div class="flex h-full min-h-0 flex-col gap-4 p-2">
		<section class="flex min-h-0 flex-1 flex-col">
			<h3
				class="mb-2 shrink-0 border-b border-border/60 pb-1 text-xs font-semibold uppercase tracking-widest text-muted-foreground"
			>
				API Endpoints
			</h3>
			<EmptyHint
				v-if="endpoints.length === 0"
				:icon="Network"
				message="No API endpoints discovered yet."
				hint="Probed endpoints with their status and auth requirements will be listed here."
			/>
			<DataTable
				v-else
				:columns="endpointColumns"
				:data="endpoints"
				search-placeholder="Filter endpoints…"
				empty-message="No endpoints match the filter."
			>
				<template #cell-endpoint="{ value, row }">
					<span class="font-mono text-[11px]">
						<span :class="methodClass(value)">{{ methodOf(value) || "" }}</span>
						{{ pathOf(value) }}
					</span>
				</template>
			</DataTable>
		</section>

		<section class="flex min-h-0 flex-1 flex-col">
			<h3
				class="mb-2 shrink-0 border-b border-border/60 pb-1 text-xs font-semibold uppercase tracking-widest text-muted-foreground"
			>
				Parameters
			</h3>
			<EmptyHint
				v-if="parameters.length === 0"
				:icon="ListFilter"
				message="No parameters discovered yet."
				hint="Observed and suspected request parameters will be listed here as test leads."
			/>
			<DataTable
				v-else
				:columns="parameterColumns"
				:data="parameters"
				search-placeholder="Filter parameters…"
				empty-message="No parameters match the filter."
			>
				<template #cell-status="{ value }">
					<span
						class="rounded px-1.5 py-0.5 text-[10px]"
						:class="value === 'tested' ? 'bg-emerald-500/15 text-emerald-400' : value === 'suspected' ? 'bg-amber-500/15 text-amber-400' : 'bg-muted text-muted-foreground'"
					>
						{{ value }}
					</span>
				</template>
			</DataTable>
		</section>
	</div>
</template>

<script setup>
import { computed } from "vue";
import { ListFilter, Network } from "@lucide/vue";
import DataTable from "@/components/views/DataTable.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

const endpoints = computed(() => {
	const result = [];
	for (const endpoint of props.data?.endpoints ?? []) {
		if (!endpoint?.endpoint) continue;
		result.push({
			endpoint: endpoint.endpoint,
			site: endpoint.site ?? "",
			status: endpoint.status != null ? String(endpoint.status) : "",
			auth: endpoint.auth ?? "unknown",
			info: [endpoint.summary, endpoint.notes].filter(Boolean).join(" — "),
			evidence: (endpoint.evidence ?? []).join(", "),
			discoveredBy: endpoint.discovered_by ?? "",
		});
	}
	return result;
});

const parameters = computed(() => {
	const result = [];
	for (const parameter of props.data?.parameters ?? []) {
		if (!parameter?.name) continue;
		result.push({
			name: parameter.name,
			endpoint: parameter.endpoint ?? "",
			location: parameter.location ?? "unknown",
			status: parameter.status ?? "observed",
			notes: parameter.notes ?? "",
			discoveredBy: parameter.discovered_by ?? "",
		});
	}
	return result;
});

function methodOf(endpointKey) {
	const method = (endpointKey ?? "").split(" ", 1)[0]?.toUpperCase() ?? "";
	return ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"].includes(method) ? method : "";
}

function pathOf(endpointKey) {
	const method = methodOf(endpointKey);
	return method ? (endpointKey ?? "").slice(method.length).trim() : endpointKey;
}

const METHOD_CLASSES = {
	GET: "text-emerald-400",
	POST: "text-amber-400",
	PUT: "text-sky-400",
	PATCH: "text-sky-400",
	DELETE: "text-red-400",
};

function methodClass(endpointKey) {
	return METHOD_CLASSES[methodOf(endpointKey)] ?? "text-sky-400";
}

const endpointColumns = [
	{ accessorKey: "endpoint", header: "Endpoint" },
	{ accessorKey: "status", header: "Status", cell: ({ getValue }) => getValue() || "—" },
	{ accessorKey: "auth", header: "Auth" },
	{
		id: "info",
		header: "Summary / Notes",
		enableSorting: false,
		accessorFn: (row) => row.info,
		cell: ({ getValue }) => getValue() || "—",
	},
	{
		id: "evidence",
		header: "Evidence",
		enableSorting: false,
		accessorFn: (row) => row.evidence,
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

const parameterColumns = [
	{ accessorKey: "name", header: "Name" },
	{ accessorKey: "location", header: "Location" },
	{ accessorKey: "endpoint", header: "Endpoint" },
	{ accessorKey: "status", header: "Status" },
	{ accessorKey: "notes", header: "Notes", enableSorting: false, cell: ({ getValue }) => getValue() || "—" },
	{
		id: "discoveredBy",
		header: "Found Via",
		enableSorting: false,
		accessorFn: (row) => row.discoveredBy,
		cell: ({ getValue }) => getValue() || "—",
	},
];
</script>