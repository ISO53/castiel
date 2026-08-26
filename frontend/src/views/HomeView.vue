<template>
	<div class="h-full w-full overflow-y-auto flex items-center justify-center p-6 select-none">
		<div class="max-w-4xl w-full flex flex-col md:flex-row items-center justify-center gap-8 md:gap-12 lg:gap-16">
			<!-- ASCII Art (Left) -->
			<div class="shrink-0 flex justify-center overflow-x-auto py-2">
				<pre
					class="font-mono text-[5px] sm:text-[6px] md:text-[7px] lg:text-[8px] leading-[1.1] text-primary/80 dark:text-primary tracking-tighter transition-all duration-300">
					{{ castielAscii }}</pre>
			</div>

			<!-- Intro & Actions (Right) -->
			<div class="flex flex-col items-center md:items-start text-center md:text-left space-y-6 max-w-md">
				<div class="space-y-2">
					<h1 class="text-3xl font-bold tracking-tight text-foreground">
						Castiel
					</h1>
					<p class="text-sm font-medium text-muted-foreground">
						AI powered penetration testing suite
					</p>
				</div>

				<!-- Action Buttons -->
				<div class="flex flex-wrap items-center gap-3 pt-2">
					<Button size="lg" class="gap-2 px-4 shadow-sm cursor-pointer" @click="openNewWorkspace">
						<FolderPlus class="size-4" />
						<span>New Workspace</span>
					</Button>

					<Button variant="outline" size="lg" class="gap-2 px-4 shadow-sm cursor-pointer"
						@click="openExistingWorkspace">
						<FolderOpen class="size-4" />
						<span>Open Workspace</span>
					</Button>
				</div>
			</div>
		</div>

		<WorkspaceDialog v-model:open="fileDialogOpen" :mode="explorerMode" @workspace-ready="onWorkspaceReady" />
	</div>
</template>

<script>
import { Button } from "@/components/ui/button";
import { CASTIEL_ASCII } from "@/lib/ascii-art";
import WorkspaceDialog from "@/components/WorkspaceDialog.vue";
import { useTabsStore } from "@/stores/tabs";
import { FolderPlus, FolderOpen } from "@lucide/vue";

export default {
	name: "HomeView",
	components: {
		Button,
		WorkspaceDialog,
		FolderPlus,
		FolderOpen,
	},
	data() {
		return {
			fileDialogOpen: false,
			explorerMode: "open",
			castielAscii: CASTIEL_ASCII,
		};
	},
	methods: {
		openNewWorkspace() {
			this.explorerMode = "new";
			this.fileDialogOpen = true;
		},
		openExistingWorkspace() {
			this.explorerMode = "open";
			this.fileDialogOpen = true;
		},
		onWorkspaceReady() {
			this.fileDialogOpen = false;
			useTabsStore().closeTab("home");
		},
	},
};
</script>
