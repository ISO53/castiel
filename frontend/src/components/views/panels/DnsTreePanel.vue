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

const treeData = computed(() => ({
	name: "DNS",
	children: (props.data?.domains ?? [])
		.filter((domain) => domain?.domain)
		.map((domain) => ({
			name: domain.domain,
			itemStyle: { color: "#1e293b", borderColor: "#60a5fa" },
			children: (domain.records ?? []).map((record) => ({
				name: `${record.type}  ${record.value}`,
				value: record.value,
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