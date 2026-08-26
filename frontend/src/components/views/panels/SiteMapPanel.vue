<template>
	<div class="flex h-full min-h-0 flex-col gap-2">
		<EmptyHint
			v-if="!hasEntities"
			:icon="Globe"
			message="No web pages discovered yet."
			hint="Sites and their pages will branch out here as content discovery fills web.json."
		/>
		<template v-else>
			<div class="min-h-0 flex-1 overflow-hidden rounded-md border">
				<CytoscapeCanvas :elements="elements" @select="selected = $event" />
			</div>

			<footer v-if="selection" class="shrink-0 rounded-md border p-2.5 text-xs">
				<p class="font-mono text-[11px] font-medium break-all text-foreground">{{ selection.label }}</p>
				<p v-if="selection.subtitle" class="mt-1 break-all text-muted-foreground">{{ selection.subtitle }}</p>
			</footer>
		</template>
	</div>
</template>

<script setup>
import { computed, ref } from "vue";
import { Globe } from "@lucide/vue";
import CytoscapeCanvas from "@/components/views/CytoscapeCanvas.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

const selected = ref(null);

const hasEntities = computed(() => {
	const d = props.data;
	return Boolean(d) && ((d.sites?.length ?? 0) > 0 || (d.pages?.length ?? 0) > 0);
});

function statusClass(status) {
	const code = Number(String(status ?? "").slice(0, 3));
	if (!Number.isFinite(code) || code === 0) return "";
	if (code < 300) return "up";
	if (code < 400) return "redirect";
	return "down"; // 4xx/5xx render muted
}

function pathOf(url) {
	try {
		const parsed = new URL(url);
		return parsed.pathname === "/" ? parsed.hostname : parsed.pathname;
	} catch {
		return url;
	}
}

/** Finds the site whose URL is the longest prefix of the page URL. */
function ownerSiteId(url, sites) {
	let best = null;
	let bestLength = -1;
	for (const site of sites) {
		const siteUrl = site?.url;
		if (siteUrl && url.startsWith(siteUrl) && siteUrl.length > bestLength) {
			best = `site:${siteUrl}`;
			bestLength = siteUrl.length;
		}
	}
	return best;
}

const elements = computed(() => {
	const d = props.data;
	if (!d) return [];
	const nodes = [];
	const edges = [];
	let edgeIndex = 0;

	for (const site of d.sites ?? []) {
		if (!site?.url) continue;
		nodes.push({
			group: "nodes",
			classes: "up",
			data: { id: `site:${site.url}`, label: site.url, subtitle: [site.server, site.title].filter(Boolean).join(" · ") },
		});
	}

	for (const page of d.pages ?? []) {
		if (!page?.url) continue;
		nodes.push({
			group: "nodes",
			classes: statusClass(page.status),
			data: {
				id: `page:${page.url}`,
				label: pathOf(page.url),
				subtitle: [page.status ? `HTTP ${page.status}` : "", page.title].filter(Boolean).join(" · "),
				parent: undefined,
			},
		});
		const owner = ownerSiteId(page.url, d.sites ?? []);
		if (owner && owner !== `page:${page.url}`) {
			edges.push({ group: "edges", data: { id: `e${edgeIndex++}`, source: owner, target: `page:${page.url}` } });
		}
	}

	for (const relationship of d.relationships ?? []) {
		const known = new Set(nodes.map((node) => node.data.id));
		const endpointOf = (value) => {
			if (!value) return null;
			for (const prefix of ["page:", "site:", "host:", "domain:"]) {
				if (known.has(prefix + value)) return prefix + value;
			}
			return null;
		};
		const source = endpointOf(relationship.from);
		const target = endpointOf(relationship.to);
		if (source && target) {
			edges.push({ group: "edges", data: { id: `e${edgeIndex++}`, source, target, label: relationship.type } });
		}
	}

	return [...nodes, ...edges];
});

const selection = computed(() => {
	if (!selected.value) return null;
	const raw = selected.value.data ?? {};
	return { label: raw.label ?? raw.id ?? "", subtitle: raw.subtitle ?? null };
});
</script>