<template>
	<!-- Inline rename editor replaces the row while active -->
	<div v-if="fileTreeUi.renamingPath === entry.path" class="flex items-center px-2 py-0.5">
		<input ref="renameField" v-model="renameDraft" type="text"
			class="h-6 w-full rounded border border-primary bg-background px-1.5 text-xs outline-none"
			spellcheck="false" autocomplete="off"
			@keydown.enter.prevent="actions.commitRename(renameDraft)"
			@keydown.esc.prevent="actions.cancelRename()"
			@blur="actions.commitRename(renameDraft)" />
	</div>

	<ContextMenu v-else>
		<ContextMenuTrigger as-child>
			<FileTreeFolder v-if="entry.directory" :path="entry.path" :name="entry.name">
				<!-- Inline create input, first child of the targeted folder -->
				<div v-if="fileTreeUi.creatingIn === entry.path" class="flex items-center px-1 py-0.5">
					<input ref="createField" v-model="createDraft" type="text"
						class="h-6 w-full rounded border border-primary bg-background px-1.5 text-xs outline-none"
						:placeholder="fileTreeUi.creatingDirectory ? 'Folder name…' : 'File name…'"
						spellcheck="false" autocomplete="off"
						@keydown.enter.prevent="actions.commitCreate(createDraft)"
						@keydown.esc.prevent="actions.cancelCreate()"
						@blur="actions.cancelCreate()" />
				</div>

				<div v-if="isLoading(entry.path)"
					class="flex items-center gap-1.5 px-2 py-1 text-[11px] text-muted-foreground">
					<Spinner class="size-3 shrink-0" />
					<span>Loading…</span>
				</div>
				<p v-else-if="!childrenOf(entry.path).length" class="px-2 py-1 text-[11px] text-muted-foreground">
					Empty folder
				</p>
				<template v-else>
					<FileTreeEntry v-for="child in childrenOf(entry.path)" :key="child.path" :entry="child"
						:nodes="nodes" :renaming-path="renamingPath" :creating-in="creatingIn" />
				</template>
			</FileTreeFolder>
			<FileTreeFile v-else :path="entry.path" :name="entry.name" />
		</ContextMenuTrigger>

		<ContextMenuContent class="w-40 text-xs">
			<template v-if="entry.directory">
				<ContextMenuItem @select="actions.startCreate(entry.path, false)">
					New File
				</ContextMenuItem>
				<ContextMenuItem @select="actions.startCreate(entry.path, true)">
					New Folder
				</ContextMenuItem>
				<ContextMenuSeparator />
			</template>
			<ContextMenuItem @select="actions.startRename(entry.path)">
				Rename
			</ContextMenuItem>
			<ContextMenuSeparator />
			<ContextMenuItem class="focus:text-destructive" @select="actions.remove(entry)">
				Delete
			</ContextMenuItem>
		</ContextMenuContent>
	</ContextMenu>
</template>

<script>
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuSeparator, ContextMenuTrigger } from "@/components/ui/context-menu";
import { Spinner } from "@/components/ui/spinner";
import { FileTreeFile, FileTreeFolder } from "@/components/ai-elements/file-tree";
import { fileTreeActions, fileTreeUi } from "@/components/file-tree-ui";
import { nextTick, ref, watch } from "vue";

// Recursive tree node renderer with context-menu CRUD actions. Folder children come
// from the parent's `nodes` map, which the root FileTreeView fills lazily as folders
// get expanded.
export default {
	name: "FileTreeEntry",
	components: {
		ContextMenuItem,
		ContextMenu,
		ContextMenuContent,
		ContextMenuSeparator,
		ContextMenuTrigger,
		FileTreeFile,
		FileTreeFolder,
		Spinner,
	},

	props: {
		entry: { type: Object, required: true },
		nodes: { type: Object, required: true },
		renamingPath: { type: String, default: "" },
		creatingIn: { type: String, default: "" },
	},

	setup(props) {
		const renameDraft = ref(props.entry.name);
		const renameField = ref(null);
		const createDraft = ref("");
		const createField = ref(null);

		watch(
			() => props.renamingPath,
			(path) => {
				if (path === props.entry.path) {
					renameDraft.value = props.entry.name;
					nextTick(() => renameField.value?.focus());
				}
			},
		);
		watch(
			() => props.creatingIn,
			(folderPath) => {
				if (folderPath === props.entry.path) {
					createDraft.value = "";
					nextTick(() => createField.value?.focus());
				}
			},
		);

		return {
			actions: fileTreeActions,
			fileTreeUi,
			renameDraft,
			renameField,
			createDraft,
			createField,
		};
	},

	methods: {
		isLoading(path) {
			return Boolean(this.nodes[path]?.loading);
		},
		childrenOf(path) {
			return this.nodes[path]?.children ?? [];
		},
	},
};
</script>
