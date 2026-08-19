<template>
	<div class="app">
		<TopBar />
		<ResizablePanelGroup direction="horizontal">
			<ResizablePanel collapsible :defaultSize="20" :minSize="3" :maxSize="45"></ResizablePanel>

			<ResizableHandle />

			<ResizablePanel>
				<ResizablePanelGroup direction="vertical">
					<ResizablePanel :minSize="40"></ResizablePanel>

					<ResizableHandle />

					<ResizablePanel collapsible :defaultSize="40" :minSize="8"></ResizablePanel>
				</ResizablePanelGroup>
			</ResizablePanel>

			<ResizableHandle />

			<ResizablePanel collapsible :defaultSize="25" :minSize="3" :maxSize="45">
				<ChatView />
			</ResizablePanel>
		</ResizablePanelGroup>
	</div>
</template>

<script setup>
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from "@/components/ui/resizable";
import ChatView from "@/views/ChatView.vue";
import TopBar from "@/views/TopBar.vue";
</script>

<style scoped>
.app {
	width: 100vw;
	height: 100vh;
}
</style>
