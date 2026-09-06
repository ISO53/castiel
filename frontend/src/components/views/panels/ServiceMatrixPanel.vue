<template>
	<EmptyHint v-if="rows.length === 0" :icon="Network" message="No open services discovered yet."
		hint="Port and service findings appear here as hosts are scanned." />
	<DataTable v-else :columns="columns" :data="rows" search-placeholder="Filter services…"
		empty-message="No services match the filter." />
</template>

<script setup>
import { computed } from "vue";
import { Network } from "@lucide/vue";
import DataTable from "@/components/views/DataTable.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

/** Flattens hosts × their nested services into one self-contained row per service. */
const rows = computed(() => {
	const result = [];
	for (const host of props.data?.hosts ?? []) {
		for (const service of host.services ?? []) {
			result.push({
				ip: host.ip,
				hostname: host.hostnames?.[0] ?? "",
				port: service.port,
				protocol: service.protocol ?? "tcp",
				state: service.state ?? "open",
				service: service.service ?? "",
				product: service.product ?? "",
				version: service.version ?? "",
				banner: service.banner ?? "",
				certSans: service.cert?.san ?? [],
				discoveredBy: service.discovered_by ?? "",
				evidence: service.evidence ?? [],
			});
		}
	}
	return result;
});

const columns = [
	{ accessorKey: "ip", header: "Host" },
	{ accessorKey: "hostname", header: "Hostname", cell: ({ getValue }) => getValue() || "—" },
	{
		accessorKey: "port",
		header: "Port",
		cell: ({ row }) => `${row.original.port}/${row.original.protocol}`,
		sortingFn: (a, b) => Number(a.original.port) - Number(b.original.port),
	},
	{ accessorKey: "state", header: "State" },
	{ accessorKey: "service", header: "Service", cell: ({ getValue }) => getValue() || "—" },
	{ accessorKey: "product", header: "Product", cell: ({ getValue }) => getValue() || "—" },
	{ accessorKey: "version", header: "Version", cell: ({ getValue }) => getValue() || "—" },
	{ accessorKey: "banner", header: "Banner", enableSorting: false, cell: ({ getValue }) => getValue() || "—" },
	{ id: "cert", header: "TLS SANs", enableSorting: false, cell: ({ row }) => row.original.certSans.join(", ") || "—" },
	{ accessorKey: "discoveredBy", header: "Discovered By", enableSorting: false, cell: ({ getValue }) => getValue() || "—" },
	{ id: "evidence", header: "Evidence", enableSorting: false, cell: ({ row }) => row.original.evidence.join(", ") || "—" },
];
</script>
