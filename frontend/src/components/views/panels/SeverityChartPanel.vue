<template>
	<EmptyHint
		v-if="findings.length === 0"
		:icon="PieChart"
		message="Nothing to chart yet."
		hint="Severity and status distributions appear once findings exist."
	/>
	<div v-else class="grid h-full min-h-0 grid-cols-1 gap-2 lg:grid-cols-2">
		<section class="flex min-h-0 flex-col rounded-md border p-2">
			<p class="mb-1 shrink-0 text-xs font-medium text-muted-foreground">Severity distribution</p>
			<EChartCanvas :option="severityOption" />
		</section>
		<section class="flex min-h-0 flex-col rounded-md border p-2">
			<p class="mb-1 shrink-0 text-xs font-medium text-muted-foreground">Status pipeline</p>
			<EChartCanvas :option="statusOption" />
		</section>
	</div>
</template>

<script setup>
import { computed } from "vue";
import { PieChart } from "@lucide/vue";
import EChartCanvas from "@/components/views/EChartCanvas.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

const findings = computed(() => props.data?.findings ?? []);

const SEVERITIES = [
	{ key: "critical", color: "#ef4444" },
	{ key: "high", color: "#f97316" },
	{ key: "medium", color: "#eab308" },
	{ key: "low", color: "#38bdf8" },
	{ key: "info", color: "#71717a" },
];

const severityOption = computed(() => {
	const counts = countBy(findings.value, (finding) => String(finding.severity ?? "unknown").toLowerCase());
	return {
		tooltip: { trigger: "item" },
		legend: { bottom: 0, textStyle: { color: "#a1a1aa", fontSize: 10 } },
		series: [
			{
				type: "pie",
				radius: ["45%", "72%"],
				center: ["50%", "44%"],
				data: SEVERITIES.filter(({ key }) => counts.has(key)).map(({ key, color }) => ({
					name: key,
					value: counts.get(key),
					itemStyle: { color },
				})),
				label: { color: "#e4e4e7", fontSize: 10, formatter: "{b}: {c}" },
			},
		],
	};
});

const STATUS_ORDER = ["new", "triaged", "confirmed", "exploited", "mitigated"];

const statusOption = computed(() => {
	const counts = countBy(findings.value, (finding) => String(finding.status ?? "unknown").toLowerCase());
	const statuses = [...STATUS_ORDER.filter((status) => counts.has(status)), ...[...counts.keys()].filter((status) => !STATUS_ORDER.includes(status))];
	return {
		tooltip: { trigger: "axis" },
		grid: { left: 8, right: 8, top: 16, bottom: 24, containLabel: true },
		xAxis: {
			type: "category",
			data: statuses,
			axisLabel: { color: "#a1a1aa", fontSize: 10 },
			axisLine: { lineStyle: { color: "#3f3f46" } },
		},
		yAxis: {
			type: "value",
			minInterval: 1,
			axisLabel: { color: "#a1a1aa", fontSize: 10 },
			splitLine: { lineStyle: { color: "#27272a" } },
		},
		series: [{ type: "bar", barMaxWidth: 28, data: statuses.map((status) => counts.get(status)), itemStyle: { color: "#38bdf8" } }],
	};
});

function countBy(items, classifier) {
	const counts = new Map();
	for (const item of items) {
		const key = classifier(item);
		counts.set(key, (counts.get(key) ?? 0) + 1);
	}
	return counts;
}
</script>