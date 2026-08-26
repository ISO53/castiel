<template>
	<div class="flex h-full min-h-0 flex-col items-center justify-center gap-2 px-6 text-center">
		<component :is="icon" v-if="icon" class="size-6 shrink-0 text-muted-foreground/40" />
		<p class="max-w-xs text-xs leading-relaxed text-muted-foreground">{{ message }}</p>
		<p v-if="hint" class="max-w-xs text-[11px] leading-relaxed text-muted-foreground/60">{{ hint }}</p>
	</div>
</template>

<script>
/** Consistent empty state for panels whose document section is still a blank scaffold. */
export default {
	name: "EmptyHint",
	props: {
		message: { type: String, required: true },
		hint: { type: String, default: "" },
		icon: { type: [Object, Function], default: null },
	},
};
</script>