<script setup>
import { useTerminalContext } from "./context";

const { isStreaming } = useTerminalContext("TerminalStatus");
</script>

<template>
	<span class="flex items-center gap-1.5 pr-2 text-xs text-zinc-400">
		<span
			class="size-2 rounded-full"
			:class="isStreaming ? 'animate-pulse bg-emerald-400' : 'bg-zinc-600'"
		/>
		{{ isStreaming ? "live" : "idle" }}
	</span>
</template>
