<script setup>
import { computed, ref } from "vue";
import { ArrowUpDown } from "@lucide/vue";
import { VULN_BANDS, VULNERABILITIES } from "./data.js";

const PROOF_OPTIONS = [
	{ id: "all", label: "All" },
	{ id: "potential", label: "Potential" },
	{ id: "proved", label: "Proved" },
];

const selectedBand = ref("all");
const hovered = ref(null);
const proof = ref("all");
const search = ref("");
const target = ref("all");

const targets = computed(() => [...new Set(VULNERABILITIES.map((row) => row.target))].sort());

const rows = computed(() => {
	const needle = search.value.trim().toLowerCase();
	return VULNERABILITIES.filter((row) => {
		if (proof.value !== "all" && row.proof !== proof.value) return false;
		if (selectedBand.value !== "all" && bandOf(row).id !== selectedBand.value) return false;
		if (target.value !== "all" && row.target !== target.value) return false;
		if (needle && !`${row.title} ${row.target}`.toLowerCase().includes(needle)) return false;
		return true;
	});
});

const total = computed(() => VULNERABILITIES.length);
const proved = computed(() => VULNERABILITIES.filter((row) => row.proof === "proved").length);
const potential = computed(() => total.value - proved.value);

const severityBreakdown = computed(() =>
	VULN_BANDS.map((band) => ({
		...band,
		count: VULNERABILITIES.filter((row) => bandOf(row).id === band.id).length,
	})),
);

function bandOf(row) {
	return (
		VULN_BANDS.find((band) => row.score >= band.min && (row.score < band.max || (band.id === "critical" && row.score <= band.max))) ??
		VULN_BANDS[VULN_BANDS.length - 1]
	);
}

function selectBand(id) {
	selectedBand.value = selectedBand.value === id ? "all" : id;
}

// Speedometer geometry — identical to VulnerabilityGaugePanel.vue.
const CX = 150;
const CY = 148;
const R_OUT = 140;
const R_IN = 108;

const trackPath = `M ${CX - R_OUT} ${CY} A ${R_OUT} ${R_OUT} 0 0 1 ${CX + R_OUT} ${CY} L ${CX + R_IN} ${CY} A ${R_IN} ${R_IN} 0 0 0 ${CX - R_IN} ${CY} Z`;

function describeArc(startAngleDeg, endAngleDeg) {
	const startRad = (startAngleDeg * Math.PI) / 180;
	const endRad = (endAngleDeg * Math.PI) / 180;
	const x1 = CX + R_OUT * Math.cos(startRad);
	const y1 = CY - R_OUT * Math.sin(startRad);
	const x2 = CX + R_OUT * Math.cos(endRad);
	const y2 = CY - R_OUT * Math.sin(endRad);
	const x3 = CX + R_IN * Math.cos(endRad);
	const y3 = CY - R_IN * Math.sin(endRad);
	const x4 = CX + R_IN * Math.cos(startRad);
	const y4 = CY - R_IN * Math.sin(startRad);
	const largeArc = Math.abs(startAngleDeg - endAngleDeg) > 180 ? 1 : 0;
	return `M ${x1.toFixed(2)} ${y1.toFixed(2)} A ${R_OUT} ${R_OUT} 0 ${largeArc} 1 ${x2.toFixed(2)} ${y2.toFixed(2)} L ${x3.toFixed(2)} ${y3.toFixed(2)} A ${R_IN} ${R_IN} 0 ${largeArc} 0 ${x4.toFixed(2)} ${y4.toFixed(2)} Z`;
}

// Info (left, 180 deg) -> Low -> Medium -> High -> Critical (right, 0 deg).
const slices = computed(() => {
	const counts = new Map(VULN_BANDS.map((band) => [band.id, severityBreakdown.value.find((b) => b.id === band.id).count]));
	const activeBands = [...VULN_BANDS].reverse().filter((band) => counts.get(band.id) > 0);
	if (total.value === 0 || activeBands.length === 0) return [];

	const gap = activeBands.length > 1 ? 1.5 : 0;
	const availableAngle = 180 - (activeBands.length - 1) * gap;
	let currentAngle = 180;

	return activeBands.map((band) => {
		const count = counts.get(band.id);
		const span = (count / total.value) * availableAngle;
		const start = currentAngle;
		const end = currentAngle - span;
		currentAngle = end - gap;
		return { ...band, count, path: describeArc(start, end) };
	});
});

function isHighlighted(bandId) {
	if (hovered.value) return hovered.value.id === bandId;
	if (selectedBand.value !== "all") return selectedBand.value === bandId;
	return true;
}

const hoveredItems = computed(() =>
	hovered.value ? VULNERABILITIES.filter((row) => bandOf(row).id === hovered.value.id) : [],
);

const centerColor = computed(() => {
	if (hovered.value) return hovered.value.color;
	if (selectedBand.value !== "all") {
		return VULN_BANDS.find((band) => band.id === selectedBand.value)?.color ?? "var(--p-foreground)";
	}
	return "var(--p-foreground)";
});

const centerCount = computed(() => {
	if (hovered.value) return hoveredItems.value.length;
	if (selectedBand.value !== "all") {
		return VULNERABILITIES.filter((row) => bandOf(row).id === selectedBand.value).length;
	}
	return total.value;
});

const centerSub = computed(() => {
	const items = hovered.value
		? hoveredItems.value
		: selectedBand.value !== "all"
			? VULNERABILITIES.filter((row) => bandOf(row).id === selectedBand.value)
			: VULNERABILITIES;
	const provedCount = items.filter((row) => row.proof === "proved").length;
	return `${provedCount} proved · ${items.length - provedCount} potential`;
});
</script>

<template>
	<div class="pv-vulns">
		<!-- Gauge panel -->
		<div class="pv-gauge-panel">
			<div class="pv-gauge-left">
				<svg viewBox="0 0 300 155" class="pv-gauge-svg">
					<path :d="trackPath" fill="rgba(255, 255, 255, 0.07)" />
					<path
						v-for="slice in slices"
						:key="slice.id"
						:d="slice.path"
						:fill="slice.color"
						:opacity="isHighlighted(slice.id) ? 1 : 0.25"
						class="pv-gauge-slice"
						@mouseenter="hovered = slice"
						@mouseleave="hovered = null"
						@click="selectBand(slice.id)"
					/>
				</svg>
				<div class="pv-gauge-center">
					<p class="pv-gauge-count" :style="{ color: centerColor }">{{ centerCount }}</p>
					<p class="pv-gauge-sub">{{ centerSub }}</p>
				</div>
			</div>

			<div class="pv-gauge-right">
				<template v-if="hovered">
					<div class="pv-gauge-head">
						<span class="pv-dot" :style="{ background: hovered.color }" />
						<p class="pv-gauge-label">{{ hovered.label }}</p>
					</div>
					<p class="pv-gauge-count" :style="{ color: hovered.color }">{{ hoveredItems.length }}</p>
					<p class="pv-gauge-subline">{{ hovered.count ? `CVSS ${hovered.min}–${hovered.max}` : "" }}</p>
				</template>
				<template v-else>
					<div class="pv-gauge-head">
						<p class="pv-gauge-label">All vulnerabilities</p>
						<button
							v-if="selectedBand !== 'all'"
							type="button"
							class="pv-clear-btn"
							@click="selectedBand = 'all'"
						>
							Clear filter
						</button>
					</div>
					<p class="pv-gauge-total">{{ total }}</p>
					<p class="pv-gauge-subline">{{ proved }} proved · {{ potential }} potential</p>
					<div class="pv-legend">
						<span
							v-for="band in severityBreakdown"
							:key="band.id"
							class="pv-legend-item"
							:class="{ dim: selectedBand !== 'all' && selectedBand !== band.id }"
							@click="selectBand(band.id)"
						>
							<span class="pv-dot" :style="{ backgroundColor: band.color }" />
							{{ band.count }} {{ band.label.toLowerCase() }}
						</span>
					</div>
				</template>
				<p class="pv-gauge-hint">Hover a band for its summary. Click to filter the table.</p>
			</div>
		</div>

		<!-- Filter toolbar + table -->
		<div class="pv-vulns-body">
			<div class="pv-toolbar">
				<input v-model="search" type="text" spellcheck="false" placeholder="Search title, target, summary…" class="pv-input pv-search" />
				<div class="pv-segmented">
					<button
						v-for="option in PROOF_OPTIONS"
						:key="option.id"
						type="button"
						:class="{ active: proof === option.id }"
						@click="proof = option.id"
					>
						{{ option.label }}
					</button>
				</div>
				<select v-model="target" class="pv-select">
					<option value="all">All targets</option>
					<option v-for="item in targets" :key="item" :value="item">{{ item }}</option>
				</select>
				<span class="pv-count">{{ rows.length }} of {{ VULNERABILITIES.length }}</span>
			</div>

			<div class="pv-table-wrap">
				<table class="pv-table" style="width: 920px; min-width: 920px">
					<colgroup>
						<col style="width: 90px" />
						<col style="width: 110px" />
						<col style="width: 330px" />
						<col style="width: 210px" />
						<col style="width: 60px" />
						<col style="width: 120px" />
					</colgroup>
					<thead>
						<tr>
							<th>Proof</th>
							<th>CVSS 4.0<ArrowUpDown class="pv-sort" /></th>
							<th>Title<ArrowUpDown class="pv-sort" /></th>
							<th>Target</th>
							<th>Ev.<ArrowUpDown class="pv-sort" /></th>
							<th>Found</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="row in rows" :key="row.title">
							<td><span class="pv-badge-proof">{{ row.proof || "potential" }}</span></td>
							<td>
								<span
									class="pv-score"
									:style="{ background: bandOf(row).color + '26', color: bandOf(row).color }"
								>
									{{ row.score.toFixed(1) }}
								</span>
							</td>
							<td>{{ row.title }}</td>
							<td class="pv-mono pv-dim">{{ row.target }}</td>
							<td class="pv-dim pv-ev">{{ row.evidence || "—" }}</td>
							<td class="pv-dim">—</td>
						</tr>
						<tr v-if="rows.length === 0">
							<td colspan="6" class="pv-dim pv-none">Nothing matches the current filters.</td>
						</tr>
					</tbody>
				</table>
			</div>
		</div>
	</div>
</template>

<style scoped>
.pv-vulns {
	display: flex;
	flex-direction: column;
	height: 100%;
	min-height: 0;
}

.pv-gauge-panel {
	display: flex;
	align-items: stretch;
	height: 11rem;
	flex-shrink: 0;
	border-bottom: 1px solid color-mix(in oklab, var(--p-border) 40%, transparent);
	user-select: none;
}

.pv-gauge-left {
	position: relative;
	display: flex;
	align-items: flex-end;
	height: 100%;
	width: min(20rem, 58%);
	min-width: 11rem;
	flex-shrink: 0;
}

.pv-gauge-svg {
	width: 100%;
	height: 100%;
}

.pv-gauge-slice {
	cursor: pointer;
}

.pv-gauge-center {
	pointer-events: none;
	position: absolute;
	inset-inline: 0;
	bottom: 1rem;
	display: flex;
	flex-direction: column;
	align-items: center;
}

.pv-gauge-count {
	font-size: 1.875rem;
	font-weight: 700;
	line-height: 1;
	letter-spacing: -0.02em;
	font-variant-numeric: tabular-nums;
	color: var(--p-foreground);
}

.pv-gauge-sub {
	margin-top: 0.25rem;
	font-size: 11px;
	line-height: 1.25;
	color: var(--p-muted-foreground);
}

.pv-gauge-right {
	display: flex;
	min-width: 0;
	flex: 1;
	flex-direction: column;
	justify-content: center;
	padding: 0 1rem;
	row-gap: 0.25rem;
}

.pv-gauge-head {
	display: flex;
	align-items: center;
	gap: 0.5rem;
}

.pv-gauge-label {
	font-size: 0.75rem;
	font-weight: 600;
	text-transform: uppercase;
	letter-spacing: 0.05em;
	color: var(--p-muted-foreground);
}

.pv-clear-btn {
	border: 1px solid color-mix(in oklab, var(--p-border) 80%, transparent);
	border-radius: 0.25rem;
	background: none;
	padding: 0.125rem 0.375rem;
	font-size: 10px;
	color: var(--p-muted-foreground);
	cursor: default;
}

.pv-gauge-total {
	font-size: 1.875rem;
	font-weight: 700;
	line-height: 1.1;
	font-variant-numeric: tabular-nums;
	color: var(--p-foreground);
}

.pv-gauge-subline {
	font-size: 0.75rem;
	color: var(--p-muted-foreground);
}

.pv-legend {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	column-gap: 0.375rem;
	row-gap: 0.125rem;
	font-size: 11px;
	color: var(--p-muted-foreground);
}

.pv-legend-item {
	display: inline-flex;
	align-items: center;
	gap: 0.25rem;
	cursor: pointer;
}

.pv-legend-item.dim {
	opacity: 0.4;
}

.pv-gauge-hint {
	padding-top: 0.125rem;
	font-size: 11px;
	color: color-mix(in oklab, var(--p-muted-foreground) 70%, transparent);
}

.pv-dot {
	display: inline-block;
	width: 0.375rem;
	height: 0.375rem;
	border-radius: 999px;
	flex-shrink: 0;
}

.pv-vulns-body {
	display: flex;
	min-height: 0;
	flex: 1;
	flex-direction: column;
}

.pv-search {
	width: 14rem;
}

.pv-score {
	display: inline-block;
	border-radius: 0.25rem;
	padding: 0.125rem 0.375rem;
	font-weight: 600;
	font-variant-numeric: tabular-nums;
}

.pv-ev {
	text-align: right;
	font-variant-numeric: tabular-nums;
}

.pv-none {
	padding: 1.5rem 0.625rem;
	text-align: center;
}
</style>


