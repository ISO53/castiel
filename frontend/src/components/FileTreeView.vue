<template>
	<section class="flex h-full min-h-0 flex-col bg-background">
		<header class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-3">
			<div class="flex min-w-0 items-center gap-1.5">
				<FolderOpen v-if="cwd" class="size-3.5 shrink-0 text-muted-foreground" />
				<p class="truncate text-xs font-medium text-foreground" :title="cwd ?? ''">{{ headerTitle }}</p>
			</div>
			<Button variant="ghost" size="icon-sm" :disabled="!cwd || loadingRoot" aria-label="Refresh file tree"
				@click="reload">
				<RotateCw :class="loadingRoot ? 'animate-spin' : ''" />
			</Button>
		</header>

		<div class="min-h-0 flex-1 overflow-y-auto p-2">
			<p v-if="error" class="px-2 py-2 text-xs leading-relaxed text-destructive wrap-break-word">{{ error }}</p>
			<div v-else-if="loadingRoot" class="flex items-center gap-2 px-2 py-2 text-xs text-muted-foreground">
				<Spinner class="size-3 shrink-0" /> Loading workspace…
			</div>
			<p v-else-if="!root" class="max-w-44 px-2 py-2 text-xs leading-relaxed text-muted-foreground">
				Open a workspace from the Home tab to browse its files.
			</p>
			<FileTree v-else class="border-none bg-transparent p-0 font-sans text-xs"
				:selected-path="selectedPath" :expanded="expanded"
				@update:selected-path="selectedPath = $event" @expanded-change="onExpandedChange">
				<FileTreeEntry :entry="root" :nodes="nodes" />
			</FileTree>
		</div>
	</section>
</template>

<script>
import { FileTree } from "@/components/ai-elements/file-tree";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import FileTreeEntry from "@/components/FileTreeEntry.vue";
import { FolderOpen, RotateCw } from "@lucide/vue";
import { useWorkspaceStore } from "@/stores/workspace";

const FILES_API = "http://localhost:8081/api/files";

// Build/output directories that would only add noise to the workspace tree.
const IGNORED_DIRECTORIES = new Set([
	".git",
	".idea",
	".next",
	".venv",
	".vs",
	"__pycache__",
	"build",
	"dist",
	"node_modules",
	"out",
	"target",
	"venv",
]);

function isIgnored(name) {
	return IGNORED_DIRECTORIES.has(name.toLowerCase());
}

// Directories first, then files, each group alphabetical.
function compareEntries(a, b) {
	if (a.directory !== b.directory) return a.directory ? -1 : 1;
	return a.name.localeCompare(b.name);
}

export default {
	name: "FileTreeView",
	components: {
		Button,
		FileTree,
		FileTreeEntry,
		FolderOpen,
		RotateCw,
		Spinner,
	},

	data() {
		return {
			workspace: useWorkspaceStore(),
			root: null,
			nodes: {},
			expanded: new Set(),
			selectedPath: "",
			loadingRoot: false,
			error: "",
		};
	},
	computed: {
		cwd() {
			return this.workspace.cwd;
		},
		headerTitle() {
			if (!this.cwd) return "No workspace";
			const segments = this.cwd.split(/[\\/]/).filter(Boolean);
			return segments[segments.length - 1] ?? this.cwd;
		},
	},
	watch: {
		cwd() {
			this.reload();
		},
	},
	mounted() {
		this.initialize();
	},
	methods: {
		async initialize() {
			if (this.cwd) {
				this.reload();
				return;
			}
			try {
				await this.workspace.fetch();
			} catch {
				// No session to restore — stay on the empty state until a workspace is opened.
			}
		},
		async reload() {
			this.root = null;
			this.nodes = {};
			this.expanded = new Set();
			this.selectedPath = "";
			this.error = "";
			if (!this.cwd) return;

			this.loadingRoot = true;
			try {
				const listing = await this.listDirectory(this.cwd);
				this.root = { path: listing.path, name: this.headerTitle, directory: true };
				this.nodes[listing.path] = {
					loaded: true,
					children: this.prepareChildren(listing.entries),
				};
				this.expanded = new Set([listing.path]);
			} catch (err) {
				this.error = err instanceof Error ? err.message : String(err);
			} finally {
				this.loadingRoot = false;
			}
		},
		async listDirectory(path) {
			const query = new URLSearchParams({ includeFiles: "true", includeHiddenFolders: "false" });
			query.set("path", path);
			const response = await fetch(`${FILES_API}?${query.toString()}`);
			if (!response.ok) {
				throw new Error(await response.text() || `Request failed with status ${response.status}`);
			}
			return response.json();
		},
		prepareChildren(entries) {
			return (entries ?? [])
				.filter((entry) => entry.directory || !isIgnored(entry.name))
				.sort(compareEntries)
				.map((entry) => ({ path: entry.path, name: entry.name, directory: Boolean(entry.directory) }));
		},
		async loadChildren(path) {
			if (this.nodes[path]?.loaded || this.nodes[path]?.loading) return;
			this.nodes[path] = { loading: true, children: [] };
			try {
				const listing = await this.listDirectory(path);
				this.nodes[path] = {
					loaded: true,
					children: this.prepareChildren(listing.entries),
				};
			} catch (err) {
				delete this.nodes[path];
				this.error = err instanceof Error ? err.message : String(err);
			}
		},
		onExpandedChange(nextExpanded) {
			const previous = this.expanded;
			this.expanded = nextExpanded;
			for (const path of nextExpanded) {
				if (!previous.has(path)) this.loadChildren(path);
			}
		},
	},
};
</script>
