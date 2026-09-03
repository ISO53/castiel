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
		</header>

		<div class="min-h-0 flex-1 overflow-y-auto p-2">
			<ul v-if="mcp.servers.length" class="space-y-0.5">
				<li
					v-for="server in mcp.servers"
					:key="server.id"
					class="flex items-center gap-2 rounded-md px-2 py-1.5"
					:title="`${server.target} — ${server.tools.length} tool(s)`"
				>
					<span
						class="size-2 shrink-0 rounded-full"
						:class="server.connected ? 'bg-emerald-500' : 'bg-destructive'"
						aria-hidden="true"
					/>
					<span class="min-w-0 flex-1 truncate text-xs text-foreground">{{ server.name }}</span>
					<span class="shrink-0 font-mono text-[10px] text-muted-foreground">{{ server.tools.length }}</span>
				</li>
			</ul>
			<EmptyState v-else text="No MCP servers registered. Add them in Settings.">
				<Plug />
			</EmptyState>
		</div>
	</section>
</template>

<script>
import { Plug } from "@lucide/vue";
import EmptyState from "@/components/EmptyState.vue";
import { useMcpStore } from "@/stores/mcp";

// Read-only display of registered MCP servers; changes happen in the settings view.
export default {
	name: "McpPanel",
	components: { EmptyState, Plug },
	data() {
		return { mcp: useMcpStore() };
	},
	mounted() {
		this.mcp.startPolling();
	},
	beforeUnmount() {
		this.mcp.stopPolling();
	},
};
</script>
