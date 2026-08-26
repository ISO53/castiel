<template>
	<DocumentShell doc-id="identity" v-slot="{ data }">
		<Tabs default-value="graph" class="flex h-full min-h-0 flex-col gap-0">
			<TabsList class="h-7 shrink-0 justify-start gap-0 rounded-none border-b bg-transparent p-0">
				<TabsTrigger value="graph" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Graph
				</TabsTrigger>
				<TabsTrigger value="org" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Org Chart
				</TabsTrigger>
			</TabsList>
			<TabsContent value="graph" class="min-h-0 flex-1 overflow-hidden pt-2">
				<IdentityGraphPanel :data="data" />
			</TabsContent>
			<TabsContent value="org" class="min-h-0 flex-1 overflow-hidden pt-2">
				<OrgChartPanel :data="data" />
			</TabsContent>
		</Tabs>
	</DocumentShell>
</template>

<script setup>
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import DocumentShell from "@/components/views/DocumentShell.vue";
import IdentityGraphPanel from "@/components/views/panels/IdentityGraphPanel.vue";
import OrgChartPanel from "@/components/views/panels/OrgChartPanel.vue";
</script>