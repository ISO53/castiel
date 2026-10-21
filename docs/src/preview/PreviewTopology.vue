<script setup>
// Static replica of the cytoscape topology the Network · Topology tab renders
// for the mock engagement: two dashed segments with green hosts, blue domain
// circles and labelled DNS/relationship edges.
</script>

<template>
	<div class="pv-topo">
		<svg viewBox="0 0 900 560" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Network topology">
			<!-- Application tier segment -->
			<rect x="285" y="44" width="540" height="248" class="pv-seg-box" />
			<text x="290" y="34" class="pv-seg-label">10.20.0.0/24 · Application tier · legacy v1 origin and production database</text>

			<!-- Hosts (up) inside the segment -->
			<rect x="468" y="56" width="54" height="54" rx="10" class="pv-host" />
			<rect x="378" y="170" width="54" height="54" rx="10" class="pv-host" />
			<text x="405" y="162" class="pv-node-label">vendor.meridianfreight.com</text>
			<rect x="620" y="130" width="54" height="54" rx="10" class="pv-host" />
			<text x="647" y="122" class="pv-node-label">api.v1.meridianfreight.com</text>
			<rect x="620" y="248" width="54" height="54" rx="10" class="pv-host" />
			<text x="647" y="240" class="pv-node-label">www.meridianfreight.com</text>

			<!-- DNS A-record chips on the segment edge -->
			<g class="pv-a-chip" transform="translate(352,140)">
				<rect x="0" y="0" width="16" height="14" rx="3" />
				<text x="8" y="10">A</text>
			</g>
			<g class="pv-a-chip" transform="translate(368,296)">
				<rect x="0" y="0" width="16" height="14" rx="3" />
				<text x="8" y="10">A</text>
			</g>

			<!-- Database segment -->
			<text x="590" y="394" class="pv-seg-label">10.20.1.0/24 · Database tier · PostgreSQL 14.8</text>
			<rect x="590" y="402" width="200" height="120" class="pv-seg-box" />
			<text x="690" y="440" class="pv-node-label">meridian-freight-db</text>
			<rect x="662" y="452" width="56" height="56" rx="10" class="pv-host" />

			<!-- Domain circles -->
			<circle cx="300" cy="262" r="27" class="pv-domain" />
			<text x="300" y="226" class="pv-node-label">portal.meridianfreight.com</text>
			<circle cx="310" cy="352" r="27" class="pv-domain" />
			<text x="262" y="394" class="pv-node-label">api.meridianfreight.com</text>
			<circle cx="395" cy="408" r="27" class="pv-domain" />
			<text x="395" y="370" class="pv-node-label">developers.meridianfreight.com</text>
			<circle cx="480" cy="418" r="27" class="pv-domain" />
			<text x="480" y="390" class="pv-node-label">vendor.meridianfreight.com</text>
			<circle cx="565" cy="398" r="27" class="pv-domain" />
			<text x="565" y="364" class="pv-node-label">legacy.meridianfreight.com</text>
			<circle cx="530" cy="478" r="27" class="pv-domain" />
			<text x="530" y="519" class="pv-node-label">sso.meridianfreight.com</text>
			<circle cx="632" cy="482" r="27" class="pv-domain" />
			<text x="645" y="536" class="pv-node-label">www.meridianfreight.com</text>
			<circle cx="452" cy="490" r="27" class="pv-domain" />

			<!-- Edges -->
			<g class="pv-edges">
				<line x1="315" y1="288" x2="352" y2="292" />
				<line x1="330" y1="330" x2="368" y2="300" />
				<line x1="386" y1="384" x2="330" y2="372" />
				<line x1="456" y1="408" x2="420" y2="410" />
				<line x1="542" y1="390" x2="510" y2="404" />
				<line x1="540" y1="458" x2="556" y2="422" />
				<line x1="608" y1="470" x2="556" y2="486" />
				<line x1="470" y1="470" x2="508" y2="484" />
				<line x1="700" y1="292" x2="668" y2="400" />
				<line x1="780" y1="292" x2="722" y2="400" />
			</g>
			<text x="452" y="342" class="pv-edge-label">part of</text>
			<text x="560" y="344" class="pv-edge-label">part of</text>
			<text x="532" y="444" class="pv-edge-label">part of</text>
			<text x="578" y="484" class="pv-edge-label">part of</text>
			<text x="700" y="316" class="pv-edge-label">provides-access</text>
			<text x="784" y="330" class="pv-edge-label">provides-access</text>
			<text x="322" y="316" class="pv-edge-label">A</text>
			<text x="338" y="286" class="pv-edge-label">A</text>
		</svg>
	</div>
</template>

<style scoped>
.pv-topo {
	height: 100%;
	min-height: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 0.5rem;
	animation: pv-fade 0.6s ease;
}

.pv-topo svg {
	width: 100%;
	height: 100%;
}

.pv-seg-box {
	fill: rgba(148, 163, 184, 0.06);
	stroke: #5b6577;
	stroke-dasharray: 6 4;
	stroke-width: 1.5;
}

.pv-seg-label {
	fill: #9aa4b2;
	font-size: 11px;
	font-family: inherit;
}

.pv-host {
	fill: #0d1622;
	stroke: #22c55e;
	stroke-width: 1.5;
}

.pv-domain {
	fill: #16324f;
	stroke: #3d6f9e;
	stroke-width: 1.5;
}

.pv-node-label {
	fill: #d7dde6;
	font-size: 11px;
	text-anchor: middle;
	font-family: inherit;
}

.pv-a-chip rect {
	fill: #0d1622;
	stroke: #5b6577;
	stroke-width: 1;
}

.pv-a-chip text {
	fill: #9aa4b2;
	font-size: 9px;
	text-anchor: middle;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.pv-edges line {
	stroke: #46536b;
	stroke-width: 1;
}

.pv-edge-label {
	fill: #8a94a6;
	font-size: 10px;
	text-anchor: middle;
	font-family: inherit;
}
</style>
