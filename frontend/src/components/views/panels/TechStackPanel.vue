<template>
	<EmptyHint
		v-if="categories.length === 0"
		:icon="Globe"
		message="No technologies fingerprinted yet."
		hint="Fingerprinting results will appear here grouped by category as web.json fills in."
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
 * Technologies may be plain strings ("nginx") or objects ({ name, category, version }).
 * Both normalize into a treemap grouped by category.
 */
const categories = computed(() => {
	const groups = new Map();
	for (const entry of props.data?.technologies ?? []) {
		if (typeof entry === "string") {
			push(groups, "General", entry, "");
		} else if (entry && (entry.name || entry.technology)) {
			push(groups, entry.category || "General", entry.name ?? entry.technology, entry.version ?? "");
		}
	}
	return [...groups.entries()].map(([category, items]) => ({
		name: category,
		itemStyle: { color: "#27272a", borderColor: "#18181b" },
		children: [...mapToChildren(items)],
	}));

	function push(map, category, name, version) {
		if (!map.has(category)) map.set(category, new Map());
		const items = map.get(category);
		items.set(name, version);
	}

	function* mapToChildren(items) {
		for (const [name, version] of items) {
			yield { name: version ? `${name} ${version}` : name, value: 1 };
		}
	}
});

const chartOption = computed(() => ({
	tooltip: {},
	series: [
		{
			type: "treemap",
			data: categories.value,
			roam: false,
			nodeClick: "zoomToNode",
			breadcrumb: { show: true, itemStyle: { color: "#27272a", textStyle: { color: "#a1a1aa" } } },
			label: { color: "#e4e4e7", fontSize: 11 },
			upperLabel: { show: true, height: 20, color: "#a1a1aa" },
			itemStyle: { gapWidth: 2, borderColor: "#18181b" },
			leafDepth: 2,
		},
	],
}));
</script>