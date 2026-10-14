<template>
	<div class="flex items-center gap-3">
		<!-- Ring while a download percent exists; plain spinner for the staging phase. -->
		<svg v-if="percent !== null" viewBox="0 0 36 36" class="size-5 shrink-0 -rotate-90">
			<circle cx="18" cy="18" r="15.5" fill="none" stroke="var(--border)" stroke-width="4" />
			<circle
				cx="18"
				cy="18"
				r="15.5"
				fill="none"
				stroke="var(--primary)"
				stroke-width="4"
				stroke-linecap="round"
				:stroke-dasharray="CIRCUMFERENCE"
				:stroke-dashoffset="dashOffset"
			/>
		</svg>
		<Spinner v-else class="size-3 shrink-0" />
		<div class="min-w-0">
			<p class="truncate text-xs font-medium text-foreground">{{ label }}</p>
			<p v-if="percent !== null" class="text-[11px] text-muted-foreground">{{ percent }}%</p>
		</div>
	</div>
</template>

<script setup>
import { computed } from "vue";
import { Spinner } from "@/components/ui/spinner";
import { useUpdateStore } from "@/stores/update";

/**
 * Live install progress for the settings row: the phase label plus a ring
 * (downloading) or a spinner (staging). Reads the store directly, so it
 * follows the backend's phases over the SSE feed with no local state.
 */
const update = useUpdateStore();

// Ring geometry; 2 * pi * r for r = 15.5.
const CIRCUMFERENCE = 97.4;

const percent = computed(() => update.status?.percent ?? null);
const label = computed(() =>
	update.status?.phase === "staging" ? "Preparing the update..." : `Downloading castiel ${update.status?.latestVersion}...`,
);
const dashOffset = computed(() => CIRCUMFERENCE * (1 - (percent.value ?? 0) / 100));
</script>
