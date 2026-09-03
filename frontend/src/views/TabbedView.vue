<template>
	<div class="tabbed-view">
		<Tabs :model-value="tabsStore.active" class="h-auto shrink-0" @update:model-value="tabsStore.active = $event">
			<TabsList class="h-8 w-full items-center justify-start gap-0 rounded-none px-0">
				<TabsTrigger v-for="tab in tabsStore.tabs" :key="tab.value" :value="tab.value"
					:closable="tab.closable !== false" class="h-8 min-w-fit px-5"
					@close="tabsStore.closeTab(tab.value)"
					@mousedown.middle.prevent="tab.closable !== false && tabsStore.closeTab(tab.value)">
					{{ tab.label }}
				</TabsTrigger>
			</TabsList>
		</Tabs>

		<!-- Panes are managed manually: v-show guarantees exactly the active tab is
		     visible (reka's TabsContent force-mounts every pane here, which made
		     contents stack and zero-size canvases). Panes stay mounted so editors
		     and graphs keep their state across switches. -->
		<div class="min-h-0 flex-1 overflow-hidden">
			<EmptyState v-if="!tabsStore.tabs.length" text="Whoa! It is empty here. Open something from the File or Menu.">
				<AppWindow />
			</EmptyState>
			<div
				v-for="tab in tabsStore.tabs"
				:key="tab.value"
				v-show="tab.value === tabsStore.active"
				class="h-full w-full overflow-hidden"
			>
				<HomeView v-if="tab.component === 'HomeView'" />
				<OnboardingView v-else-if="tab.component === 'OnboardingView'" />
				<SettingsView v-else-if="tab.component === 'SettingsView'" />
				<FileEditorView v-else-if="tab.component === 'FileEditorView'" :path="tab.path" />
				<NetworkView v-else-if="tab.component === 'NetworkView'" />
				<WebAppView v-else-if="tab.component === 'WebAppView'" />
				<FindingsView v-else-if="tab.component === 'FindingsView'" />
				<EvidenceView v-else-if="tab.component === 'EvidenceView'" />
				<TasksView v-else-if="tab.component === 'TasksView'" />
			</div>
		</div>
	</div>
</template>

<script setup>
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AppWindow } from "@lucide/vue";
import EmptyState from "@/components/EmptyState.vue";
import HomeView from "@/views/HomeView.vue";
import OnboardingView from "@/views/OnboardingView.vue";
import SettingsView from "@/views/SettingsView.vue";
import { defineAsyncComponent } from "vue";

// The editor (CodeMirror core + language chunks) only loads when a file tab opens.
const FileEditorView = defineAsyncComponent(() => import("@/views/FileEditorView.vue"));
// Engagement document views pull their charting/graphing libraries, so keep them chunked too.
const NetworkView = defineAsyncComponent(() => import("@/views/NetworkView.vue"));
const WebAppView = defineAsyncComponent(() => import("@/views/WebAppView.vue"));
const FindingsView = defineAsyncComponent(() => import("@/views/FindingsView.vue"));
const EvidenceView = defineAsyncComponent(() => import("@/views/EvidenceView.vue"));
const TasksView = defineAsyncComponent(() => import("@/views/TasksView.vue"));
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
