<template>
	<div class="tabbed-view">
		<Tabs :model-value="tabsStore.active" class="h-full" @update:model-value="tabsStore.active = $event">
			<TabsList class="h-8 w-full items-center justify-start gap-0 rounded-none px-0">
				<TabsTrigger v-for="tab in tabsStore.tabs" :key="tab.value" :value="tab.value"
					:closable="tab.closable !== false" class="h-8 min-w-fit px-5"
					@close="tabsStore.closeTab(tab.value)">
					{{ tab.label }}
				</TabsTrigger>
			</TabsList>

			<TabsContent v-for="tab in tabsStore.tabs" :key="tab.value" :value="tab.value"
				class="h-full overflow-hidden">
				<HomeView v-if="tab.component === 'HomeView'" />
				<SettingsView v-else-if="tab.component === 'SettingsView'" />
				<FileEditorView v-else-if="tab.component === 'FileEditorView'" :path="tab.path" />
			</TabsContent>
		</Tabs>
	</div>
</template>

<script setup>
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import HomeView from "@/views/HomeView.vue";
import SettingsView from "@/views/SettingsView.vue";
import { defineAsyncComponent } from "vue";

// The editor (CodeMirror core + language chunks) only loads when a file tab opens.
const FileEditorView = defineAsyncComponent(() => import("@/views/FileEditorView.vue"));
import { useTabsStore } from "@/stores/tabs";

const tabsStore = useTabsStore();
</script>

<style scoped>
.tabbed-view {
	width: 100%;
	height: 100%;
	display: flex;
	flex-direction: column;
	overflow: hidden;
}
</style>
