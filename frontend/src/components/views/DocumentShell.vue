<template>
	<section class="flex h-full min-h-0 flex-col bg-background">
		<header class="flex h-8 shrink-0 items-center gap-2 border-b px-3">
			<component :is="icon" class="size-3.5 shrink-0 text-muted-foreground" />
			<p class="truncate text-xs font-medium text-foreground">{{ entry.label }}</p>
			<span class="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
				{{ entry.file }}
			</span>
			<span class="flex-1" />
			<Button
				variant="ghost"
				size="icon-sm"
				:disabled="saving || loading"
				:aria-label="`Refresh ${entry.label}`"
				@click="refresh()"
			>
				<RotateCw :class="(loading || saving) ? 'animate-spin' : ''" />
			</Button>
		</header>

		<div class="min-h-0 flex-1 overflow-hidden p-2">
			<p v-if="error" class="px-2 py-2 text-xs leading-relaxed text-destructive wrap-break-word">{{ error }}</p>
			<div v-else-if="loading && !hasData" class="flex items-center gap-2 px-2 py-2 text-xs text-muted-foreground">
				<Spinner class="size-3 shrink-0" /> Loading {{ entry.file }}…
			</div>
			<div v-else-if="!hasData" class="px-2 py-2 text-xs leading-relaxed text-muted-foreground">
				No workspace open.
			</div>
			<slot v-else :data="data" :refresh="refresh" :persist="persist" :saving="saving" />
		</div>
	</section>
</template>

<script>
import { Bug, Globe, Image as ImageIcon, KeyRound, ListChecks, Network, RotateCw, Users } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { documentByFile, saveDocument } from "@/lib/documents";
import { useEngagementDocument } from "@/components/views/useEngagementDocument";
import { useWorkspaceStore } from "@/stores/workspace";

const ICONS = {
	network: Network,
	web: Globe,
	findings: Bug,
	credentials: KeyRound,
	identity: Users,
	evidence: ImageIcon,
	tasks: ListChecks,
};

/**
 * Shared chrome for engagement-document views: header with icon, file chip and refresh,
 * plus loading & error states. Content renders through the default slot, which receives
 * { data, refresh, persist, saving }.
 */
export default {
	name: "DocumentShell",

	props: {
		docId: { type: String, required: true },
	},

	setup(props) {
		const entry = documentByFile(`${props.docId}.json`);
		if (!entry) throw new Error(`Unknown engagement document: ${props.docId}`);
		const state = useEngagementDocument(entry.file);
		return { entry, ...state };
	},

	data() {
		return { saving: false, workspace: useWorkspaceStore() };
	},

	computed: {
		hasData() {
			return this.data !== null && this.data !== undefined;
		},
		icon() {
			return ICONS[this.entry.id] ?? null;
		},
	},

	methods: {
		/** Writes the current in-memory document back to disk. */
		async persist() {
			this.saving = true;
			try {
				await saveDocument(this.entry.file, this.workspace.cwd, this.data);
			} finally {
				this.saving = false;
			}
		},
	},
};
</script>