<template>
	<div class="flex h-full min-h-0 flex-col gap-2">
		<EmptyHint
			v-if="!hasEntities"
			:icon="Network"
			message="No hosts, segments or domains discovered yet."
			hint="As recon fills network.json, the topology will grow here. Click any node for its full details."
		/>
		<template v-else>
			<div class="min-h-0 flex-1 overflow-hidden">
				<CytoscapeCanvas :elements="elements" @select="selected = $event" />
			</div>

			<footer v-if="detail" class="max-h-52 shrink-0 overflow-y-auto rounded-md border p-2.5 text-xs">
				<template v-if="detail.kind === 'host'">
					<div class="flex items-baseline justify-between gap-2">
						<p class="font-mono text-[11px] font-medium text-foreground">
							{{ detail.ref.hostnames?.[0] ?? detail.ref.ip }}
						</p>
						<p class="shrink-0 text-[10px] text-muted-foreground">{{ hostMetaLine }}</p>
					</div>
					<p v-if="(detail.ref.hostnames?.length ?? 0) > 1" class="mt-0.5 text-[10px] text-muted-foreground">
						Also known as: {{ detail.ref.hostnames.slice(1).join(", ") }}
					</p>
					<p v-if="detail.ref.summary" class="mt-1.5 leading-relaxed text-foreground/90">
						{{ detail.ref.summary }}
					</p>
					<p v-if="detail.ref.notes" class="mt-1 italic text-amber-500/90">Note: {{ detail.ref.notes }}</p>
					<table v-if="detail.ref.services?.length" class="mt-2 w-full border-collapse text-[11px]">
						<thead>
							<tr class="text-left text-muted-foreground">
								<th class="py-0.5 pr-3 font-medium">Port</th>
								<th class="py-0.5 pr-3 font-medium">State</th>
								<th class="py-0.5 pr-3 font-medium">Service</th>
								<th class="py-0.5 pr-3 font-medium">Product / Version</th>
							</tr>
						</thead>
						<tbody>
							<tr v-for="(service, index) in detail.ref.services" :key="index" class="border-t border-border/50">
								<td class="py-0.5 pr-3 font-mono">{{ service.port }}/{{ service.protocol ?? "tcp" }}</td>
								<td class="py-0.5 pr-3">{{ service.state ?? "open" }}</td>
								<td class="py-0.5 pr-3">{{ service.service ?? "?" }}</td>
								<td class="py-0.5 pr-3 text-muted-foreground">
									{{ [service.product, service.version].filter(Boolean).join(" ") || "—" }}
								</td>
							</tr>
						</tbody>
					</table>
					<p v-if="tlsSans.length" class="mt-1.5 font-mono text-[10px] text-muted-foreground">
						TLS SANs: {{ tlsSans.join(", ") }}
					</p>
					<p
						v-if="detail.ref.discovered_by || detail.ref.last_seen"
						class="mt-1.5 text-[10px] text-muted-foreground"
					>
						{{
							[detail.ref.discovered_by, detail.ref.last_seen ? `last seen ${detail.ref.last_seen}` : null]
								.filter(Boolean)
								.join(" · ")
						}}
					</p>
				</template>

				<template v-else-if="detail.kind === 'domain'">
					<div class="flex items-baseline justify-between gap-2">
						<p class="font-mono text-[11px] font-medium text-foreground">{{ detail.ref.domain }}</p>
						<p v-if="detail.ref.parent" class="shrink-0 text-[10px] text-muted-foreground">
							subdomain of {{ detail.ref.parent }}
						</p>
					</div>
					<p v-if="detail.ref.summary" class="mt-1.5 leading-relaxed text-foreground/90">
						{{ detail.ref.summary }}
					</p>
					<table v-if="records.length" class="mt-2 w-full border-collapse text-[11px]">
						<thead>
							<tr class="text-left text-muted-foreground">
								<th class="py-0.5 pr-3 font-medium">Type</th>
								<th class="py-0.5 pr-3 font-medium">Value</th>
							</tr>
						</thead>
						<tbody>
							<tr v-for="(record, index) in records" :key="index" class="border-t border-border/50">
								<td class="py-0.5 pr-3 font-mono">{{ record.type }}</td>
								<td class="py-0.5 pr-3 font-mono text-muted-foreground">{{ record.value }}</td>
							</tr>
						</tbody>
					</table>
					<p v-if="detail.ref.discovered_by" class="mt-1.5 text-[10px] text-muted-foreground">
						{{ detail.ref.discovered_by }}
					</p>
				</template>

				<template v-else-if="detail.kind === 'segment'">
					<p class="font-mono text-[11px] font-medium text-foreground">{{ detail.ref.cidr }}</p>
					<p class="mt-0.5 text-[10px] text-muted-foreground">
						{{ [detail.ref.label, detail.ref.role, `${hostCount} host(s) inside`].filter(Boolean).join(" · ") }}
					</p>
					<p v-if="detail.ref.discovered_by" class="mt-1.5 text-[10px] text-muted-foreground">
						{{ detail.ref.discovered_by }}
					</p>
				</template>

				<template v-else-if="detail.kind === 'relationship'">
					<p class="font-mono text-[11px] font-medium text-foreground">
						{{ detail.ref.from }} → {{ detail.ref.to }}
					</p>
					<p class="mt-0.5 text-muted-foreground">{{ detail.ref.type }}</p>
					<p v-if="detail.ref.detail" class="mt-1 text-muted-foreground">{{ detail.ref.detail }}</p>
				</template>
			</footer>
		</template>
	</div>
</template>

<script setup>
import { computed, ref } from "vue";
import { Network } from "@lucide/vue";
import CytoscapeCanvas from "@/components/views/CytoscapeCanvas.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });
const selected = ref(null);

const hasEntities = computed(() => {
	const d = props.data;
	return Boolean(d) && ((d.hosts?.length ?? 0) > 0 || (d.domains?.length ?? 0) > 0 || (d.segments?.length ?? 0) > 0);
});

/** True when an IPv4 address falls inside an IPv4 CIDR range. */
function inCidr(ip, cidr) {
	if (!ip || ip.includes(":") || !cidr?.includes("/")) return false;
	const [range, bitsRaw] = cidr.split("/");
	const mask = Number.parseInt(bitsRaw, 10);
	if (Number.isNaN(mask) || mask < 0 || mask > 32) return false;
	const toInt = (text) =>
		text.split(".").reduce((acc, part) => (acc << 8) + (Number.parseInt(part, 10) & 255), 0) >>> 0;
	const netMask = mask === 0 ? 0 : (0xffffffff << (32 - mask)) >>> 0;
	return (toInt(ip) & netMask) === (toInt(range) & netMask);
}

/** Flattens DNS records given as arrays or type-keyed object maps into {type, value} rows. */
function normalizeRecords(records) {
	if (!records) return [];
	const flatten = (type, values) =>
		(Array.isArray(values) ? values : [values]).map((value) => ({
			type,
			value: typeof value === "string" ? value : String(value.exchange ?? value.value ?? JSON.stringify(value)),
		}));
	if (Array.isArray(records)) {
		return records.flatMap((record) =>
			typeof record === "string" ? flatten("REC", [record]) : flatten(record.type ?? "REC", [record.value ?? record]),
		);
	}
	return Object.entries(records).flatMap(([type, values]) => flatten(type, values));
}

const hostByIp = computed(() => {
	const map = new Map();
	for (const host of props.data?.hosts ?? []) if (host?.ip) map.set(host.ip, host);
	return map;
});

const domainByName = computed(() => {
	const map = new Map();
	for (const domain of props.data?.domains ?? []) if (domain?.domain) map.set(domain.domain, domain);
	return map;
});

/** Maps a raw relationship endpoint (an ip or a domain name) onto its node id when known. */
function idFor(value) {
	if (!value) return null;
	if (hostByIp.value.has(value)) return `host:${value}`;
	if (domainByName.value.has(value)) return `domain:${value}`;
	return null;
}

/** Builds the cytoscape graph from the document; edges are derived, not authored. */
const elements = computed(() => {
	const d = props.data;
	if (!d) return [];
	const nodes = [];
	const edges = [];
	let edgeIndex = 0;
	const pushEdge = (source, target, label, kind) => {
		if (!source || !target || source === target) return;
		edges.push({ group: "edges", data: { id: `e${edgeIndex++}`, source, target, label, kind } });
	};

	for (const segment of d.segments ?? []) {
		if (!segment?.cidr) continue;
		nodes.push({
			group: "nodes",
			classes: "segment",
			data: {
				id: `seg:${segment.cidr}`,
				label: segment.label ? `${segment.cidr} · ${segment.label}` : segment.cidr,
				kind: "segment",
				ref: segment,
			},
		});
	}

	for (const host of d.hosts ?? []) {
		if (!host?.ip) continue;
		const parent = (d.segments ?? []).find((segment) => inCidr(host.ip, segment.cidr));
		nodes.push({
			group: "nodes",
			classes: host.status === "up" ? "host up" : "host down",
			data: {
				id: `host:${host.ip}`,
				label: host.hostnames?.[0] ?? host.ip,
				kind: "host",
				ref: host,
				parent: parent ? `seg:${parent.cidr}` : undefined,
			},
		});
	}

	for (const domain of d.domains ?? []) {
		if (!domain?.domain) continue;
		const domainId = `domain:${domain.domain}`;
		nodes.push({
			group: "nodes",
			classes: domain.parent ? "domain sub" : "domain",
			data: { id: domainId, label: domain.domain, kind: "domain", ref: domain },
		});
		if (domain.parent && domainByName.value.has(domain.parent)) {
			pushEdge(domainId, `domain:${domain.parent}`, "part of", "dns");
		}
		for (const record of normalizeRecords(domain.records)) {
			if ((record.type === "A" || record.type === "AAAA") && hostByIp.value.has(record.value)) {
				pushEdge(domainId, `host:${record.value}`, record.type, "dns");
			}
		}
	}

	for (const relationship of d.relationships ?? []) {
		pushEdge(idFor(relationship.from), idFor(relationship.to), relationship.type, "rel");
	}

	return [...nodes, ...edges];
});

const detail = computed(() => {
	const raw = selected.value?.data;
	if (!raw?.kind) return null;
	return { kind: raw.kind, ref: raw.ref ?? {} };
});

const hostMetaLine = computed(() => {
	if (detail.value?.kind !== "host") return "";
	const ref = detail.value.ref;
	return [
		ref.status,
		ref.os?.name ? `${ref.os.name}${ref.os.confidence ? ` (${ref.os.confidence})` : ""}` : null,
		ref.role,
	].filter(Boolean).join(" · ");
});

const records = computed(() =>
	detail.value?.kind === "domain" ? normalizeRecords(detail.value.ref.records) : [],
);

const tlsSans = computed(() => {
	if (detail.value?.kind !== "host") return [];
	const sans = new Set();
	for (const service of detail.value.ref.services ?? []) {
		for (const name of service.cert?.san ?? []) sans.add(name);
	}
	return [...sans];
});

const hostCount = computed(() => {
	if (detail.value?.kind !== "segment") return 0;
	const cidr = detail.value.ref.cidr;
	return (props.data?.hosts ?? []).filter((host) => inCidr(host.ip, cidr)).length;
});
</script>
