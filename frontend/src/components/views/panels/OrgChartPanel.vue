<template>
	<EmptyHint
		v-if="roots.length === 0"
		:icon="Users"
		message="No organizations or people charted yet."
		hint="The org structure builds itself as identity.json fills in."
	/>
	<EChartCanvas v-else :option="chartOption" />
</template>

<script setup>
import { computed } from "vue";
import { Users } from "@lucide/vue";
import EChartCanvas from "@/components/views/EChartCanvas.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

function personName(person) {
	return person?.name ?? person?.full_name ?? person?.username ?? "?";
}

/** Forest of org → people hierarchies; people without an org fall under "Unassigned". */
const roots = computed(() => {
	const people = props.data?.people ?? [];
	const byOrg = new Map();
	for (const person of people) {
		const orgName = typeof person?.org === "string" ? person.org : person?.org?.name;
		const key = orgName || "Unassigned";
		if (!byOrg.has(key)) byOrg.set(key, []);
		byOrg.get(key).push({
			name: personName(person),
			value: [person.role ?? person.title ?? "", person.email ?? ""].filter(Boolean).join("\n"),
		});
	}

	const orgNodes = (props.data?.organizations ?? [])
		.map((org) => org?.name ?? org?.organization)
		.filter(Boolean)
		.map((name) => ({
			name,
			itemStyle: { color: "#1c1917", borderColor: "#d97706" },
			children: byOrg.get(name) ?? [],
		}));

	// Orgs with no explicit entry still get their people shown.
	const covered = new Set(orgNodes.map((node) => node.name));
	for (const [orgName, children] of byOrg) {
		if (!covered.has(orgName) && orgName !== "Unassigned") {
			orgNodes.push({ name: orgName, itemStyle: { color: "#1c1917", borderColor: "#d97706" }, children });
			covered.add(orgName);
		}
	}

	const unassigned = byOrg.get("Unassigned");
	if (unassigned?.length) orgNodes.push({ name: "Unassigned", itemStyle: { color: "#27272a", borderColor: "#52525b" }, children: unassigned });

	return orgNodes;
});

const chartOption = computed(() => ({
	tooltip: { trigger: "item", triggerOn: "mousemove" },
	series: [
		{
			type: "tree",
			data: roots.value.length > 1 ? [{ name: "Identity", children: roots.value }] : roots.value,
			orient: "LR",
			left: 20,
			right: 200,
			top: 20,
			bottom: 20,
			symbol: "circle",
			symbolSize: 8,
			initialTreeDepth: -1,
			lineStyle: { color: "#3f3f46" },
			label: {
				position: "left",
				align: "right",
				color: "#e4e4e7",
				fontSize: 11,
				distance: 8,
			},
			leaves: {
				label: { position: "right", align: "left", color: "#a1a1aa", fontSize: 10, distance: 8 },
			},
			expandAndCollapse: true,
			animationDuration: 300,
		},
	],
}));
</script>