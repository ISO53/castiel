<template>
	<section class="flex max-h-60 shrink-0 flex-col border-t bg-background">
		<header class="flex h-7 shrink-0 items-center gap-1.5 border-b px-3">
			<Plug class="size-3.5 shrink-0 text-muted-foreground" />
			<span class="text-xs font-medium text-foreground">MCP Servers</span>
			<span
				v-if="mcp.servers.length"
				class="ml-auto rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground"
			>
				{{ mcp.servers.filter((server) => server.connected).length }}/{{ mcp.servers.length }}
			</span>
			<Button
				variant="ghost"
				size="icon-sm"
				class="size-5 text-muted-foreground hover:text-foreground"
				aria-label="Reconnect disconnected MCP servers"
				title="Reconnect disconnected servers"
				:disabled="reconnecting || !mcp.servers.length"
				@click="reconnectDisconnected"
			>
				<RefreshCw :class="reconnecting ? 'animate-spin' : ''" />
			</Button>
		</header>

		<div class="min-h-0 flex-1 overflow-y-auto p-2">
			<ul v-if="mcp.servers.length" class="space-y-0.5">
				<li
					v-for="server in mcp.servers"
					:key="server.id"
					class="flex items-center gap-2 rounded-md px-2 py-1.5"
					:class="server.enabled ? '' : 'opacity-50'"
					:title="server.enabled ? `${server.target} - ${server.tools.length} tool(s)` : `${server.name} is disabled`"
				>
					<span
						class="size-2 shrink-0 rounded-full"
						:class="server.enabled ? (server.connected ? 'bg-emerald-500' : 'bg-destructive') : 'bg-muted-foreground/40'"
						aria-hidden="true"
					/>
					<span class="min-w-0 flex-1 truncate text-xs text-foreground">{{ server.name }}</span>
					<span class="shrink-0 font-mono text-[10px] text-muted-foreground">{{ server.tools.length }}</span>
					<Switch
						size="sm"
						:model-value="server.enabled"
						:aria-label="`${server.enabled ? 'Disable' : 'Enable'} MCP server ${server.name}`"
						@update:model-value="(value) => toggle(server, value)"
					/>
				</li>
			</ul>
			<EmptyState v-else text="No MCP servers registered. Add them in Settings.">
				<Plug />
			</EmptyState>
		</div>
	</section>
</template>

<script>
import { Plug, RefreshCw } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import EmptyState from "@/components/EmptyState.vue";
import { useMcpStore } from "@/stores/mcp";

// Live view of the MCP servers declared in mcp.json; the refresh button retries the down ones.
export default {
	name: "McpPanel",
	components: { Button, EmptyState, Plug, RefreshCw, Switch },
	data() {
		return { mcp: useMcpStore(), reconnecting: false };
	},
	mounted() {
		this.mcp.startFeed();
	},
	methods: {
		async reconnectDisconnected() {
			this.reconnecting = true;
			try {
				await this.mcp.reconnectDisconnected();
			} catch {
				// Statuses keep their last known values; the SSE feed refreshes on changes.
			} finally {
				this.reconnecting = false;
			}
		},
		async toggle(server, enabled) {
			try {
				await this.mcp.setEnabled(server.id, enabled);
			} catch {
				// Statuses keep their last known values; the SSE feed refreshes on changes.
			}
		},
	},
};
</script>
