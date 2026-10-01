<template>
	<div class="space-y-4">
		<div class="flex flex-wrap items-center gap-3">
			<Button size="sm" @click="openConfigFile">Open settings file</Button>
			<p v-if="config" class="min-w-0 truncate font-mono text-[11px] text-muted-foreground">
				{{ config.path }}
			</p>
		</div>

		<!-- Registered servers: state, transport and the tools each one offers. -->
		<div v-if="mcp.servers.length">
			<div class="mb-2 flex items-center gap-2 text-xs text-muted-foreground">
				<span>{{ mcp.servers.length }} {{ mcp.servers.length === 1 ? "server" : "servers" }}</span>
				<span aria-hidden="true" class="text-muted-foreground/50">/</span>
				<span>{{ connectedCount }} connected</span>
			</div>

			<ul class="divide-y overflow-hidden rounded-lg border border-border">
				<li
					v-for="server in mcp.servers"
					:key="server.id"
					class="flex flex-col gap-2 p-3"
					:class="server.enabled ? '' : 'opacity-60'"
				>
					<div class="flex items-center gap-2">
						<span
							class="size-2 shrink-0 rounded-full"
							:class="dotClass(server)"
							:title="statusLabel(server)"
							aria-hidden="true"
						/>
						<span class="min-w-0 truncate text-sm font-medium text-foreground">{{ server.name }}</span>
						<span class="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
							{{ server.type }}
						</span>
						<Switch
							class="ml-auto"
							size="sm"
							:aria-label="(server.enabled ? 'Disable' : 'Enable') + ' ' + server.name"
							:model-value="server.enabled"
							@update:model-value="(value) => toggle(server, value)"
						/>
					</div>

					<div v-if="server.tools.length" class="flex flex-wrap gap-1">
						<span
							v-for="tool in server.tools.slice(0, TOOL_LIMIT)"
							:key="tool"
							class="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground"
						>
							{{ tool }}
						</span>
						<span v-if="server.tools.length > TOOL_LIMIT" class="px-1 py-0.5 text-[10px] text-muted-foreground">
							+{{ server.tools.length - TOOL_LIMIT }} more
						</span>
					</div>
					<p v-else class="text-[11px] text-muted-foreground">No tools reported yet.</p>
				</li>
			</ul>
		</div>

		<EmptyState v-else text="No MCP servers registered. Add them in mcp.json.">
			<Plug />
		</EmptyState>

		<p v-if="error" class="wrap-break-word text-xs text-destructive">{{ error }}</p>
		<p v-if="config?.error" class="wrap-break-word text-xs text-destructive">
			Invalid mcp.json: {{ config.error }}
		</p>
	</div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { Plug } from "@lucide/vue";
import EmptyState from "@/components/EmptyState.vue";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { useMcpStore } from "@/stores/mcp";
import { useSettingsUiStore } from "@/stores/settingsUi";
import { useTabsStore } from "@/stores/tabs";

const mcp = useMcpStore();
const settingsUi = useSettingsUiStore();
const config = ref(null);
const error = ref("");

/** Cap the chips so a server with dozens of tools cannot flood the page. */
const TOOL_LIMIT = 12;

const connectedCount = computed(() => mcp.servers.filter((server) => server.connected).length);

onMounted(async () => {
	// The left dock already feeds the store; this only covers a cold open.
	if (!mcp.loaded) mcp.fetch().catch(() => {});
	try {
		config.value = await mcp.fetchConfig();
	} catch (err) {
		error.value = err instanceof Error ? err.message : String(err);
	}
});

function dotClass(server) {
	if (!server.enabled) return "bg-muted-foreground/40";
	return server.connected ? "bg-emerald-500" : "bg-destructive";
}

function statusLabel(server) {
	if (!server.enabled) return "Disabled";
	return server.connected ? "Connected" : "Unreachable";
}

async function toggle(server, enabled) {
	try {
		await mcp.setEnabled(server.id, enabled);
		settingsUi.flashSaved();
	} catch {
		// Statuses keep their last known values; the SSE feed refreshes on changes.
	}
}

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

