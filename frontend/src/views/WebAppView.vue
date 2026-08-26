<template>
	<DocumentShell doc-id="web" v-slot="{ data }">
		<Tabs default-value="sitemap" class="flex h-full min-h-0 flex-col gap-0">
			<TabsList class="h-7 shrink-0 justify-start gap-0 rounded-none border-b bg-transparent p-0">
				<TabsTrigger value="sitemap" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Site Map
				</TabsTrigger>
				<TabsTrigger value="tech" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Tech Stack
				</TabsTrigger>
				<TabsTrigger value="headers" class="rounded-none border-b border-transparent px-3 py-1 text-xs data-[state=active]:border-foreground">
					Security Headers
				</TabsTrigger>
			</TabsList>
			<TabsContent value="sitemap" class="min-h-0 flex-1 overflow-hidden pt-2">
				<SiteMapPanel :data="data" />
			</TabsContent>
			<TabsContent value="tech" class="min-h-0 flex-1 overflow-hidden pt-2">
				<TechStackPanel :data="data" />
			</TabsContent>
			<TabsContent value="headers" class="min-h-0 flex-1 overflow-hidden pt-2">
				<HeaderMatrixPanel :data="data" />
			</TabsContent>
		</Tabs>
	</DocumentShell>
</template>

<script setup>
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import DocumentShell from "@/components/views/DocumentShell.vue";
import HeaderMatrixPanel from "@/components/views/panels/HeaderMatrixPanel.vue";
import SiteMapPanel from "@/components/views/panels/SiteMapPanel.vue";
import TechStackPanel from "@/components/views/panels/TechStackPanel.vue";
</script>