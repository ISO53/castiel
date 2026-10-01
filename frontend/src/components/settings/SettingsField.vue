<template>
	<div class="grid grid-cols-[1fr_auto] items-center gap-x-6 gap-y-1 border-b border-border py-2.5 last:border-b-0">
		<div class="min-w-0">
			<p class="text-xs font-medium text-foreground">{{ label }}</p>
			<p v-if="hint" class="mt-0.5 text-[11px] leading-relaxed text-muted-foreground">{{ hint }}</p>
		</div>
		<!-- controlClass lets a section widen its controls. Inputs are intrinsically
		     ~170px, which is too narrow for the long values these fields hold. -->
		<div class="flex shrink-0 items-center justify-end" :class="controlClass">
			<slot />
		</div>
	</div>
</template>

<script setup>
/**
 * One setting: its name and explanation on the left, its control on the right.
 * The control column keeps a fixed gutter so every row in a section lines up,
 * which is what makes a long list of settings scannable.
 */
defineProps({
	label: { type: String, required: true },
	hint: { type: String, default: "" },
	/** Width for the control column, e.g. "w-72". Left unset, it hugs the control. */
	controlClass: { type: String, default: "" },
});
</script>
