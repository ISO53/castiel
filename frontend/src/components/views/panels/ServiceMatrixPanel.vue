<template>
	<EmptyHint
		v-if="rows.length === 0"
		:icon="Network"
		message="No open services discovered yet."
		hint="Port and service findings appear here as hosts are scanned."
	/>
	<DataTable
		v-else
		:columns="columns"
		:data="rows"
		search-placeholder="Filter services…"
		empty-message="No services match the filter."
	/>
</template>

<script setup>
import { computed } from "vue";
import { Network } from "@lucide/vue";
import DataTable from "@/components/views/DataTable.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

/** Flattens hosts × their nested services into one sortable matrix row each. */
const rows = computed(() => {
	const result = [];
	for (const host of props.data?.hosts ?? []) {
		for (const service of host.services ?? []) {
			result.push({
				ip: host.ip,
				os: host.os ?? "",
				port: service.port,
				protocol: service.protocol ?? "tcp",
				state: service.state ?? "open",
				service: service.service ?? "",
				version: service.version ?? "",
			});
		}
	}
	return result;
});

const columns = [
	{ accessorKey: "ip", header: "Host" },
	{
		accessorKey: "port",
		header: "Port",
		cell: ({ row }) => `${row.original.port}/${row.original.protocol}`,
		sortingFn: (a, b) => Number(a.original.port) - Number(b.original.port),
	},
	{ accessorKey: "state", header: "State" },
	{ accessorKey: "service", header: "Service" },
	{ accessorKey: "version", header: "Version", cell: ({ getValue }) => getValue() || "—" },
	{ accessorKey: "os", header: "OS", cell: ({ getValue }) => getValue() || "—" },
];
</script>