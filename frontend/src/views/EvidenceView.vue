<template>
	<DocumentShell doc-id="evidence" v-slot="{ data }">
		<div class="flex h-full min-h-0 flex-col gap-2 bg-card">
			<div v-if="(data?.artifacts ?? []).length > 0" class="relative shrink-0">
				<Search class="absolute left-2 top-1/2 size-3 -translate-y-1/2 text-muted-foreground" />
				<input v-model="filter"
					class="h-7 w-full border-b bg-cart pl-7 pr-2 text-xs text-foreground outline-none placeholder:text-muted-foreground focus:border-ring"
					placeholder="Filter artifacts…" />
			</div>

			<div v-if="filtered(data).length > 0"
				class="grid min-h-0 flex-1 auto-rows-min grid-cols-2 gap-2 overflow-y-auto sm:grid-cols-3 lg:grid-cols-4">
				<article v-for="artifact in filtered(data)" :key="artifact.path"
					class="group flex flex-col overflow-hidden rounded-md border bg-background"
					:class="isDirectory(artifact) ? '' : 'cursor-pointer hover:border-ring'"
					@click="openArtifact(artifact)">
					<div class="flex h-24 items-center justify-center overflow-hidden bg-muted/40">
						<img v-if="isImage(artifact)" :src="rawUrl(artifact.path)"
							:alt="artifact.description ?? fileName(artifact.path)" class="h-full w-full object-cover"
							loading="lazy" />
						<Folder v-else-if="isDirectory(artifact)" class="size-6 text-muted-foreground/50" />
						<FileText v-else class="size-6 text-muted-foreground/50" />
					</div>
					<div class="min-w-0 p-1.5">
						<p class="truncate font-mono text-[10px] text-muted-foreground"
							:title="fileName(artifact.path)">
							{{ fileName(artifact.path) }}
						</p>
						<p v-if="artifact.description"
							class="mt-0.5 line-clamp-2 text-[11px] leading-snug text-foreground">
							{{ artifact.description }}
						</p>
					</div>
				</article>
			</div>

			<EmptyHint v-else-if="(data?.artifacts ?? []).length === 0" :icon="ImageIcon"
				message="No evidence captured yet."
				hint="Artifacts registered in evidence.json appear here; binaries stay on disk under evidence/." />
			<p v-else class="px-2 py-2 text-xs text-muted-foreground">No artifacts match the filter.</p>
		</div>
	</DocumentShell>
</template>

<script setup>
import { FileText, Folder, Image as ImageIcon, Search } from "@lucide/vue";
import { ref } from "vue";
import DocumentShell from "@/components/views/DocumentShell.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";
import { joinWorkspacePath } from "@/lib/documents";
import { useTabsStore } from "@/stores/tabs";
import { useWorkspaceStore } from "@/stores/workspace";

const FILES_API = `${window.location.origin}/api/files`;

const tabs = useTabsStore();
const workspace = useWorkspaceStore();
const filter = ref("");

const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif"]);

function fileName(path) {
	return path.split(/[\\/]/).pop() ?? path;
}

function extension(path) {
	return fileName(path).split(".").pop()?.toLowerCase() ?? "";
}

function isImage(artifact) {
	return IMAGE_EXTENSIONS.has(extension(artifact.path));
}

function rawUrl(path) {
	return `${FILES_API}/raw?path=${encodeURIComponent(joinWorkspacePath(workspace.cwd ?? "", path))}`;
}

function isDirectory(artifact) {
	return artifact.path.endsWith("/") || artifact.path.endsWith("\\");
}

function filtered(data) {
	const artifacts = (data?.artifacts ?? [])
		.map((artifact) => ({
			...artifact,
			path: artifact.path ?? artifact.file ?? artifact.location ?? "",
			description: artifact.description ?? artifact.note ?? "",
			entity: artifact.related_to ?? artifact.entity ?? "",
		}))
		.filter((artifact) => artifact?.path);
	const needle = filter.value.trim().toLowerCase();
	if (!needle) return artifacts;
	return artifacts.filter((artifact) =>
		`${artifact.path} ${artifact.description} ${artifact.entity}`.toLowerCase().includes(needle),
	);
}

async function openArtifact(artifact) {
	if (isDirectory(artifact)) return;
	if (!isImage(artifact)) {
		const path = joinWorkspacePath(workspace.cwd ?? "", artifact.path);
		tabs.openTab({ value: `file:${path}`, label: fileName(artifact.path), component: "FileEditorView", path });
		return;
	}

	// Lightbox over every visible image so users can swipe through evidence.
	const [{ default: PhotoSwipe }] = await Promise.all([import("photoswipe"), import("photoswipe/dist/photoswipe.css")]);
	const slides = filtered({ artifacts: [artifact] }).map((entry) => ({ src: rawUrl(entry.path), title: entry.description }));
	new PhotoSwipe({
		dataSource: slides,
		showHideAnimationType: "zoom",
	}).init();
}
</script>
