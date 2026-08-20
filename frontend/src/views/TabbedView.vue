<script setup>
import { ref } from "vue";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import HomeView from "@/views/HomeView.vue";

const tabs = ref([
	{ value: "home", label: "Home", component: "HomeView" },
	{ value: "deneme", label: "Deneme", component: "Deneme" },
]);
const active = ref("home");

function closeTab(value) {
	const index = tabs.value.findIndex((t) => t.value === value);
	if (index === -1) return;
	const wasActive = active.value === value;
	tabs.value.splice(index, 1);
	if (wasActive) {
		const next = tabs.value[Math.min(index, tabs.value.length - 1)];
		active.value = next ? next.value : "";
	}
}
</script>

<template>
	<div class="tabbed-view">
		<Tabs v-model="active" class="h-full">
			<TabsList class="h-8 w-full items-center justify-start gap-0 rounded-none px-0">
				<TabsTrigger v-for="tab in tabs" :key="tab.value" :value="tab.value" :closable="true"
					class="h-8 min-w-fit px-5" @close="closeTab(tab.value)">
					{{ tab.label }}
				</TabsTrigger>
			</TabsList>

			<TabsContent v-for="tab in tabs" :key="tab.value" :value="tab.value" class="h-full">
				<HomeView v-if="tab.component === 'HomeView'" />
				<div v-else>{{ tab.value }}</div>
			</TabsContent>
		</Tabs>
	</div>
</template>

<style scoped>
.tabbed-view {
	width: 100%;
	height: 100%;
	display: flex;
	flex-direction: column;
	overflow: hidden;
}
</style>
