<script setup>
import {
	Folder,
	Columns2,
	LayoutGrid,
	RotateCcw,
	PanelLeftClose,
	Network,
	Globe,
	Bug,
	Image,
	ListChecks,
	Plug,
	RefreshCw,
} from "@lucide/vue";
import { DOCUMENTS, MCP_SERVERS, WORKSPACE_NAME } from "./data.js";

const ICONS = { network: Network, web: Globe, vulnerabilities: Bug, evidence: Image, tasks: ListChecks };

defineProps({
	activeDocId: { type: String, default: "network" },
});

defineEmits(["select"]);
</script>

<template>
	<!-- Replica of the left workspace dock: document list + MCP servers footer. -->
	<aside class="pv-dock">
		<div class="pv-dock-header">
			<Folder class="pv-header-icon" />
			<span class="pv-ws-name">{{ WORKSPACE_NAME }}</span>
			<button class="pv-icon-btn" type="button" aria-label="Split view"><Columns2 /></button>
			<button class="pv-icon-btn" type="button" aria-label="Grid view"><LayoutGrid /></button>
			<button class="pv-icon-btn" type="button" aria-label="Refresh"><RotateCcw /></button>
			<button class="pv-icon-btn" type="button" aria-label="Collapse dock"><PanelLeftClose /></button>
		</div>

		<div class="pv-docs">
			<button
				v-for="doc in DOCUMENTS"
				:key="doc.id"
				type="button"
				class="pv-doc"
				:class="{ active: doc.id === activeDocId }"
				@click="$emit('select', doc.id)"
			>
				<component :is="ICONS[doc.id]" class="pv-doc-icon" />
				<span class="pv-doc-text">
					<span class="pv-doc-label">{{ doc.label }}</span>
					<span class="pv-doc-file">{{ doc.file }}</span>
				</span>
			</button>
		</div>

		<div class="pv-mcp">
			<div class="pv-mcp-header">
				<Plug class="pv-header-icon" />
				<span class="pv-mcp-title">MCP Servers</span>
				<span class="pv-mcp-badge">{{ MCP_SERVERS.length }}/{{ MCP_SERVERS.length }}</span>
				<button class="pv-icon-btn" type="button" aria-label="Reconnect MCP servers"><RefreshCw /></button>
			</div>
			<ul class="pv-mcp-list">
				<li v-for="server in MCP_SERVERS" :key="server.name" class="pv-mcp-row">
					<span class="pv-dot" />
					<span class="pv-mcp-name">{{ server.name }}</span>
					<span class="pv-mcp-count">{{ server.tools }}</span>
				</li>
			</ul>
		</div>
	</aside>
</template>

<style scoped>
.pv-dock {
	display: flex;
	flex-direction: column;
	min-height: 0;
	background: var(--p-background);
	border-right: 1px solid var(--p-border);
}

.pv-dock-header {
	display: flex;
	align-items: center;
	gap: 0.375rem;
	height: 2rem;
	padding: 0 0.625rem;
	border-bottom: 1px solid var(--p-border);
	flex-shrink: 0;
}

.pv-header-icon {
	width: 0.875rem;
	height: 0.875rem;
	color: var(--p-muted-foreground);
	flex-shrink: 0;
}

.pv-ws-name {
	flex: 1;
	min-width: 0;
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.pv-icon-btn {
	display: grid;
	place-items: center;
	padding: 0.2rem;
	border: none;
	border-radius: 0.25rem;
	background: none;
	color: var(--p-muted-foreground);
	cursor: default;
}

.pv-icon-btn svg {
	width: 0.875rem;
	height: 0.875rem;
}

.pv-icon-btn:hover {
	color: var(--p-foreground);
}

.pv-docs {
	display: flex;
	flex-direction: column;
	gap: 0.125rem;
	padding: 0.5rem;
}

.pv-doc {
	display: flex;
	align-items: center;
	gap: 0.625rem;
	width: 100%;
	padding: 0.4375rem 0.625rem;
	border: none;
	border-radius: 0.375rem;
	background: none;
	text-align: left;
	cursor: default;
}

.pv-doc:hover,
.pv-doc.active {
	background: var(--p-muted);
}

.pv-doc-icon {
	width: 1rem;
	height: 1rem;
	color: var(--p-muted-foreground);
	flex-shrink: 0;
}

.pv-doc-text {
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.pv-doc-label {
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.pv-doc-file {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 0.65rem;
	color: var(--p-muted-foreground);
}

.pv-mcp {
	margin-top: auto;
	border-top: 1px solid var(--p-border);
	flex-shrink: 0;
}

.pv-mcp-header {
	display: flex;
	align-items: center;
	gap: 0.375rem;
	height: 1.75rem;
	padding: 0 0.625rem;
	border-bottom: 1px solid var(--p-border);
}

.pv-mcp-title {
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--p-foreground);
}

.pv-mcp-badge {
	margin-left: auto;
	padding: 0.125rem 0.375rem;
	border-radius: 0.25rem;
	background: var(--p-muted);
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 10px;
	color: var(--p-muted-foreground);
}

.pv-mcp-list {
	list-style: none;
	margin: 0;
	padding: 0.5rem;
	display: flex;
	flex-direction: column;
	gap: 0.125rem;
}

.pv-mcp-row {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	padding: 0.375rem 0.5rem;
	border-radius: 0.375rem;
}

.pv-dot {
	width: 0.5rem;
	height: 0.5rem;
	flex-shrink: 0;
	border-radius: 999px;
	background: #10b981;
}

.pv-mcp-name {
	flex: 1;
	min-width: 0;
	font-size: 0.75rem;
	color: var(--p-foreground);
}

.pv-mcp-count {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 10px;
	color: var(--p-muted-foreground);
}
</style>
