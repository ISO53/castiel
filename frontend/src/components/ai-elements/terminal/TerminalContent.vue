<script setup>
import { computed, nextTick, ref, watch } from "vue";
import Ansi from "ansi-to-vue3";
import { useTerminalContext } from "./context";

const { output, isStreaming, autoScroll } =
	useTerminalContext("TerminalContent");

const body = ref(null);
const source = computed(() => output.value || "");

watch(source, async () => {
	if (!autoScroll.value || !body.value) return;
	await nextTick();
	body.value.scrollTop = body.value.scrollHeight;
});
</script>

<template>
	<div ref="body" class="min-h-0 flex-1 overflow-auto px-4 py-3">
		<pre class="whitespace-pre-wrap break-words font-mono text-xs leading-relaxed"><Ansi
				:linkify="false"
			>{{ source }}</Ansi><span
				v-if="isStreaming"
				class="ml-0.5 inline-block h-3 w-[7px] translate-y-0.5 animate-pulse bg-emerald-400"
			/></pre>
	</div>
</template>
