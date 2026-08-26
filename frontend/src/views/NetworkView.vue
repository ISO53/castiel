<template>
	<DocumentShell doc-id="network" v-slot="{ data }">
		<Tabs default-value="topology" class="flex h-full min-h-0 flex-col gap-0">
			<TabsList class="h-7 shrink-0 justify-start gap-0 rounded-none border-b bg-transparent p-0">
				<TabsTrigger value="topology" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Topology
				</TabsTrigger>
				<TabsTrigger value="services" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Ports &amp; Services
				</TabsTrigger>
				<TabsTrigger value="dns" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					DNS
				</TabsTrigger>
			</TabsList>
			<TabsContent value="topology" class="min-h-0 flex-1 overflow-hidden pt-2">
				<NetworkTopologyPanel :data="data" />
			</TabsContent>
			<TabsContent value="services" class="min-h-0 flex-1 overflow-hidden pt-2">
				<ServiceMatrixPanel :data="data" />
			</TabsContent>
			<TabsContent value="dns" class="min-h-0 flex-1 overflow-hidden pt-2">
				<DnsTreePanel :data="data" />
			</TabsContent>
		</Tabs>
	</DocumentShell>
</template>

<script setup>
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import DocumentShell from "@/components/views/DocumentShell.vue";
import DnsTreePanel from "@/components/views/panels/DnsTreePanel.vue";
import NetworkTopologyPanel from "@/components/views/panels/NetworkTopologyPanel.vue";
import ServiceMatrixPanel from "@/components/views/panels/ServiceMatrixPanel.vue";
</script>