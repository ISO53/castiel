<script setup>
import { computed } from "vue";
import { ChevronDown } from "@lucide/vue";
import { cn } from "@/lib/utils";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Shimmer } from "../shimmer";
import { toolIcon } from "@/lib/tool-icons";
import ToolCallParams from "./ToolCallParams.vue";

const props = defineProps({
	name: { type: String, required: true },
	arguments: { type: String, default: "" },
	result: { type: [String, Object, Array], default: null },
	class: { type: null, default: "" },
});

const isOpen = defineModel("open", { type: Boolean, default: false });

// Shimmering while the call is in flight; a finished call simply stops
// shimmering — no badge or extra state icon needed.
const running = computed(() => props.result === null);

const icon = computed(() => toolIcon(props.name));

// Input arrives as a raw JSON string mid-stream; fall back to wrapping it so
// partially streamed arguments still render instead of being dropped.
const input = computed(() => {
	try {
		return JSON.parse(props.arguments || "{}");
	} catch {
		return props.arguments ? { input: props.arguments } : {};
	}
});

const hasParams = computed(
	() =>
		input.value !== null &&
		typeof input.value === "object" &&
		!Array.isArray(input.value) &&
		Object.keys(input.value).length > 0,
);

const summaryParams = computed(() => {
	if (!hasParams.value) return "";
	const params = Object.entries(input.value)
		.map(([key, value]) => `${key}: ${JSON.stringify(value)}`)
		.join(", ");
	return `(${params})`;
});

const summary = computed(() => props.name + summaryParams.value);

/**
 * Output is plain wrapped text. It sits directly beneath a header that already states
 * what was called, so it needs no label or syntax highlighting to read as the result.
 * It cannot push the transcript sideways.
 */
const resultText = computed(() => {
	if (typeof props.result === "string") return props.result;
	try {
		return JSON.stringify(props.result, null, 2);
	} catch {
		return String(props.result);
	}
});
</script>

<template>
	<Collapsible v-model:open="isOpen" :class="cn('group/tool-call not-prose w-full min-w-0', props.class)">
		<!-- An open call keeps the hover colour, so the header still reads as the active
		     control once its body is showing rather than dimming back to idle. -->
		<CollapsibleTrigger
			class="flex w-full min-w-0 items-center gap-2 text-left text-muted-foreground text-xs transition-colors hover:text-foreground data-[state=open]:text-foreground">
			<component :is="icon" class="size-3.5 shrink-0" />
			<!-- The name in bold with the arguments beside it, on one line: the body
			     repeats them in full, so the header only has to be a summary. -->
			<span class="min-w-0 flex-1 truncate">
				<Shimmer v-if="running" as="span" :duration="1">{{ summary }}</Shimmer>
				<template v-else>
					<span class="font-bold">{{ name }}</span><span v-if="hasParams">{{ summaryParams }}</span>
				</template>
			</span>
			<ChevronDown class="size-3.5 shrink-0 transition-all group-data-[state=open]/tool-call:rotate-180" />
		</CollapsibleTrigger>

		<CollapsibleContent
			class="outline-none data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:slide-out-to-top-2 data-[state=open]:animate-in data-[state=open]:slide-in-from-top-2">
			<div class="space-y-2 px-1 py-1.5">
				<ToolCallParams v-if="hasParams" :input="input" class="text-muted-foreground/70" />
				<!-- Output sits at the same weight of colour as the arguments; monospace
				     alone marks it as the result. -->
				<pre v-if="result !== null"
					class="min-w-0 overflow-hidden wrap-break-word whitespace-pre-wrap font-mono text-xs text-muted-foreground/70">
					{{ resultText }}</pre>
			</div>
		</CollapsibleContent>
	</Collapsible>
</template>
