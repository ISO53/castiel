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

			<ResizablePanel collapsible :defaultSize="30" :minSize="3" :maxSize="45">
				<ChatView />
			</ResizablePanel>
		</ResizablePanelGroup>
	</div>
</template>

<script setup>
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from "@/components/ui/resizable";
import ChatView from "@/views/ChatView.vue";
import FileTreeView from "@/components/FileTreeView.vue";
import MenuBar from "@/views/MenuBar.vue";
import TabbedView from "./views/TabbedView.vue";
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
