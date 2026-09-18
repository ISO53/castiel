<template>
	<DocumentShell doc-id="evidence" v-slot="{ data, persist }">
		<div class="flex h-full min-h-0 flex-col">
			<EmptyHint
				v-if="artifactsOf(data).length === 0"
				:icon="ImageIcon"
				message="No evidence captured yet."
				hint="Artifacts registered in evidence.json appear here; binaries stay on disk under evidence/."
			/>
			<DataTable
				v-else
				:columns="columns"
				:data="artifactsOf(data)"
				search-placeholder="Filter artifacts…"
				empty-message="No artifacts match the filter."
				@row-click="(row) => (selected = row)"
			>
				<template #cell-name="{ row, value }">
					<span class="flex items-center gap-1.5">
						<component :is="kindIcon(row.kind)" class="size-3 shrink-0 text-muted-foreground" />
						<span class="font-mono text-[11px]" :title="value">{{ value }}</span>
					</span>
				</template>
				<template #cell-kind="{ value }">
					<span class="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{{ value || "other" }}</span>
				</template>
				<template #cell-title="{ value }">
					<span class="block max-w-[36ch] truncate" :title="value">{{ value || "—" }}</span>
				</template>
				<template #cell-target="{ value }">
					<span class="block max-w-[28ch] truncate font-mono text-[11px]" :title="value">{{ value || "—" }}</span>
				</template>
				<template #cell-vulnerability="{ value }">
					<span v-if="value" class="font-mono text-[11px] text-sky-400" :title="value">{{ value }}</span>
					<span v-else class="text-muted-foreground">—</span>
				</template>
				<template #cell-secret="{ row }">
					<span v-if="row.secret" class="flex items-center gap-1 font-mono text-[11px]">
						{{ maskSecret(row.secret) }}
						<Button
							variant="ghost"
							size="icon-sm"
							class="size-5"
							:aria-label="copiedName === row.name ? 'Copied' : 'Copy secret'"
							@click.stop="copySecret(row)"
						>
							<Check v-if="copiedName === row.name" class="text-emerald-400" />
							<Copy v-else class="text-muted-foreground" />
						</Button>
					</span>
					<span v-else class="text-muted-foreground">—</span>
				</template>
				<template #cell-discoveredBy="{ value }">
					{{ value || "—" }}
				</template>
				<template #cell-discoveredAt="{ value }">
					<span class="whitespace-nowrap tabular-nums text-muted-foreground">{{ relative(value) }}</span>
				</template>
			</DataTable>
			<ArtifactDetailDialog :artifact="selected" :persist="persist" @close="selected = null" />
		</div>
	</DocumentShell>
</template>

<script setup>
import { Check, Copy, FileText, Image as ImageIcon, Key, ScrollText, TerminalSquare } from "@lucide/vue";
import { ref } from "vue";
import ArtifactDetailDialog from "@/components/views/panels/ArtifactDetailDialog.vue";
import DataTable from "@/components/views/DataTable.vue";
import DocumentShell from "@/components/views/DocumentShell.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";
import { Button } from "@/components/ui/button";
import { maskSecret, normalizeArtifact } from "@/lib/evidence";

const selected = ref(null);
const copiedName = ref("");
let copiedTimer = null;

const KIND_ICONS = {
	screenshot: ImageIcon,
	scan: TerminalSquare,
	capture: TerminalSquare,
	poc: FileText,
	log: ScrollText,
	credential: Key,
	other: FileText,
};

function kindIcon(kind) {
	return KIND_ICONS[kind] ?? FileText;
}

function artifactsOf(data) {
	return (data?.artifacts ?? [])
		.map(normalizeArtifact)
		.filter((artifact) => artifact?.name)
		.map((artifact) => ({
			...artifact,
			// Raw ISO value for DataTable sorting; the cell renders it as relative time.
			discoveredAt: artifact.discovered_at ?? "",
		}));
}

async function copySecret(artifact) {
	await navigator.clipboard.writeText(artifact.secret ?? "");
	copiedName.value = artifact.name;
	clearTimeout(copiedTimer);
	copiedTimer = setTimeout(() => {
		copiedName.value = "";
	}, 1500);
}

function relative(timestamp) {
	const time = Date.parse(timestamp ?? "");
	if (!Number.isFinite(time)) return "—";
	const seconds = Math.floor((Date.now() - time) / 1000);
	if (seconds < 60) return "just now";
	if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
	if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
	return `${Math.floor(seconds / 86400)}d ago`;
}

const columns = [
	{ accessorKey: "name", header: "Name" },
	{ accessorKey: "kind", header: "Kind" },
	{ accessorKey: "title", header: "Title" },
	{
		id: "target",
		accessorFn: (row) => row.target,
		header: "Target",
	},
	{
		id: "vulnerability",
		accessorFn: (row) => row.vulnerability,
		header: "Vulnerability",
	},
	{
		id: "secret",
		accessorFn: (row) => row.secret,
		header: "Secret",
		enableSorting: false,
	},
	{
		id: "discoveredBy",
		accessorFn: (row) => row.discovered_by,
		header: "Captured By",
		enableSorting: false,
	},
	{
		id: "discoveredAt",
		accessorFn: (row) => row.discoveredAt,
		header: "Found",
	},
];
</script>
