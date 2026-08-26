<template>
	<EmptyHint
		v-if="treeData.children.length === 0"
		:icon="Globe"
		message="No DNS records collected yet."
		hint="Domain names and their records will grow into this tree as recon proceeds."
	/>
	<EChartCanvas v-else :option="chartOption" />
</template>

<script setup>
import { computed } from "vue";
import { Globe } from "@lucide/vue";
import EChartCanvas from "@/components/views/EChartCanvas.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

/**
 * Records arrive either as an array ({ type, value } entries or plain strings)
 * or as an object map keyed by type ({ A: ["1.2.3.4"], MX: [{ exchange }] }).
 */
function normalizeRecords(records) {
	if (!records) return [];
	const flatten = (type, values) =>
		(Array.isArray(values) ? values : [values]).map((value) => ({
			type,
			value: typeof value === "string" ? value : String(value.exchange ?? value.value ?? JSON.stringify(value)),
		}));
	if (Array.isArray(records)) {
		return records.flatMap((record) =>
			typeof record === "string" ? flatten("REC", [record]) : flatten(record.type ?? "REC", [record.value ?? record]),
		);
	}
	return Object.entries(records).flatMap(([type, values]) => flatten(type, values));
}

const treeData = computed(() => ({
	name: "DNS",
	children: (props.data?.domains ?? [])
		.filter((domain) => domain?.domain)
		.map((domain) => ({
			name: domain.domain,
			itemStyle: { color: "#1e293b", borderColor: "#60a5fa" },
			children: normalizeRecords(domain.records).map((record) => ({
				name: `${record.type}  ${record.value}`,
			})),
		})),
}));

const chartOption = computed(() => ({
	tooltip: { trigger: "item", triggerOn: "mousemove" },
	series: [
		{
			type: "tree",
			data: [treeData.value],
			orient: "LR",
			left: 20,
			right: 160,
			top: 20,
			bottom: 20,
			symbol: "circle",
			symbolSize: 8,
			initialTreeDepth: -1,
			roam: true,
			lineStyle: { color: "#3f3f46" },
			label: {
				position: "left",
				verticalAlign: "middle",
				align: "right",
				color: "#e4e4e7",
				fontSize: 11,
				distance: 8,
			},
			leaves: {
				label: { position: "right", verticalAlign: "middle", align: "left", color: "#a1a1aa", distance: 8 },
			},
			expandAndCollapse: true,
			animationDuration: 300,
		},
	],
}));
</script>