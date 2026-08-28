<template>
	<div class="flex h-96 flex-col gap-3">

		<!-- Workspace name input and path preview -->
		<template v-if="mode === 'new'">
			<div class="flex shrink-0 flex-col gap-1.5">
				<Label for="workspace_name">Workspace name</Label>
				<Input id="workspace_name" v-model="workspaceName" type="text" placeholder="my-project"
					autocomplete="off" spellcheck="false" />
			</div>
			<p v-if="currentPath" class="shrink-0 text-xs text-muted-foreground wrap-break-word" :title="previewPath">
				Your new workspace will be created at
				<span class="font-medium text-foreground">{{ previewPath }}</span>
			</p>
			<p v-if="nameConflict" class="shrink-0 text-xs text-destructive">
				"{{ trimmedWorkspaceName }}" already exists in this folder.
			</p>
		</template>

		<!-- Navigation buttons + current path -->
		<div class="flex shrink-0 items-center gap-1">
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
		<p v-if="error" class="shrink-0 text-xs text-destructive wrap-break-word">{{ error }}</p>

		<!-- List -->
		<div class="min-h-0 flex-1 overflow-y-auto rounded-md border bg-background p-1">
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
		<div class="flex shrink-0 justify-between">
			<div class="flex items-center gap-3">
				<Checkbox id="hidden_folders" v-model="includeHiddenFolders" />
				<Label for="hidden_folders">Show Hidden Folders</Label>
			</div>
			<Button :disabled="!canSelect || submitting" @click="selectWorkspace">
				{{ submitting ? "Working..." : "Select" }}
			</Button>
		</div>
	</div>
</template>

<script>
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useWorkspaceStore } from "@/stores/workspace";
import { ArrowUp, File, Folder, Home, LoaderCircle } from "@lucide/vue";
import { Checkbox } from "./ui/checkbox";
import { Label } from "./ui/label";

const API_BASE_URL = `${window.location.origin}/api/files`;

export default {
	name: "FileViewer",
	components: {
		Button,
		Input,
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
		/** `"open"` selects an existing folder; `"new"` creates a child folder under currentPath. */
		mode: {
			type: String,
			required: false,
			default: "open",
			validator: (value) => value === "open" || value === "new",
		},
	},
	emits: ["workspace-ready"],
	data() {
		return {
			currentPath: "",
			parentPath: "",
			entries: [],
			selectedPath: "",
			error: "",
			loading: false,
			submitting: false,
			includeHiddenFolders: false,
			workspaceName: "",
		};
	},
	computed: {
		trimmedWorkspaceName() {
			return this.workspaceName.trim();
		},
		previewPath() {
			const parent = (this.currentPath || "").trim();
			const name = this.trimmedWorkspaceName;
			if (!parent) return "";
			if (!name) return parent;
			const sep = parent.includes("\\") ? "\\" : "/";
			const trimmed = parent.endsWith("\\") || parent.endsWith("/") ? parent.slice(0, -1) : parent;
			return `${trimmed}${sep}${name}`;
		},
		nameConflict() {
			if (this.mode !== "new" || !this.trimmedWorkspaceName) return false;
			const target = this.trimmedWorkspaceName.toLowerCase();
			return this.entries.some((entry) => entry.name.toLowerCase() === target);
		},
		selectedEntry() {
			return this.entries.find((entry) => entry.path === this.selectedPath) ?? null;
		},
		canSelect() {
			if (this.mode === "new") {
				return Boolean(this.currentPath && this.trimmedWorkspaceName && !this.nameConflict);
			}
			return Boolean(this.selectedEntry?.directory);
		},
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
		async selectWorkspace() {
			if (!this.canSelect || this.submitting) return;
			this.submitting = true;
			this.error = "";
			const workspace = useWorkspaceStore();
			try {
				if (this.mode === "new") {
					await workspace.create(this.currentPath, this.trimmedWorkspaceName);
				} else {
					await workspace.open(this.selectedPath);
				}
				this.$emit("workspace-ready", workspace.cwd);
			} catch (err) {
				this.error = err instanceof Error ? err.message : String(err);
			} finally {
				this.submitting = false;
			}
		},
	},
	mounted() {
		this.fetchDirectory(null);
	},
};
</script>

<style scoped></style>
