<template>
	<div class="flex h-full min-h-0 flex-col gap-2">
		<EmptyHint
			v-if="!hasEntities"
			:icon="Network"
			message="No hosts, segments or domains discovered yet."
			hint="As recon fills network.json, hosts and their services will appear here as an interactive topology."
		/>
		<template v-else>
			<div class="min-h-0 flex-1 overflow-hidden rounded-md border">
				<CytoscapeCanvas :elements="elements" @select="selected = $event" />
			</div>

			<footer v-if="selection" class="max-h-40 shrink-0 overflow-y-auto rounded-md border p-2.5 text-xs">
				<p class="mb-1 font-mono text-[11px] font-medium text-foreground">{{ selection.label }}</p>
				<p v-if="selection.subtitle" class="text-muted-foreground">{{ selection.subtitle }}</p>
				<table v-if="selectionServices.length" class="mt-2 w-full border-collapse text-[11px]">
					<thead>
						<tr class="text-left text-muted-foreground">
							<th class="py-0.5 pr-3 font-medium">Port</th>
							<th class="py-0.5 pr-3 font-medium">Service</th>
							<th class="py-0.5 pr-3 font-medium">Version</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="(service, index) in selectionServices" :key="index" class="border-t border-border/50">
							<td class="py-0.5 pr-3 font-mono">{{ service.port }}/{{ service.protocol ?? "tcp" }}</td>
							<td class="py-0.5 pr-3">{{ service.service ?? "?" }}</td>
							<td class="py-0.5 pr-3 text-muted-foreground">{{ service.version ?? service.product ?? "—" }}</td>
						</tr>
					</tbody>
				</table>
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

/** Maps a relationship endpoint value onto a node id when we know that entity. */
function idFor(value) {
	if (!value) return null;
	const known = new Set(knownIds.value);
	for (const prefix of ["host:", "domain:", "site:", "page:"]) {
		if (known.has(prefix + value)) return prefix + value;
	}
	return null;
}

const knownIds = computed(() => {
	const ids = new Set();
	const d = props.data;
	for (const host of d?.hosts ?? []) if (host.ip) ids.add(`host:${host.ip}`);
	for (const domain of d?.domains ?? []) if (domain.domain) ids.add(`domain:${domain.domain}`);
	return ids;
});

/** Normalizes DNS records given either as arrays or type-keyed object maps. */
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

const elements = computed(() => {
	const d = props.data;
	if (!d) return [];
	const nodes = [];
	const edges = [];
	let edgeIndex = 0;

	const pushEdge = (source, target, label) => {
		if (!source || !target || source === target) return;
		edges.push({ group: "edges", data: { id: `e${edgeIndex++}`, source, target, label } });
	};

	for (const segment of d.segments ?? []) {
		if (!segment?.cidr) continue;
		nodes.push({
			group: "nodes",
			classes: "segment",
			data: { id: `seg:${segment.cidr}`, label: segment.label ? `${segment.cidr} · ${segment.label}` : segment.cidr },
		});
	}

	for (const host of d.hosts ?? []) {
		if (!host?.ip) continue;
		nodes.push({
			group: "nodes",
			classes: host.status === "down" ? "down" : "up",
			data: {
				id: `host:${host.ip}`,
				label: host.hostname ?? host.ip,
				subtitle: [host.ip, host.os ?? host.type, host.status].filter(Boolean).join(" · "),
				parent: host.segment ? `seg:${host.segment}` : undefined,
			},
		});
	}

	for (const domain of d.domains ?? []) {
		if (!domain?.domain) continue;
		const domainId = `domain:${domain.domain}`;
		nodes.push({
			group: "nodes",
			classes: "domain",
			data: {
				id: domainId,
				label: domain.domain,
				subtitle: normalizeRecords(domain.records)
					.map((record) => `${record.type} ${record.value}`)
					.join("\n") || undefined,
			},
		});
		for (const record of normalizeRecords(domain.records)) {
			pushEdge(domainId, idFor(record.value), record.type);
		}
	}

	for (const relationship of d.relationships ?? []) {
		pushEdge(idFor(relationship.from), idFor(relationship.to), relationship.type);
	}

	return [...nodes, ...edges];
});

const selection = computed(() => {
	if (!selected.value) return null;
	const raw = selected.value.data ?? {};
	return {
		label: raw.label ?? raw.id ?? "",
		subtitle: raw.subtitle ?? null,
		isHost: (selected.value.classes ?? "").includes("host"),
	};
});

const selectionServices = computed(() => {
	if (!selection.value?.isHost) return [];
	const ip = selected.value.data.id.slice("host:".length);
	return props.data?.hosts?.find((host) => host.ip === ip)?.services ?? [];
});
</script>