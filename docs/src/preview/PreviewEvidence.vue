<script setup>
import { computed, ref } from "vue";
import { ArrowUpDown, FileText, Image as ImageIcon, Key, ScrollText, Search, SquareTerminal } from "@lucide/vue";
import { EVIDENCE_ARTIFACTS } from "./data.js";

const KIND_ICONS = {
	screenshot: ImageIcon,
	scan: SquareTerminal,
	capture: SquareTerminal,
	poc: FileText,
	log: ScrollText,
	credential: Key,
	other: FileText,
};

const search = ref("");

const rows = computed(() => {
	const needle = search.value.trim().toLowerCase();
	if (!needle) return EVIDENCE_ARTIFACTS;
	return EVIDENCE_ARTIFACTS.filter((artifact) =>
		[artifact.name, artifact.title, artifact.target, artifact.vulnerability]
			.filter(Boolean)
			.join(" ")
			.toLowerCase()
			.includes(needle),
	);
});
</script>

<template>
	<div class="pv-evidence">
		<div class="pv-evidence-search">
			<Search class="pv-search-icon" />
			<input v-model="search" type="text" spellcheck="false" placeholder="Filter artifacts…" />
		</div>

		<div class="pv-table-wrap">
			<!-- Table is wider than the panel on purpose: the Secret column clips at
			     the edge exactly like the real view does on narrow panes. -->
			<table class="pv-table" style="width: 860px; min-width: 860px">
				<colgroup>
					<col style="width: 150px" />
					<col style="width: 74px" />
					<col style="width: 210px" />
					<col style="width: 170px" />
					<col style="width: 160px" />
					<col style="width: 96px" />
				</colgroup>
				<thead>
					<tr>
						<th>Name<ArrowUpDown class="pv-sort" /></th>
						<th>Kind<ArrowUpDown class="pv-sort" /></th>
						<th>Title<ArrowUpDown class="pv-sort" /></th>
						<th>Target<ArrowUpDown class="pv-sort" /></th>
						<th>Vulnerability<ArrowUpDown class="pv-sort" /></th>
						<th>Secret</th>
					</tr>
				</thead>
				<tbody>
					<tr v-for="artifact in rows" :key="artifact.name">
						<td>
							<span class="pv-name-cell">
								<component :is="KIND_ICONS[artifact.kind] ?? FileText" class="pv-kind-icon" />
								<span class="pv-mono">{{ artifact.name }}</span>
							</span>
						</td>
						<td><span class="pv-badge-kind">{{ artifact.kind || "other" }}</span></td>
						<td>{{ artifact.title || "—" }}</td>
						<td class="pv-mono">{{ artifact.target || "—" }}</td>
						<td>
							<span v-if="artifact.vulnerability" class="pv-vuln-name">{{ artifact.vulnerability }}</span>
							<span v-else class="pv-dim">—</span>
						</td>
						<td class="pv-dim">—</td>
					</tr>
					<tr v-if="rows.length === 0">
						<td colspan="6" class="pv-dim pv-none">No artifacts match the filter.</td>
					</tr>
				</tbody>
			</table>
		</div>
	</div>
</template>

<style scoped>
.pv-evidence {
	display: flex;
	flex-direction: column;
	height: 100%;
	min-height: 0;
	gap: 0.375rem;
}

.pv-evidence-search {
	position: relative;
	flex-shrink: 0;
}

.pv-search-icon {
	position: absolute;
	left: 0.5rem;
	top: 50%;
	width: 0.75rem;
	height: 0.75rem;
	transform: translateY(-50%);
	color: var(--p-muted-foreground);
}

.pv-evidence-search input {
	height: 1.75rem;
	width: 100%;
	border-bottom: 1px solid var(--p-border);
	background: var(--p-card);
	padding: 0 0.5rem 0 1.75rem;
	border-top: none;
	border-left: none;
	border-right: none;
	font-size: 0.75rem;
	color: var(--p-foreground);
	font-family: inherit;
}

.pv-evidence-search input::placeholder {
	color: var(--p-muted-foreground);
}

.pv-evidence-search input:focus {
	outline: none;
}

.pv-name-cell {
	display: flex;
	align-items: center;
	gap: 0.375rem;
	min-width: 0;
}

.pv-kind-icon {
	width: 0.75rem;
	height: 0.75rem;
	flex-shrink: 0;
	color: var(--p-muted-foreground);
}

.pv-none {
	padding: 1.5rem 0.625rem;
	text-align: center;
}
</style>
