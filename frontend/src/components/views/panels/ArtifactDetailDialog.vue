<template>
	<Dialog :open="!!artifact" @update:open="(value) => !value && emit('close')">
		<ScrollArea>
			<DialogContent v-if="artifact" class="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
				<DialogHeader>
					<div class="flex items-start justify-between gap-3 pr-6">
						<DialogTitle class="text-base leading-snug">{{ headerTitle }}</DialogTitle>
						<span
							class="shrink-0 rounded bg-muted px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-muted-foreground"
							>{{ artifact.kind ?? "other" }}</span
						>
					</div>
					<DialogDescription v-if="headerSubtitle" class="font-mono text-xs">
						{{ headerSubtitle }}
					</DialogDescription>
				</DialogHeader>

				<div class="flex flex-col gap-3 text-xs leading-relaxed">
					<section v-if="isViewable">
						<div class="mb-0.5 flex items-center justify-between">
							<p class="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Preview</p>
							<Button v-if="isImage" variant="ghost" size="sm" class="h-6 gap-1 px-2 text-[10px]" @click="openLightbox">
								<Maximize2 /> Fullscreen
							</Button>
						</div>
						<button v-if="isImage" type="button" class="block w-full cursor-zoom-in" @click="openLightbox">
							<img
								:src="rawUrl"
								:alt="artifact.description || fileName"
								class="max-h-72 w-full rounded-md border object-contain bg-muted/30"
								loading="lazy"
							/>
						</button>
						<div v-else class="group relative rounded-md border bg-muted/30">
							<Button
								variant="ghost"
								size="icon-sm"
								class="absolute right-1 top-1"
								:aria-label="copied ? 'Copied' : 'Copy preview'"
								@click="copyContent"
							>
								<Check v-if="copied" />
								<Copy v-else />
							</Button>
							<pre
								class="max-h-72 overflow-auto px-3 py-2 pr-9 font-mono text-[11px] leading-relaxed whitespace-pre-wrap break-all"
								>{{ content || "Loading…" }}</pre
							>
						</div>
					</section>

					<section v-if="isCredential">
						<p class="mb-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Secret</p>
						<div class="flex items-center gap-1.5 rounded-md border bg-muted/30 px-2 py-1.5">
							<p class="min-w-0 flex-1 truncate font-mono text-[11px]">
								{{ revealed ? artifact.secret : maskSecret(artifact.secret) }}
							</p>
							<Button variant="ghost" size="icon-sm" aria-label="Toggle secret visibility" @click="revealed = !revealed">
								<EyeOff v-if="revealed" />
								<Eye v-else />
							</Button>
							<Button variant="ghost" size="icon-sm" :aria-label="copied ? 'Copied' : 'Copy secret'" @click="copySecret">
								<Check v-if="copied" />
								<Copy v-else />
							</Button>
						</div>
					</section>

					<section v-if="artifact.description">
						<p class="mb-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Description</p>
						<p class="whitespace-pre-wrap">{{ artifact.description }}</p>
					</section>

					<section>
						<p class="mb-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Metadata</p>
						<dl class="grid grid-cols-[9rem_minmax(0,1fr)] gap-x-3 gap-y-0.5">
							<template v-for="entry in metadata" :key="entry.label">
								<dt class="text-muted-foreground">{{ entry.label }}</dt>
								<dd class="min-w-0 truncate font-mono text-[11px]" :title="entry.value">
									{{ entry.value }}
								</dd>
							</template>
						</dl>
					</section>

					<section v-if="isCredential">
						<div class="mb-0.5 flex items-center justify-between">
							<p class="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Notes</p>
							<Button
								v-if="notesDirty"
								size="sm"
								variant="outline"
								class="h-6 px-2 text-[10px]"
								:disabled="saving"
								@click="saveNotes"
							>
								{{ saving ? "Saving…" : "Save notes" }}
							</Button>
						</div>
						<Textarea v-model="notes" :rows="3" class="text-xs" placeholder="Your notes — the agent never overwrites this." />
					</section>

					<section v-if="isViewable" class="flex items-center gap-2">
						<Button variant="outline" size="sm" class="h-7 gap-1.5 text-xs" @click="openInEditor">
							<SquarePen /> Open in editor
						</Button>
					</section>
				</div>
			</DialogContent>
		</ScrollArea>
	</Dialog>
</template>

<script setup>
import { Check, Copy, Eye, EyeOff, Maximize2, SquarePen } from "@lucide/vue";
import { computed, ref, watch } from "vue";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Textarea } from "@/components/ui/textarea";
import {
	artifactFileName,
	isCredentialArtifact,
	isImageArtifact,
	isViewableArtifact,
	maskSecret,
} from "@/lib/evidence";
import { joinWorkspacePath } from "@/lib/documents";
import { useTabsStore } from "@/stores/tabs";
import { useWorkspaceStore } from "@/stores/workspace";

const FILES_API = `${window.location.origin}/api/files`;
const TEXT_EXTENSIONS = new Set([
	"txt", "log", "json", "xml", "csv", "md", "html", "htm", "js", "py", "php", "sh", "ps1",
	"bat", "conf", "cfg", "ini", "yaml", "yml", "toml", "sql", "har", "env", "key", "pem",
	"crt", "cer", "list", "out",
]);

const props = defineProps({
	artifact: { type: Object, default: null },
	persist: { type: Function, required: true },
});

const emit = defineEmits(["close"]);

const tabs = useTabsStore();
const workspace = useWorkspaceStore();

const content = ref("");
const copied = ref(false);
const revealed = ref(false);
const notes = ref("");
const saving = ref(false);
let copiedTimer = null;

const fileName = computed(() => artifactFileName(props.artifact ?? {}));
const isImage = computed(() => isImageArtifact(props.artifact ?? {}));
const isCredential = computed(() => isCredentialArtifact(props.artifact ?? {}));
const isViewable = computed(() => isViewableArtifact(props.artifact ?? {}));
const rawUrl = computed(
	() => `${FILES_API}/raw?path=${encodeURIComponent(joinWorkspacePath(workspace.cwd ?? "", props.artifact?.name ?? ""))}`,
);

const headerTitle = computed(() => props.artifact?.title || fileName.value);
const headerSubtitle = computed(() => (props.artifact?.title ? fileName.value : props.artifact?.target || ""));

const metadata = computed(() => {
	const artifact = props.artifact ?? {};
	const rows = [
		{ label: "Name", value: artifact.name ?? "" },
		{ label: "Target", value: artifact.target ?? "" },
	];
	if (isCredential.value) {
		rows.push({ label: "Username", value: artifact.username ?? "" });
	} else if (artifact.vulnerability) {
		rows.push({ label: "Vulnerability", value: artifact.vulnerability });
	}
	rows.push(
		{ label: "Captured by", value: artifact.discovered_by ?? "" },
		{ label: "Captured at", value: artifact.discovered_at ?? "" },
	);
	return rows.filter((row) => row.value);
});

watch(
	() => props.artifact,
	(artifact) => {
		notes.value = artifact?.notes ?? "";
		revealed.value = false;
		content.value = "";
		copied.value = false;
		if (artifact && isViewableArtifact(artifact) && !isImageArtifact(artifact)) loadContent(artifact);
	},
	{ immediate: true },
);

const notesDirty = computed(() => props.artifact !== null && notes.value !== (props.artifact.notes ?? ""));

async function loadContent(artifact) {
	try {
		const query = new URLSearchParams({ path: joinWorkspacePath(workspace.cwd ?? "", artifact.name) });
		const response = await fetch(`${FILES_API}/content?${query.toString()}`);
		if (!response.ok) throw new Error(await response.text());
		const payload = await response.json();
		content.value = typeof payload?.content === "string" ? payload.content.slice(0, 10000) : "";
	} catch {
		content.value = "Preview unavailable — open the file in the editor instead.";
	}
}

async function flashCopied() {
	copied.value = true;
	clearTimeout(copiedTimer);
	copiedTimer = setTimeout(() => {
		copied.value = false;
	}, 1500);
}

async function copyContent() {
	await navigator.clipboard.writeText(content.value || "");
	await flashCopied();
}

async function copySecret() {
	await navigator.clipboard.writeText(props.artifact?.secret ?? "");
	await flashCopied();
}

async function openLightbox() {
	const [{ default: PhotoSwipe }] = await Promise.all([
		import("photoswipe"),
		import("photoswipe/dist/photoswipe.css"),
	]);
	new PhotoSwipe({
		dataSource: [{ src: rawUrl.value, title: props.artifact?.description ?? fileName.value }],
		showHideAnimationType: "zoom",
	}).init();
}

function openInEditor() {
	const path = joinWorkspacePath(workspace.cwd ?? "", props.artifact?.name ?? "");
	tabs.openTab({ value: `file:${path}`, label: fileName.value, component: "FileEditorView", path });
	emit("close");
}

async function saveNotes() {
	if (!props.artifact) return;
	saving.value = true;
	try {
		props.artifact.notes = notes.value;
		await props.persist();
	} catch (error) {
		console.error("Could not save notes:", error.message);
	} finally {
		saving.value = false;
	}
}
</script>
