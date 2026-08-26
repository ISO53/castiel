<template>
	<DocumentShell doc-id="web" v-slot="{ data }">
		<Tabs default-value="sitemap" class="flex h-full min-h-0 flex-col gap-0">
			<TabsList class="h-8 w-full items-center justify-start gap-0 rounded-none px-0">
				<TabsTrigger value="sitemap" class="h-8 min-w-fit px-5">
					Site Map
				</TabsTrigger>
				<TabsTrigger value="tech" class="h-8 min-w-fit px-5">
					Tech Stack
				</TabsTrigger>
				<TabsTrigger value="headers" class="h-8 min-w-fit px-5">
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