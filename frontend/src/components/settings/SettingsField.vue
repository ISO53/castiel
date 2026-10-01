<template>
	<div class="flex flex-col gap-1.5">
		<Label :for="id">{{ label }}</Label>
		<slot />
		<p v-if="hint" class="text-[11px] text-muted-foreground">{{ hint }}</p>
	</div>
</template>

<script setup>
import { Label } from "@/components/ui/label";

/**
 * One settings row: label, control and an optional hint. Keeps every section
 * looking the same as new settings are added.
 */
defineProps({
	id: { type: String, default: "" },
	label: { type: String, required: true },
	hint: { type: String, default: "" },
});
</script>
