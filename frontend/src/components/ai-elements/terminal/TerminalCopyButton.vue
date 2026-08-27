<script setup>
import { ref } from "vue";
import { CopyIcon, CheckIcon } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { useTerminalContext } from "./context";

const props = defineProps({
	timeout: { type: Number, default: 2000 },
});
const emit = defineEmits(["copy", "error"]);
const { output } = useTerminalContext("TerminalCopyButton");
const copied = ref(false);

async function copy() {
	try {
		await navigator.clipboard.writeText(output.value || "");
		copied.value = true;
		emit("copy");
		setTimeout(() => (copied.value = false), props.timeout);
	} catch (error) {
		emit("error", error);
	}
}
</script>

<template>
	<Button
		:class="'size-7 shrink-0 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100'"
		size="icon"
		variant="ghost"
		:title="copied ? 'Copied' : 'Copy output'"
		@click="copy"
	>
		<CheckIcon v-if="copied" :size="14" />
		<CopyIcon v-else :size="14" />
	</Button>
</template>
