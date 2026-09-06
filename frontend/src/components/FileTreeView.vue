<template>
	<section class="flex h-full min-h-0 flex-col bg-background">
		<header class="flex h-8 shrink-0 items-center justify-between gap-2 border-b px-3">
			<div class="flex min-w-0 items-center gap-1.5">
				<FolderOpen v-if="cwd" class="size-3.5 shrink-0 text-muted-foreground" />
				<p class="truncate text-xs font-medium text-foreground" :title="cwd ?? ''">{{ headerTitle }}</p>
			</div>
			<div class="flex shrink-0 items-center gap-0.5">
				<Button
					variant="ghost"
					size="icon-sm"
					:class="mode === 'files' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground'"
					aria-label="Show file tree"
					@click="mode = 'files'"
				>
					<FolderTree />
				</Button>
				<Button
					variant="ghost"
					size="icon-sm"
					:class="mode === 'views' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground'"
					aria-label="Show engagement views"
					:disabled="!cwd"
					@click="mode = 'views'"
				>
					<LayoutGrid />
				</Button>
				<Button variant="ghost" size="icon-sm" :disabled="!cwd || loadingRoot" aria-label="Refresh file tree"
					@click="reload">
					<RotateCw :class="loadingRoot ? 'animate-spin' : ''" />
				</Button>
				<Button variant="ghost" size="icon-sm" class="text-muted-foreground hover:text-foreground"
					aria-label="Hide the file tree dock" title="Hide dock" @click="docks.toggle('left')">
					<PanelLeftClose />
				</Button>
			</div>
		</header>

		<div class="min-h-0 flex-1 overflow-y-auto p-2">
			<template v-if="mode === 'files'">
				<p v-if="error" class="px-2 py-2 text-xs leading-relaxed text-destructive wrap-break-word">{{ error }}</p>
				<div v-else-if="loadingRoot" class="flex items-center gap-2 px-2 py-2 text-xs text-muted-foreground">
					<Spinner class="size-3 shrink-0" /> Loading workspace…
				</div>
				<EmptyState v-else-if="!root" text="Open a workspace from the Home tab to browse its files.">
					<FolderOpen />
				</EmptyState>
				<FileTree v-else class="border-none bg-transparent p-0 font-sans text-xs"
					:selected-path="selectedPath" :expanded="expanded"
					@update:selected-path="onSelect" @expanded-change="onExpandedChange">
					<FileTreeEntry :entry="root" :nodes="nodes" :renaming-path="fileTreeUi.renamingPath"
						:creating-in="fileTreeUi.creatingIn" />
				</FileTree>
			</template>

			<template v-else>
				<div class="space-y-0.5">
					<button
						v-for="entry in documents"
						:key="entry.id"
						type="button"
						class="flex w-full items-start gap-2 rounded-md px-2 py-1.5 text-left transition-colors hover:bg-muted/60"
						:title="`${entry.description} - opens the ${entry.label} views`"
						@click="openDocumentView(entry)"
					>
						<span class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded border bg-muted/40 text-muted-foreground">
							<component :is="docIcons[entry.id]" class="size-3.5" />
						</span>
						<span class="min-w-0">
							<span class="block truncate text-xs font-medium text-foreground">{{ entry.label }}</span>
							<span class="block truncate font-mono text-[10px] text-muted-foreground">{{ entry.file }}</span>
						</span>
					</button>
				</div>
			</template>
		</div>
	</section>
</template>

<script>
import { FileTree } from "@/components/ai-elements/file-tree";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import EmptyState from "@/components/EmptyState.vue";
import FileTreeEntry from "@/components/FileTreeEntry.vue";
import { fileTreeActions, fileTreeUi } from "@/components/file-tree-ui";
import {
	Bug,
	FolderOpen,
	FolderTree,
	Globe,
	Image as ImageIcon,
	LayoutGrid,
	ListChecks,
	Network,
	PanelLeftClose,
	RotateCw,
} from "@lucide/vue";
import { ENGAGEMENT_DOCUMENTS } from "@/lib/documents";
import { useDocksStore } from "@/stores/docks";
import { useTabsStore } from "@/stores/tabs";
import { useWorkspaceStore } from "@/stores/workspace";

const FILES_API = `${window.location.origin}/api/files`;

const DOC_ICONS = {
	network: Network,
	web: Globe,
	findings: Bug,
	evidence: ImageIcon,
	tasks: ListChecks,
};

/** Extracts the harness error message from a failed response. */
async function readErrorBody(response) {
	const body = await response.text();
	try {
		const json = JSON.parse(body);
		return json.message || json.detail || json.error || body || `Request failed with status ${response.status}`;
	} catch {
		return body || `Request failed with status ${response.status}`;
	}
}

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
		EmptyState,
		FileTree,
		FileTreeEntry,
		FolderOpen,
		FolderTree,
		LayoutGrid,
		PanelLeftClose,
		RotateCw,
		Spinner,
	},

	data() {
		return {
			workspace: useWorkspaceStore(),
			docks: useDocksStore(),
			root: null,
			nodes: {},
			entryIndex: {},
			expanded: new Set(),
			selectedPath: "",
			loadingRoot: false,
			error: "",
			fileTreeUi,
			mode: "files",
			documents: ENGAGEMENT_DOCUMENTS,
			docIcons: DOC_ICONS,
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
		Object.assign(fileTreeActions, {
			startCreate: this.startCreate,
			startRename: this.startRename,
			remove: this.removeItem,
			commitCreate: this.commitCreate,
			cancelCreate: this.cancelCreate,
			commitRename: this.commitRename,
			cancelRename: this.cancelRename,
		});
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
				// No session to restore. Stay on the empty state until a workspace is opened.
			}
		},
		async reload() {
			this.root = null;
			this.nodes = {};
			this.entryIndex = {};
			this.expanded = new Set();
			this.selectedPath = "";
			this.error = "";
			if (!this.cwd) return;

			this.loadingRoot = true;
			try {
				const listing = await this.listDirectory(this.cwd);
				this.root = { path: listing.path, name: this.headerTitle, directory: true };
				this.indexEntries(listing.entries);
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
				this.indexEntries(listing.entries);
				this.nodes[path] = {
					loaded: true,
					children: this.prepareChildren(listing.entries),
				};
			} catch (err) {
				delete this.nodes[path];
				this.error = err instanceof Error ? err.message : String(err);
			}
		},
		indexEntries(entries) {
			for (const entry of entries ?? []) {
				this.entryIndex[entry.path] = { name: entry.name, directory: Boolean(entry.directory) };
			}
		},
		onSelect(path) {
			this.selectedPath = path;
			const entry = this.entryIndex[path];
			if (!entry || entry.directory) return;
			useTabsStore().openTab({
				value: `file:${path}`,
				label: entry.name,
				component: "FileEditorView",
				path,
			});
		},

		openDocumentView(entry) {
			useTabsStore().openTab({
				value: `doc:${entry.file}`,
				label: entry.label,
				component: entry.component,
			});
		},

		// ---- Create / rename / delete --------------------------------------

		startCreate(parentPath, directory) {
			this.renamingPath = "";
			fileTreeUi.creatingDirectory = directory;
			fileTreeUi.creatingIn = parentPath;
			if (!this.expanded.has(parentPath)) {
				this.expanded.add(parentPath);
				this.loadChildren(parentPath);
			}
		},
		cancelCreate() {
			fileTreeUi.creatingIn = "";
		},
		async commitCreate(name) {
			name = String(name ?? "").trim();
			const target = fileTreeUi.creatingIn;
			if (!name || !target) return;
			try {
				await this.mutate(`${FILES_API}/items`, "POST", {
					parentPath: target,
					name,
					directory: fileTreeUi.creatingDirectory,
				});
				fileTreeUi.creatingIn = "";
			} catch (err) {
				this.error = err.message;
			}
		},
		startRename(path) {
			fileTreeUi.creatingIn = "";
			fileTreeUi.renamingPath = path;
		},
		cancelRename() {
			fileTreeUi.renamingPath = "";
		},
		async commitRename(name) {
			name = String(name ?? "").trim();
			const oldPath = fileTreeUi.renamingPath;
			if (!oldPath || !name || name === this.entryIndex[oldPath]?.name) {
				fileTreeUi.renamingPath = "";
				return;
			}
			try {
				const listing = await this.mutate(`${FILES_API}/items`, "PATCH", { path: oldPath, name });
				this.remapAfterRename(oldPath, listing.path);
				this.applyListing(listing);
				fileTreeUi.renamingPath = "";
			} catch (err) {
				this.error = err.message;
			}
		},
		async removeItem(entry) {
			try {
				const listing = await this.apiDelete(`${FILES_API}/items?path=${encodeURIComponent(entry.path)}`);
				this.purge(entry.path);
				this.applyListing(listing);
				if (this.selectedPath === entry.path) this.selectedPath = "";
			} catch (err) {
				this.error = err.message;
			}
		},

		/**
		 * Applies a fresh parent listing returned by a mutation: re-indexes and
		 * replaces that folder's children if it is currently loaded.
		 */
		applyListing(listing) {
			if (!listing?.path) return;
			this.indexEntries(listing.entries);
			if (this.nodes[listing.path]?.loaded !== false && this.nodes[listing.path]) {
				this.nodes[listing.path] = {
					loaded: true,
					children: this.prepareChildren(listing.entries),
				};
			}
		},

		/** Rewrites every tracked path under a renamed folder to its new prefix. */
		remapAfterRename(oldPath, newParentPath) {
			const separator = oldPath.includes("\\") ? "\\" : "/";
			const oldPrefix = oldPath.endsWith(separator) ? oldPath : oldPath + separator;
			for (const key of Object.keys(this.entryIndex)) {
				if (key.startsWith(oldPrefix)) {
					this.entryIndex[newParentPath + key.slice(oldPath.length)] = this.entryIndex[key];
					delete this.entryIndex[key];
				}
			}
			for (const key of Object.keys(this.nodes)) {
				if (key.startsWith(oldPrefix)) {
					this.nodes[newParentPath + key.slice(oldPath.length)] = this.nodes[key];
					delete this.nodes[key];
				}
			}
			this.expanded = new Set(
				[...this.expanded].map((path) => (path.startsWith(oldPrefix)
					? newParentPath + path.slice(oldPath.length)
					: path)),
			);
			if (this.selectedPath.startsWith(oldPrefix)) {
				this.selectedPath = newParentPath + this.selectedPath.slice(oldPath.length);
			}
		},

		/** Drops a deleted item and everything beneath it from the tree state. */
		purge(path) {
			delete this.entryIndex[path];
			delete this.nodes[path];
			const prefix = path.endsWith("\\") || path.endsWith("/") ? path : path + "\\";
			const altPrefix = path.endsWith("\\") ? path + "/" : path + "/";
			for (const key of Object.keys(this.entryIndex)) {
				if (key.startsWith(prefix) || key.startsWith(altPrefix)) delete this.entryIndex[key];
			}
			for (const key of Object.keys(this.nodes)) {
				if (key.startsWith(prefix) || key.startsWith(altPrefix)) delete this.nodes[key];
			}
			this.expanded = new Set([...this.expanded].filter(
				(expandedPath) => !expandedPath.startsWith(prefix) && !expandedPath.startsWith(altPrefix),
			));
		},

		async mutate(url, method, body) {
			const response = await fetch(url, {
				method,
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(body),
			});
			if (!response.ok) throw new Error(await readErrorBody(response));
			return response.json();
		},

		async apiDelete(url) {
			const response = await fetch(url, { method: "DELETE" });
			if (!response.ok) throw new Error(await readErrorBody(response));
			return response.json();
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
