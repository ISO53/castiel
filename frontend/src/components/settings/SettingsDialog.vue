<template>
	<Dialog :open="ui.open" @update:open="(value) => (value ? (ui.open = true) : ui.close())">
		<!-- Remounting on open resets every section's form, so nothing leaks between visits. -->
		<DialogContent
			v-if="ui.open"
			class="flex h-[85vh] max-h-[85vh] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl"
			@open-auto-focus="(event) => event.preventDefault()"
			@escape-key-down="onEscapeKeyDown"
		>
			<div class="flex min-h-0 flex-1">
				<SettingsSidebar />

				<ScrollArea class="min-w-0 flex-1">
					<div class="mx-auto max-w-2xl space-y-5 p-6">
						<div class="space-y-1 pr-10">
							<DialogTitle class="flex items-center gap-2 text-base font-semibold tracking-tight text-foreground">
								<img v-if="logo" :src="logo" class="size-4 shrink-0" alt="" aria-hidden="true" />
								{{ item.title }}
							</DialogTitle>
							<DialogDescription class="text-xs text-muted-foreground">
								{{ item.description }}
							</DialogDescription>
						</div>

						<Separator />

						<p v-if="loadError" class="wrap-break-word text-xs text-destructive">{{ loadError }}</p>
						<div v-else-if="!settings.loaded" class="flex items-center gap-2 text-xs text-muted-foreground">
							<Spinner class="size-3" />
							<span>Loading settings...</span>
						</div>
						<component :is="section" v-else :key="item.id" />
					</div>
				</ScrollArea>
			</div>
		</DialogContent>
	</Dialog>
</template>

<script setup>
import { computed, defineAsyncComponent, ref, watch } from "vue";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogTitle,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import SettingsSidebar from "@/components/settings/SettingsSidebar.vue";
import { providerLogo } from "@/lib/provider-logos";
import { SETTINGS_ROWS, SETTINGS_ITEM_BY_ID } from "@/settings/registry";
import { useSettingsStore } from "@/stores/settings";
import { useSettingsUiStore } from "@/stores/settingsUi";

const ui = useSettingsUiStore();
const settings = useSettingsStore();
const loadError = ref("");

/** Escape backs out of the search first; only a second press closes the dialog. */
function onEscapeKeyDown(event) {
	if (ui.searching) {
		event.preventDefault();
		ui.query = "";
	}
}

const item = computed(() => SETTINGS_ITEM_BY_ID.get(ui.activeId) ?? SETTINGS_ROWS[0]);
const logo = computed(() => (item.value.logo ? providerLogo(item.value.logo) : null));

// One async wrapper per page, so switching back does not re-resolve the chunk.
const loaders = new Map();
const section = computed(() => {
	const entry = item.value;
	if (!loaders.has(entry.id)) loaders.set(entry.id, defineAsyncComponent(entry.component));
	return loaders.get(entry.id);
});

// Sections read the store on mount, so the document has to be in hand first.
watch(
	() => ui.open,
	async (open) => {
		if (!open || settings.loaded) return;
		loadError.value = "";
		try {
			await settings.fetch();
		} catch (err) {
			loadError.value = err instanceof Error ? err.message : String(err);
		}
	},
	{ immediate: true },
);
</script>
