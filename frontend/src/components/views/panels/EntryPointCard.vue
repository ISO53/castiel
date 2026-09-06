<template>
	<div
		class="group rounded-lg border border-border bg-background p-3"
	>
		<div class="flex items-start justify-between gap-2">
			<div class="flex min-w-0 items-center gap-2">
				<span :title="authTitle" class="shrink-0 text-muted-foreground">
					<LockOpen v-if="ep.auth === 'public'" class="size-3.5" />
					<Lock v-else-if="ep.auth === 'authenticated'" class="size-3.5" />
					<CircleHelp v-else class="size-3.5" />
				</span>
				<span
					class="shrink-0 rounded bg-primary/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-primary"
				>
					{{ ep.kind }}
				</span>
				<p class="min-w-0 truncate text-xs font-semibold text-foreground" :title="ep.name">
					{{ ep.name }}
				</p>
			</div>
			<p v-if="ep.method || ep.action" class="shrink-0 font-mono text-[10px] text-muted-foreground">
				<span :class="methodClass">{{ ep.method ?? "-" }}</span>
				{{ ep.action ?? "" }}
			</p>
		</div>

		<div class="mt-1.5 flex flex-wrap items-center gap-1.5 text-[10px]">
			<span class="font-mono text-muted-foreground" :title="ep.page">{{ pageLabel }}</span>
		</div>

		<div v-if="ep.fields?.length" class="mt-2 flex flex-wrap gap-1">
			<span
				v-for="field in ep.fields"
				:key="field.name"
				class="rounded border border-border/70 bg-muted/40 px-1.5 py-0.5 font-mono text-[10px] text-foreground/90"
			>
				{{ field.name }}<span v-if="field.type" class="text-muted-foreground"> : {{ field.type }}</span>
			</span>
		</div>

		<p v-if="ep.description" class="mt-2 text-[11px] leading-relaxed text-foreground/90">
			{{ ep.description }}
		</p>

		<div class="mt-2.5 flex items-center justify-between gap-2 border-t border-border/50 pt-2">
			<p class="min-w-0 truncate text-[10px] text-muted-foreground">
				{{ footerLine }}
			</p>
			<Button
				variant="default"
				size="sm"
				class="h-6 shrink-0 gap-1 px-2 text-[10px] opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100"
			>
				<MessageSquareText class="size-3" />
				Ask AI
			</Button>
		</div>
	</div>
</template>

<script setup>
import { computed } from "vue";
import { CircleHelp, Lock, LockOpen, MessageSquareText } from "@lucide/vue";
import { Button } from "@/components/ui/button";

const props = defineProps({ ep: { type: Object, required: true } });
defineEmits(["ask"]);

const methodClass = computed(() => METHOD_CLASSES[props.ep.method?.toUpperCase()] ?? "text-sky-400");

const METHOD_CLASSES = {
	GET: "text-emerald-400",
	POST: "text-amber-400",
	PUT: "text-sky-400",
	DELETE: "text-red-400",
};

const pageLabel = computed(() => props.ep.page?.replace(/^https?:\/\//, "") ?? "");

const authTitle = computed(() => {
	if (props.ep.auth === "public") return "Public. No authentication required";
	if (props.ep.auth === "authenticated") return "Requires authentication";
	return "Authentication state unknown";
});

const footerLine = computed(() =>
	[
		(props.ep.evidence ?? []).join(", ") || null,
		props.ep.discovered_by ?? null,
	]
		.filter(Boolean)
		.join(" · "),
);
</script>
