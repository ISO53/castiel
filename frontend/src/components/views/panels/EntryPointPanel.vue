<template>
	<div class="flex h-full min-h-0 flex-col gap-2 p-2">
		<div class="flex flex-wrap items-center gap-2">
			<Input v-model="search" placeholder="Filter entry points…" class="h-8 max-w-56 flex-1 text-xs" />
			<Select v-model="kindFilter">
				<SelectTrigger class="h-8 w-36 text-xs">
					<SelectValue placeholder="Kind" />
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="all">All kinds</SelectItem>
					<SelectItem v-for="kind in KINDS" :key="kind" :value="kind">{{ kind }}</SelectItem>
				</SelectContent>
			</Select>
			<Select v-model="authFilter">
				<SelectTrigger class="h-8 w-40 text-xs">
					<SelectValue placeholder="Auth" />
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="all">All auth</SelectItem>
					<SelectItem value="public">public</SelectItem>
					<SelectItem value="authenticated">authenticated</SelectItem>
					<SelectItem value="unknown">unknown</SelectItem>
				</SelectContent>
			</Select>
			<span class="ml-auto shrink-0 font-mono text-[10px] text-muted-foreground">
				{{ filtered.length }}/{{ all.length }}
			</span>
		</div>

		<EmptyHint
			v-if="all.length === 0"
			:icon="MousePointerClick"
			message="No entry points discovered yet."
			hint="Forms, uploads and request-triggering buttons appear here as interactive cards once the agent maps the pages."
		/>
		<p v-else-if="filtered.length === 0" class="text-xs text-muted-foreground">
			No entry points match the filters.
		</p>
		<ScrollArea v-else class="min-h-0 flex-1">
			<div class="grid auto-rows-min content-start gap-2 pr-3">
				<EntryPointCard v-for="entry in filtered" :key="entryKey(entry)" :ep="entry" />
			</div>
		</ScrollArea>
	</div>
</template>

<script setup>
import { computed, ref } from "vue";
import { MousePointerClick } from "@lucide/vue";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import EmptyHint from "@/components/views/EmptyHint.vue";
import EntryPointCard from "@/components/views/panels/EntryPointCard.vue";

const props = defineProps({ data: { type: Object, default: null } });

const KINDS = ["form", "input", "file-upload", "button-action", "api-trigger"];

const search = ref("");
const kindFilter = ref("all");
const authFilter = ref("all");

const all = computed(() => (props.data?.entry_points ?? []).filter((entry) => entry?.name || entry?.action));

function entryKey(entry) {
	return [entry.page, entry.method, entry.action, entry.name].filter(Boolean).join("|");
}

const filtered = computed(() =>
	all.value.filter((entry) => {
		if (kindFilter.value !== "all" && (entry.kind ?? "input") !== kindFilter.value) return false;
		if (authFilter.value !== "all" && (entry.auth ?? "unknown") !== authFilter.value) return false;
		const needle = search.value.trim().toLowerCase();
		if (!needle) return true;
		const haystack = [
			entry.name,
			entry.page,
			entry.action,
			entry.description,
			entry.notes,
			...(entry.fields ?? []).map((field) => field.name),
		]
			.filter(Boolean)
			.join(" ")
			.toLowerCase();
		return haystack.includes(needle);
	}),
);
</script>