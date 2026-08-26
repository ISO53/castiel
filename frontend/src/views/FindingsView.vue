<template>
	<DocumentShell doc-id="findings" v-slot="{ data, persist, refresh }">
		<Tabs default-value="board" class="flex h-full min-h-0 flex-col gap-0">
			<TabsList class="h-7 shrink-0 justify-start gap-0 rounded-none border-b bg-transparent p-0">
				<TabsTrigger value="board" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Board
				</TabsTrigger>
				<TabsTrigger value="severity" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Severity
				</TabsTrigger>
			</TabsList>
			<TabsContent value="board" class="min-h-0 flex-1 overflow-hidden pt-2">
				<FindingsBoardPanel :data="data" :persist="persist" :refresh="refresh" />
			</TabsContent>
			<TabsContent value="severity" class="min-h-0 flex-1 overflow-hidden pt-2">
				<SeverityChartPanel :data="data" />
			</TabsContent>
		</Tabs>
	</DocumentShell>
</template>

<script setup>
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import DocumentShell from "@/components/views/DocumentShell.vue";
import FindingsBoardPanel from "@/components/views/panels/FindingsBoardPanel.vue";
import SeverityChartPanel from "@/components/views/panels/SeverityChartPanel.vue";
</script>