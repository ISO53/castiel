<template>
	<FileTreeFolder v-if="entry.directory" :path="entry.path" :name="entry.name">
		<div v-if="isLoading(entry.path)"
			class="flex items-center gap-1.5 px-2 py-1 text-[11px] text-muted-foreground">
			<Spinner class="size-3 shrink-0" />
			<span>Loading…</span>
		</div>
		<p v-else-if="!childrenOf(entry.path).length" class="px-2 py-1 text-[11px] text-muted-foreground">
			Empty folder
		</p>
		<template v-else>
			<FileTreeEntry v-for="child in childrenOf(entry.path)" :key="child.path" :entry="child" :nodes="nodes" />
		</template>
	</FileTreeFolder>
	<FileTreeFile v-else :path="entry.path" :name="entry.name" />
</template>

<script>
import { Spinner } from "@/components/ui/spinner";
import { FileTreeFile, FileTreeFolder } from "@/components/ai-elements/file-tree";

// Recursive tree node renderer. Folder children come from the parent's `nodes`
// map, which the root FileTreeView fills lazily as folders get expanded.
export default {
	name: "FileTreeEntry",
	components: {
		FileTreeFile,
		FileTreeFolder,
		Spinner,
	},
	props: {
		entry: { type: Object, required: true },
		nodes: { type: Object, required: true },
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
