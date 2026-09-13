<template>
	<div class="app">
		<MenuBar />

		<ResizablePanelGroup direction="horizontal" class="flex-1 min-h-0">
			<ResizablePanel collapsible :collapsed-size="0" :default-size="20" :min-size="3" :max-size="45"
				ref="leftDock" @collapse="docks.left = false" @expand="docks.left = true">
				<div class="flex h-full min-h-0 flex-col">
					<div class="min-h-0 flex-1">
						<FileTreeView />
					</div>
					<McpPanel />
				</div>
			</ResizablePanel>

			<ResizableHandle />

			<ResizablePanel>
				<ResizablePanelGroup direction="vertical">
					<ResizablePanel :minSize="40">
						<TabbedView />
					</ResizablePanel>

					<ResizableHandle />

					<ResizablePanel collapsible :collapsed-size="0" :default-size="20" :min-size="8" ref="bottomDock"
						@collapse="docks.bottom = false" @expand="docks.bottom = true">
						<component :is="bottomView" />
					</ResizablePanel>
				</ResizablePanelGroup>
			</ResizablePanel>

			<ResizableHandle />

			<ResizablePanel collapsible :collapsed-size="0" :default-size="30" :min-size="15" :max-size="50"
				ref="rightDock" @collapse="docks.right = false" @expand="docks.right = true">
				<component :is="rightView" />
			</ResizablePanel>
		</ResizablePanelGroup>

		<!-- Bottom bar -->
		<DockBar />
	</div>
</template>

<script>
import { computed, markRaw } from "vue";
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from "@/components/ui/resizable";
import DockBar from "@/components/DockBar.vue";
import ChatView from "@/views/ChatView.vue";
import ChatHistoryPanel from "@/components/ChatHistoryPanel.vue";
import FileTreeView from "@/components/FileTreeView.vue";
import McpPanel from "@/components/McpPanel.vue";
import MenuBar from "@/views/MenuBar.vue";
import ProcessesView from "@/views/ProcessesView.vue";
import TabbedView from "./views/TabbedView.vue";
import { useChatsStore } from "@/stores/chats";
import { useDocksStore } from "@/stores/docks";
import { useTabsStore } from "@/stores/tabs";

const ONBOARDING_SEEN_KEY = "castiel.onboardingComplete";

// Bottom-dock views; a new view registers here and in the DockBar.
const BOTTOM_DOCK_VIEWS = {
	processes: markRaw(ProcessesView),
};

// Right-dock views; a new view registers here and in the DockBar.
const RIGHT_DOCK_VIEWS = {
	chat: markRaw(ChatView),
	history: markRaw(ChatHistoryPanel),
};

export default {
	name: "App",
	components: {
		ResizablePanelGroup,
		ResizablePanel,
		ResizableHandle,
		ChatView,
		ChatHistoryPanel,
		DockBar,
		FileTreeView,
		McpPanel,
		MenuBar,
		ProcessesView,
		TabbedView,
	},
	setup() {
		const docks = useDocksStore();
		// Docks render one registered view at a time.
		const rightView = computed(() => RIGHT_DOCK_VIEWS[docks.rightView] ?? RIGHT_DOCK_VIEWS.chat);
		const bottomView = computed(() => BOTTOM_DOCK_VIEWS[docks.bottomView] ?? BOTTOM_DOCK_VIEWS.processes);
		return { docks, rightView, bottomView };
	},
	mounted() {
		// The onboarding tour opens once; it can be reopened from the Help menu.
		if (!localStorage.getItem(ONBOARDING_SEEN_KEY)) {
			useTabsStore().openTab({
				value: "onboarding",
				label: "Onboarding",
				component: "OnboardingView",
			});
		}
	},
	watch: {
		"docks.left"(isOpen) {
			this.$nextTick(() => this.syncPanelState("leftDock", isOpen));
		},
		"docks.right"(isOpen) {
			this.$nextTick(() => this.syncPanelState("rightDock", isOpen));
		},
		"docks.bottom"(isOpen) {
			this.$nextTick(() => this.syncPanelState("bottomDock", isOpen));
		},
	},
	methods: {
		// ResizablePanel re-exposes the underlying SplitterPanel's API, so a ref
		// on it gives us collapse/expand/isCollapsed.
		syncPanelState(dockName, isOpen) {
			const wrapper = this.$refs[dockName];
			if (!wrapper) return;

			if (isOpen) {
				if (typeof wrapper.expand === "function" && wrapper.isCollapsed) wrapper.expand();
			} else if (typeof wrapper.collapse === "function") {
				wrapper.collapse();
			}
		},
	},
};
</script>

<style scoped>
.app {
	width: 100%;
	height: 100vh;
	display: flex;
	flex-direction: column;
	overflow: hidden;
}
</style>
