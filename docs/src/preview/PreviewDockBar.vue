<script setup>
import { Bot, FolderTree, History, LayoutGrid, MessageSquare, Terminal } from "@lucide/vue";

// Replica of the bottom dock bar (frontend/src/components/DockBar.vue):
// left-dock views + the Kali / ATT&CK launchers, right-dock views at the end.
</script>

<template>
	<footer class="pv-dockbar">
		<div class="pv-dockbar-group">
			<button class="pv-dockbar-btn" type="button" aria-label="File tree"><FolderTree /></button>
			<button class="pv-dockbar-btn" type="button" aria-label="Engagement views"><LayoutGrid /></button>
			<span class="pv-dockbar-sep" aria-hidden="true" />
			<button class="pv-dockbar-btn pv-kali-btn" type="button" aria-label="Kali Linux Tools">
				<span class="pv-kali">KALI</span>
			</button>
			<button class="pv-dockbar-btn pv-attack-btn" type="button" aria-label="MITRE ATT&amp;CK">
				<span class="pv-attack">ATT&amp;CK<sup>°</sup></span>
			</button>
		</div>

		<div class="pv-dockbar-group">
			<button class="pv-dockbar-btn" type="button" aria-label="Agents"><Bot /></button>
			<button class="pv-dockbar-btn" type="button" aria-label="Background processes"><Terminal /></button>
			<span class="pv-dockbar-sep" aria-hidden="true" />
			<button class="pv-dockbar-btn" type="button" aria-label="Chat"><MessageSquare /></button>
			<button class="pv-dockbar-btn" type="button" aria-label="Chat history"><History /></button>
		</div>
	</footer>
</template>

<style scoped>
.pv-dockbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	height: 2rem;
	flex-shrink: 0;
	padding: 0 0.75rem;
	border-top: 1px solid var(--p-border);
	background: var(--p-card);
}

.pv-dockbar-group {
	display: flex;
	align-items: center;
	gap: 0.125rem;
}

.pv-dockbar-btn {
	display: grid;
	place-items: center;
	border: none;
	background: none;
	padding: 0.25rem;
	border-radius: 0.25rem;
	color: var(--p-muted-foreground);
	cursor: default;
}

.pv-dockbar-btn:hover {
	color: var(--p-foreground);
}

.pv-dockbar-btn svg {
	width: 0.875rem;
	height: 0.875rem;
}

.pv-dockbar-sep {
	margin-inline: 0.25rem;
	height: 1rem;
	width: 1px;
	flex-shrink: 0;
	background: var(--p-border);
}

.pv-kali {
	border: 1px solid color-mix(in oklab, var(--p-border) 90%, transparent);
	border-radius: 0.25rem;
	padding: 0.05rem 0.3rem;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 8px;
	font-weight: 700;
	letter-spacing: 0.08em;
}

.pv-attack {
	font-size: 0.7rem;
	font-weight: 700;
	letter-spacing: 0.02em;
}

.pv-attack sup {
	font-size: 6px;
	font-weight: 400;
}
</style>
