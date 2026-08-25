<template>
	<div class="app">
		<MenuBar />

		<ResizablePanelGroup direction="horizontal" class="flex-1 min-h-0">
			<ResizablePanel collapsible :defaultSize="20" :minSize="3" :maxSize="45">
				<FileTreeView />
			</ResizablePanel>

			<ResizableHandle />

			<ResizablePanel>
				<ResizablePanelGroup direction="vertical">
					<ResizablePanel :minSize="40">
						<TabbedView />
					</ResizablePanel>

					<ResizableHandle />

					<ResizablePanel collapsible :defaultSize="40" :minSize="8"></ResizablePanel>
				</ResizablePanelGroup>
			</ResizablePanel>

			<ResizableHandle />

			<ResizablePanel collapsible :defaultSize="chats.historyOpen ? 25 : 30" :minSize="15" :maxSize="50">
				<ChatView />
			</ResizablePanel>

			<template v-if="chats.historyOpen">
				<ResizableHandle />
				<ResizablePanel collapsible :defaultSize="18" :minSize="10" :maxSize="35">
					<ChatHistoryPanel />
				</ResizablePanel>
			</template>
		</ResizablePanelGroup>
	</div>
</template>

<script setup>
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from "@/components/ui/resizable";
import ChatView from "@/views/ChatView.vue";
import ChatHistoryPanel from "@/components/ChatHistoryPanel.vue";
import FileTreeView from "@/components/FileTreeView.vue";
import MenuBar from "@/views/MenuBar.vue";
import TabbedView from "./views/TabbedView.vue";
import { useChatsStore } from "@/stores/chats";

const chats = useChatsStore();
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
