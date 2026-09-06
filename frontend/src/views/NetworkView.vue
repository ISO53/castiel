<template>
	<DocumentShell doc-id="network" v-slot="{ data }">
		<Tabs default-value="topology" class="flex h-full min-h-0 flex-col gap-0">
			<TabsList class="h-8 w-full items-center justify-start gap-0 rounded-none px-0">
				<TabsTrigger value="topology" class="h-8 min-w-fit px-5">
					Topology
				</TabsTrigger>
				<TabsTrigger value="services" class="h-8 min-w-fit px-5">
					Ports &amp; Services
				</TabsTrigger>
			</TabsList>
			<TabsContent value="topology" class="min-h-0 flex-1 overflow-hidden pt-2">
				<NetworkTopologyPanel :data="data" />
			</TabsContent>
			<TabsContent value="services" class="min-h-0 flex-1 overflow-hidden">
				<ServiceMatrixPanel :data="data" />
			</TabsContent>
		</Tabs>
	</DocumentShell>
</template>

<script setup>
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import DocumentShell from "@/components/views/DocumentShell.vue";
import NetworkTopologyPanel from "@/components/views/panels/NetworkTopologyPanel.vue";
import ServiceMatrixPanel from "@/components/views/panels/ServiceMatrixPanel.vue";
</script>
