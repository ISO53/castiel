<template>
	<div class="space-y-4">
		<div class="space-y-2 text-xs leading-relaxed text-muted-foreground">
			<slot name="prose" />
		</div>

		<div class="flex flex-col gap-3">
			<slot />
		</div>

		<div class="flex items-center gap-3 pt-1">
			<Button size="sm" :disabled="connecting || disabled" @click="emit('connect')">
				{{ connecting ? "Connecting..." : "Connect" }}
			</Button>
			<div v-if="health" class="flex items-center gap-2 text-xs">
				<span
					class="size-2 rounded-full"
					:class="health.ok ? 'bg-emerald-500' : 'bg-destructive'"
					aria-hidden="true"
				/>
				<span :class="health.ok ? 'text-muted-foreground' : 'text-destructive'">{{ health.message }}</span>
			</div>
		</div>

		<!-- Cline renders its account sign-in block here. -->
		<slot name="extra" />

		<p v-if="error" class="wrap-break-word text-xs text-destructive">{{ error }}</p>
	</div>
</template>

<script setup>
import { Button } from "@/components/ui/button";

/**
 * The body shared by every LLM provider section: help prose, the fields
 * themselves, the Connect button and the resulting health check. The section
 * components only supply their own fields and wire up their store call.
 */
defineProps({
	connecting: { type: Boolean, default: false },
	disabled: { type: Boolean, default: false },
	health: { type: Object, default: null },
	error: { type: String, default: "" },
});

const emit = defineEmits(["connect"]);
</script>
