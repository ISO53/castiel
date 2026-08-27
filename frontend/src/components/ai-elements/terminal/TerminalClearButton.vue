<script setup>
import { Trash2Icon } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { useTerminalContext } from "./context";

const { hasClear, onClear } = useTerminalContext("TerminalClearButton");
</script>

<template>
	<Button
		v-if="hasClear"
		:class="'size-7 shrink-0 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100'"
		size="icon"
		variant="ghost"
		title="Clear view"
		@click="onClear"
	>
		<Trash2Icon :size="14" />
	</Button>
</template>
