<script setup>
import { computed } from "vue";
import { useTerminalContext } from "./context";

const context = useTerminalContext("TerminalTitle");
const label = computed(() =>
	context.isStreaming.value ? "process output · running" : "process output",
);
</script>

<template>
	<span class="select-none text-xs tracking-wide text-zinc-500 uppercase">
		{{ label }}
	</span>
</template>

