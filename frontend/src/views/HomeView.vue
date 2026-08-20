<template>
	<div class="h-full w-full overflow-y-auto flex items-center justify-center p-6 select-none">
		<div class="max-w-4xl w-full flex flex-col md:flex-row items-center justify-center gap-8 md:gap-12 lg:gap-16">
			<!-- ASCII Art (Left) -->
			<div class="shrink-0 flex justify-center overflow-x-auto py-2">
				<pre
					class="font-mono text-[5px] sm:text-[6px] md:text-[7px] lg:text-[8px] leading-[1.1] text-primary/80 dark:text-primary tracking-tighter transition-all duration-300">
					{{ asciiArt }}</pre>
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
					<Button size="lg" class="gap-2 px-4 shadow-sm cursor-pointer" @click="openFileExplorer">
						<FolderPlus class="size-4" />
						<span>New Workspace</span>
					</Button>

					<Button variant="outline" size="lg" class="gap-2 px-4 shadow-sm cursor-pointer"
						@click="openFileExplorer">
						<FolderOpen class="size-4" />
						<span>Open Workspace</span>
					</Button>
				</div>
			</div>
		</div>

		<Dialog :open="fileDialogOpen" @update:open="fileDialogOpen = $event">
			<DialogContent class="sm:max-w-136">
				<DialogHeader>
					<DialogTitle>File Explorer</DialogTitle>
					<DialogDescription>
						Select a directory to use as your workspace.
					</DialogDescription>
				</DialogHeader>

				<FileViewer :include-files="includeFiles" />
			</DialogContent>
		</Dialog>
	</div>
</template>

<script>
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import FileViewer from "@/components/FileViewer.vue";
import { FolderPlus, FolderOpen } from "@lucide/vue";

export default {
	name: "HomeView",
	components: {
		Button,
		Dialog,
		DialogContent,
		DialogDescription,
		DialogHeader,
		DialogTitle,
		FileViewer,
		FolderPlus,
		FolderOpen,
	},
	data() {
		return {
			fileDialogOpen: false,
			includeFiles: false,
			asciiArt: `
⠀⠀⠀⠀⠀⠀⠀⢠⡄⠀⠀⢠⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⢹⣿⣄⠀⠈⣧⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⢷⢽⢦⡀⢹⣇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠘⣯⠳⡙⢦⣿⣦⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⢿⡦⣄⣀⠀⠀⠀⠀⠀⠈⢷⡙⢦⡙⢿⣷⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠈⠻⣮⡿⣿⢶⣤⣀⠀⠀⠀⠻⣄⠙⢦⡙⠿⣿⣦⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⢨⠻⣮⣝⠲⢌⣙⠒⠦⣤⣈⡳⣤⡉⠲⢬⡑⠿⣟⣷⢶⣤⣤⣄⣀⣤⣀⣀⣄⣀⣀⣀⣀⣀⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠻⣿⡷⣿⣿⣶⣬⣙⠲⢤⣀⡉⠙⠛⠳⢤⣈⡑⠲⠬⣍⣉⡒⠛⠛⠛⣿⠛⠛⠻⠭⣍⣉⣉⣛⣛⠻⠷⣶⣤⣄⣰⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠈⠻⣿⣗⠦⣍⡙⠛⠶⢦⣍⡙⠒⠦⢤⣄⣉⠓⠲⢦⢤⣭⡤⠶⠒⠚⢷⣦⣀⠀⠸⣧⡄⠀⠉⠙⠒⠀⠈⠙⠳⡿⣦⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⢰⣶⣶⣽⣷⣦⣍⣑⠒⠤⢌⣉⣙⠒⠶⠤⢬⣹⣿⣄⢹⣇⡒⠒⠦⠤⡸⣧⡉⠛⠒⠈⠉⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠙⣷⣤⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠉⠻⢾⣕⡺⠭⣙⣛⠲⠶⢦⣭⣍⣑⣒⠒⠒⠒⠂⠉⠉⢛⡖⠆⣀⣀⣈⣙⣶⠒⠒⠚⠀⠀⠀⠀⠀⢀⣠⣤⣀⣀⠀⠘⢿⣆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠈⠙⢓⣶⣬⣍⣑⣒⣶⠶⠭⠭⠭⠉⠉⠉⠍⠭⠉⠙⠶⣥⣄⣀⣀⣩⣿⡓⠒⠀⠀⠀⠀⠀⢻⣁⣀⠀⠈⠙⢦⠈⣿⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⢿⣭⣉⠉⢭⣉⡛⠛⠓⠒⠲⠶⠶⠶⠶⠖⠒⠒⣚⡩⠍⠉⢻⣍⣤⠀⠀⠀⠀⠀⠀⠀⢾⡉⠉⠛⠓⠀⠀⠈⣇⢸⡇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⢩⣻⣳⣶⣮⣭⣭⣍⣉⣉⣉⣉⣉⣭⣭⠤⠴⠒⢋⡿⢋⠤⠔⠒⠊⠉⢀⡴⠚⠛⢛⠓⠶⠆⠀⠀⠀⢻⠈⣷⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠘⠿⣍⣧⡤⠭⣍⣉⣉⣉⣀⣠⡤⠤⠴⠒⠊⣉⣨⠿⠒⠒⢶⣛⣀⣠⣞⣛⣶⠖⠲⠶⠀⠀⢀⡄⠀⠸⡦⣿⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠛⠛⡶⣶⣦⣤⣤⣿⡦⠤⠴⠖⠛⠋⣉⣀⠤⠔⣀⡤⢿⣛⡩⢤⣈⣻⡤⠶⠆⠀⠀⡞⠁⠀⠀⠉⢽⣇⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠻⢯⣭⣓⣀⣤⣒⣒⣒⣾⣉⣉⣁⣤⡴⢚⡥⠖⢋⣥⠶⠚⡉⠉⢧⣤⣤⠀⠸⢿⡶⠃⠀⠀⠀⢻⣆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠉⠉⣽⣯⣭⡥⠴⢻⡟⠘⣉⣠⡾⢋⡤⠖⣭⡴⢖⠻⣯⡴⠗⠀⠀⣧⣠⡆⠀⠀⠀⠹⣷⣄⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠘⠛⢛⣿⣫⠖⠉⣰⠟⣡⠖⢉⣾⣏⣀⣠⢤⡄⠈⣿⣠⡗⢀⠀⠀⠀⠙⠻⣦⣄⡀⠀⠀⠀⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣾⣁⣱⣶⡞⣡⠞⣁⡴⢋⡴⢋⡿⢶⡟⠀⠀⠈⢻⣀⡿⠀⢀⡇⠀⠀⠀⠈⠙⠓⠶⢤⣄
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠉⢠⡟⡝⣡⣾⢋⡴⠋⣴⠏⣠⠋⣿⠦⣶⠀⢈⠿⣧⢀⣾⣷⡀⢠⡀⠀⠀⠀⠀⠁⢸
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⡷⠚⣹⣣⢞⣷⣿⢇⡼⣡⣿⣵⢃⣾⡟⣹⢠⢸⢩⠘⡏⣳⡞⢧⣀⣿⣦⣷⢀⡞
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⠷⠛⠁⣿⣿⡾⢛⣿⣡⢾⣹⢡⣿⡏⣾⣼⢠⣿⡇⣷⣀⣽⠀⢨⢉⡄⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠁⠀⠀⠋⠁⢸⣷⠟⣿⣣⠟⣿⡾⣿⣷⡏⠛⠛⠶⠾⠾⠇⠀
⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠙⠋⠀⠀⠀⠈⠉⠀⠀⠀⠀⠀⠀⠀⠀`,
		};
	},
	methods: {
		openFileExplorer() {
			this.fileDialogOpen = true;
		},
	},
}
</script>
