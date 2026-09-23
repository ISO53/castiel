<template>
	<Dialog :open="open" @update:open="$emit('update:open', $event)">
		<DialogContent class="sm:max-w-112">
			<DialogHeader class="items-center text-center">
				<pre
					class="max-h-56 overflow-hidden font-mono text-[6px] leading-[1.1] tracking-tighter text-primary/80 select-none dark:text-primary"
				>{{ CASTIEL_ASCII }}</pre>
				<div class="flex items-center gap-2">
					<DialogTitle class="text-2xl font-bold tracking-tight">castiel</DialogTitle>
					<span
						class="rounded-md border bg-muted px-2 py-0.5 font-mono text-[11px] font-semibold text-foreground select-none"
					>
						v{{ pkg.version }}
					</span>
				</div>
				<DialogDescription>AI powered penetration testing harness</DialogDescription>
			</DialogHeader>

			<div class="divide-y rounded-lg border bg-muted/20 text-xs">
				<div class="flex items-center justify-between px-3 py-2">
					<span class="text-muted-foreground">Version</span>
					<span class="font-mono font-medium text-foreground">v{{ pkg.version }}</span>
				</div>
				<div class="flex items-center justify-between px-3 py-2">
					<span class="text-muted-foreground">License</span>
					<span class="font-medium text-foreground">{{ pkg.license }}</span>
				</div>
				<div class="flex items-center justify-between px-3 py-2">
					<span class="text-muted-foreground">Author</span>
					<span class="font-medium text-foreground">{{ pkg.author?.name || "iso53" }}</span>
				</div>
			</div>

			<div class="flex items-center justify-center gap-2 pb-1">
				<a
					class="inline-flex h-8 items-center gap-1.5 rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground transition-colors hover:bg-primary/90"
					href="https://github.com/iso53/castiel"
					target="_blank"
					rel="noopener noreferrer"
				>
					<Star class="size-3.5" />
					<span>GitHub</span>
				</a>
				<a
					class="inline-flex h-8 items-center gap-1.5 rounded-md border px-3 text-xs font-medium text-foreground transition-colors hover:bg-muted"
					:href="pkg.homepage || 'https://iso53.github.io/castiel'"
					target="_blank"
					rel="noopener noreferrer"
				>
					<BookOpen class="size-3.5" />
					<span>Docs</span>
				</a>
				<a
					class="inline-flex h-8 items-center gap-1.5 rounded-md border px-3 text-xs font-medium text-foreground transition-colors hover:bg-muted"
					href="https://github.com/iso53/castiel/issues"
					target="_blank"
					rel="noopener noreferrer"
				>
					<CircleDot class="size-3.5" />
					<span>Issues</span>
				</a>
			</div>
		</DialogContent>
	</Dialog>
</template>

<script setup>
import { BookOpen, CircleDot, Star } from "@lucide/vue";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { CASTIEL_ASCII } from "@/lib/ascii-art";
import pkg from "../../package.json";

defineProps({
	open: { type: Boolean, required: true },
});

defineEmits(["update:open"]);
</script>
