<template>
	<div class="space-y-4">
		<div class="flex flex-wrap items-center gap-3">
			<Button size="sm" @click="openConfigFile">Open settings file</Button>
			<p v-if="config" class="min-w-0 truncate font-mono text-[11px] text-muted-foreground">
				{{ config.path }}
			</p>
		</div>
		<p v-if="error" class="wrap-break-word text-xs text-destructive">{{ error }}</p>
		<p v-if="config?.error" class="wrap-break-word text-xs text-destructive">
			Invalid mcp.json: {{ config.error }}
		</p>
	</div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { Button } from "@/components/ui/button";
import { useMcpStore } from "@/stores/mcp";
import { useSettingsUiStore } from "@/stores/settingsUi";
import { useTabsStore } from "@/stores/tabs";

const mcp = useMcpStore();
const settingsUi = useSettingsUiStore();
const config = ref(null);
const error = ref("");

onMounted(async () => {
	try {
		config.value = await mcp.fetchConfig();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
});

/** The editor is a tab, so the dialog steps aside before it opens. */
async function openConfigFile() {
	error.value = "";
	try {
		if (!config.value) config.value = await mcp.fetchConfig();
		settingsUi.close();
		useTabsStore().openTab({
			value: `file:${config.value.path}`,
			label: "mcp.json",
			component: "FileEditorView",
			path: config.value.path,
		});
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
}
</script>
