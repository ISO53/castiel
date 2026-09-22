<script setup>
import { computed } from "vue";
import { ChevronDown } from "@lucide/vue";
import { cn } from "@/lib/utils";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { CodeBlock } from "../code-block";
import { Shimmer } from "../shimmer";
import { toolIcon } from "@/lib/tool-icons";

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

const prettyInput = computed(() => JSON.stringify(input.value, null, 2));

const isError = computed(
	() => typeof props.result === "string" && props.result.startsWith("Error"),
);

const prettyResult = computed(() =>
	typeof props.result === "string" ? props.result : JSON.stringify(props.result, null, 2),
);

// Compact one-line signature for the collapsed header, e.g.
// `bash(command: "nmap -sV 10.0.0.0/24")`. CSS truncates the overflow.
const summaryParams = computed(() => {
	if (!input.value || typeof input.value !== "object" || Array.isArray(input.value)) {
		return "";
	}
	const entries = Object.entries(input.value);
	if (!entries.length) return "()";
	const params = entries
		.map(([key, value]) => `${key}: ${JSON.stringify(value)}`)
		.join(", ");
	return `(${params})`;
});

const summary = computed(() => props.name + summaryParams.value);
</script>

<template>
	<Collapsible
		v-model:open="isOpen"
		:class="cn('group/tool-call not-prose w-full', props.class)"
	>
		<CollapsibleTrigger
			class="flex w-full items-center gap-2 text-left text-muted-foreground text-xs transition-colors hover:text-foreground"
		>
			<component :is="icon" class="size-3.5 shrink-0" />
			<span class="min-w-0 truncate">
				<Shimmer v-if="running" as="span" :duration="1">{{ summary }}</Shimmer>
				<template v-else><span class="font-bold">{{ name }}</span><span>{{ summaryParams }}</span></template>
			</span>
			<ChevronDown
				class="size-3.5 shrink-0 transition-all group-data-[state=open]/tool-call:rotate-180"
			/>
		</CollapsibleTrigger>

		<CollapsibleContent
			class="outline-none data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:slide-out-to-top-2 data-[state=open]:animate-in data-[state=open]:slide-in-from-top-2"
		>
			<div class="space-y-3 px-1 py-1.5">
				<section class="space-y-1.5">
					<h4 class="text-[10px] font-medium uppercase tracking-wider text-muted-foreground/70">
						Parameters
					</h4>
					<CodeBlock :code="prettyInput" language="json" class="border-0 bg-muted/40 text-xs" />
				</section>

				<section v-if="result !== null" class="space-y-1.5">
					<h4 class="text-[10px] font-medium uppercase tracking-wider text-muted-foreground/70">
						{{ isError ? "Error" : "Result" }}
					</h4>
					<pre
						v-if="isError"
						class="overflow-x-auto rounded-md bg-destructive/10 p-2 text-xs text-destructive whitespace-pre-wrap"
					>{{ prettyResult }}</pre>
					<CodeBlock
						v-else
						:code="prettyResult"
						language="json"
						class="border-0 bg-muted/40 text-xs"
					/>
				</section>
			</div>
		</CollapsibleContent>
	</Collapsible>
</template>
