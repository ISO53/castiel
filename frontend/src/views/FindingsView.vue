<template>
	<DocumentShell doc-id="findings" v-slot="{ data, persist, refresh }">
		<Tabs default-value="board" class="flex h-full min-h-0 flex-col gap-0">
			<TabsList class="h-8 w-full items-center justify-start gap-0 rounded-none px-0">
				<TabsTrigger value="board" class="h-8 min-w-fit px-5">
					Board
				</TabsTrigger>
				<TabsTrigger value="severity" class="h-8 min-w-fit px-5">
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