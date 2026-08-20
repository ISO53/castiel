<template>
	<div class="flex flex-col gap-3 min-h-96">
		<!-- Navigation buttons + current path -->
		<div class="flex items-center gap-1">
			<Button variant="outline" size="icon" class="h-7 w-7 shrink-0" @click="goHome" aria-label="Go to desktop">
				<Home class="size-4" />
			</Button>
			<Button variant="outline" size="icon" class="h-7 w-7 shrink-0" :disabled="!parentPath" @click="goUp"
				aria-label="Go to parent directory">
				<ArrowUp class="size-4" />
			</Button>
			<div class="flex-1 rounded-md border bg-background px-2 py-1.5">
				<span class="block truncate text-xs text-muted-foreground" :title="currentPath">
					{{ currentPath || "Loading..." }}
				</span>
			</div>
		</div>

		<!-- Error -->
		<p v-if="error" class="text-xs text-destructive wrap-break-word">{{ error }}</p>

		<!-- List -->
		<div class="min-h-64 max-h-104 flex-1 overflow-y-auto rounded-md border bg-background p-1">
			<template v-if="loading">
				<div class="flex items-center gap-2 px-2 py-2 text-xs text-muted-foreground">
					<LoaderCircle class="size-4 animate-spin shrink-0" />
					<span>Loading directory...</span>
				</div>
			</template>
			<p v-else-if="entries.length === 0" class="px-2 py-2 text-xs text-muted-foreground">
				This directory is empty.
			</p>
			<div v-else class="flex flex-col gap-px">
				<div v-for="entry in entries" :key="entry.path" :class="[
					'flex items-center gap-2 rounded-md px-2 py-1.5 text-xs cursor-pointer select-none',
					selectedPath === entry.path
						? 'bg-accent text-accent-foreground'
						: 'hover:bg-muted',
				]" @click="selectedPath = entry.path" @dblclick="openEntry(entry)" :title="entry.path">
					<Folder v-if="entry.directory" class="size-4 shrink-0" />
					<File v-else class="size-4 shrink-0" />
					<span class="flex-1 truncate">{{ entry.name }}</span>
				</div>
			</div>
		</div>

		<!-- Footer -->
		<div class="flex justify-between">
			<div class="flex items-center gap-3">
				<Checkbox id="hidden_folders" v-model="includeHiddenFolders" />
				<Label for="hidden_folders">Enable Hidden Folders</Label>
			</div>
			<Button>Select</Button>
		</div>
	</div>
</template>

<script>
import { Button } from "@/components/ui/button";
import { ArrowUp, File, Folder, Home, LoaderCircle } from "@lucide/vue";
import { Checkbox } from "./ui/checkbox";
import { Label } from "./ui/label";

// this will probably change later
const API_BASE_URL = "http://localhost:8081/api/files";

export default {
	name: "FileViewer",
	components: {
		Button,
		ArrowUp,
		File,
		Folder,
		Home,
		LoaderCircle,
		Checkbox,
		Label,
	},
	props: {
		includeFiles: { type: Boolean, required: false, default: false },
	},
	data() {
		return {
			currentPath: "",
			parentPath: "",
			entries: [],
			selectedPath: "",
			error: "",
			loading: false,
			includeHiddenFolders: true,
		};
	},
	watch: {
		includeHiddenFolders() {
			this.fetchDirectory(this.currentPath || null);
		},
	},
	methods: {
		async fetchDirectory(path) {
			this.loading = true;
			this.error = "";
			this.selectedPath = "";
			try {
				const query = new URLSearchParams({
					includeFiles: String(this.includeFiles),
					includeHiddenFolders: String(this.includeHiddenFolders),
				});
				if (path) query.set("path", path);
				const response = await fetch(`${API_BASE_URL}?${query.toString()}`);
				if (!response.ok) {
					const body = await response.text();
					throw new Error(body || `Request failed with status ${response.status}`);
				}
				const data = await response.json();
				this.currentPath = data.path ?? "";
				this.parentPath = data.parentPath ?? "";
				this.entries = data.entries ?? [];
			} catch (err) {
				this.error = err instanceof Error ? err.message : String(err);
			} finally {
				this.loading = false;
			}
		},
		openEntry(entry) {
			if (entry.directory) {
				this.fetchDirectory(entry.path);
			}
		},
		goUp() {
			if (this.parentPath) {
				this.fetchDirectory(this.parentPath);
			}
		},
		goHome() {
			this.fetchDirectory(null);
		},
	},
	mounted() {
		// Start from the backend's default starting position (the user's desktop).
		this.fetchDirectory(null);
	},
};
</script>

<style scoped></style>
