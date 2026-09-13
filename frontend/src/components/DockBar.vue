<script setup>
import {
	FolderTree,
	History,
	LayoutGrid,
	MessageSquare,
	Terminal,
} from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { useDocksStore } from "@/stores/docks";

const docks = useDocksStore();

// One entry per selectable view, grouped by host dock. Adding a view
// (e.g. agents in the bottom dock) is a single registration here.
const DOCK_VIEWS = {
	left: [
		{ id: "files", label: "File tree", icon: FolderTree },
		{ id: "views", label: "Engagement views", icon: LayoutGrid },
	],
	right: [
		{ id: "chat", label: "Chat", icon: MessageSquare },
		{ id: "history", label: "Chat history", icon: History },
	],
	bottom: [
		{ id: "processes", label: "Background processes", icon: Terminal },
	],
};

// A view is highlighted while its dock is open and showing it.
function isViewActive(dock, entry) {
	return docks[dock] && docks[`${dock}View`] === entry.id;
}
</script>

<template>
	<footer class="flex h-8 shrink-0 items-center justify-between gap-2 border-t px-3">
		<!-- Left dock views -->
		<div class="flex items-center gap-0.5">
			<Button
				v-for="entry in DOCK_VIEWS.left"
				:key="`left:${entry.id}`"
				variant="ghost"
				size="icon-sm"
				:class="[isViewActive('left', entry) ? 'text-primary hover:text-primary' : 'text-muted-foreground', 'active:translate-y-0!']"
				:aria-label="entry.label"
				:title="entry.label"
				@click="docks.toggleView('left', entry.id)"
			>
				<component :is="entry.icon" class="size-3.5" />
			</Button>
		</div>

		<!-- Bottom and right dock views, separated by a line -->
		<div class="flex items-center gap-0.5">
			<Button
				v-for="entry in DOCK_VIEWS.bottom"
				:key="`bottom:${entry.id}`"
				variant="ghost"
				size="icon-sm"
				:class="[isViewActive('bottom', entry) ? 'text-primary hover:text-primary' : 'text-muted-foreground', 'active:translate-y-0!']"
				:aria-label="entry.label"
				:title="entry.label"
				@click="docks.toggleView('bottom', entry.id)"
			>
				<component :is="entry.icon" class="size-3.5" />
			</Button>

			<div class="mx-1 h-4 w-px shrink-0 bg-border" aria-hidden="true" />

			<Button
				v-for="entry in DOCK_VIEWS.right"
				:key="`right:${entry.id}`"
				variant="ghost"
				size="icon-sm"
				:class="[isViewActive('right', entry) ? 'text-primary hover:text-primary' : 'text-muted-foreground', 'active:translate-y-0!']"
				:aria-label="entry.label"
				:title="entry.label"
				@click="docks.toggleView('right', entry.id)"
			>
				<component :is="entry.icon" class="size-3.5" />
			</Button>
		</div>
	</footer>
</template>