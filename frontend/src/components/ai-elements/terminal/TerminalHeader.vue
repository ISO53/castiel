<script setup>
import { useTerminalContext } from "./context";

useTerminalContext("TerminalHeader");
</script>

<template>
	<div
		class="flex items-center justify-between border-b border-zinc-800 px-4 py-2"
	>
		<slot />
	</div>
</template>

