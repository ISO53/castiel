<template>
	<EmptyHint
		v-if="layers.length === 0"
		:icon="Boxes"
		message="No technologies fingerprinted yet."
		hint="The service stack will assemble here — frontend on top, infrastructure below."
	/>
	<div v-else class="flex h-full min-h-0 flex-col items-center gap-0 overflow-y-auto py-2">
		<p class="mb-1 shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">Frontend ↑ · ↓ Infrastructure</p>
		<div
			v-for="(layer, index) in layers"
			:key="layer.name"
			class="w-full max-w-xl"
			:class="index > 0 ? '-mt-5' : ''"
		>
			<svg viewBox="0 0 440 110" class="w-full">
				<polygon
					:points="'220,6 430,55 220,104 10,55'"
					:fill="layer.fill"
					:stroke="layer.border"
					stroke-width="1.5"
					class="drop-shadow-md"
				>
					<title>{{ layer.techs.join(" · ") }}</title>
				</polygon>
				<text x="220" y="42" text-anchor="middle" class="fill-zinc-100" font-size="12" font-weight="600">
					{{ layer.name }}
				</text>
				<text x="220" y="62" text-anchor="middle" class="fill-zinc-400" font-size="10">
					{{ layer.summary }}
				</text>
				<text x="220" y="80" text-anchor="middle" class="fill-zinc-500" font-size="9">
					{{ layer.overflow }}
				</text>
			</svg>
		</div>
	</div>
</template>

<script setup>
import { computed } from "vue";
import { Boxes } from "@lucide/vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

/** Ordered top → bottom; first keyword hit wins. Unknown techs land in Application. */
const LAYER_DEFS = [
	{
		name: "Frontend & UI",
		keywords: ["next", "vue", "react", "nuxt", "svelte", "angular", "tailwind", "css", "frontend", "spa", "ssr"],
		fill: "#172554",
		border: "#38bdf8",
	},
	{
		name: "Application / API",
		keywords: ["api", "express", "node", "rest", "backend", "server", "auth", "oauth", "application", "service"],
		fill: "#2e1065",
		border: "#a78bfa",
	},
	{
		name: "Edge / CDN / Proxy",
		keywords: ["cloudflare", "cdn", "proxy", "waf", "edge", "cache", "nginx", "apache", "load balancer"],
		fill: "#451a03",
		border: "#f59e0b",
	},
	{
		name: "Network / DNS / Hosting",
		keywords: ["dns", "hostinger", "mx", "spf", "dmarc", "dkim", "registrar", "rdap", "mail", "hosting"],
		fill: "#052e16",
		border: "#4ade80",
	},
];

/** Technology entries may be plain strings or { name|technology, ...meta } objects. */
function techName(entry) {
	if (typeof entry === "string") return entry;
	return entry?.name ?? entry?.technology ?? null;
}

const layers = computed(() => {
	const byName = new Map(); // name -> meta (evidence/category/version)
	for (const entry of props.data?.technologies ?? []) {
		const name = techName(entry);
		if (name && !byName.has(name)) byName.set(name, typeof entry === "object" ? entry : {});
	}
	for (const site of props.data?.sites ?? []) {
		for (const entry of site.technologies ?? []) {
			const name = techName(entry);
			if (name && !byName.has(name)) byName.set(name, {});
		}
	}

	const buckets = LAYER_DEFS.map((def) => ({ ...def, techs: [] }));
	for (const [name, meta] of byName) {
		const lowered = name.toLowerCase();
		const layer = buckets.find(({ keywords }) => keywords.some((keyword) => lowered.includes(keyword)));
		const target = layer ?? buckets[1];
		target.techs.push(meta.evidence || meta.version ? `${name} (${meta.version ?? meta.category ?? ""})`.trim() : name);
	}
	return buckets
		.filter((layer) => layer.techs.length > 0)
		.map((layer) => ({
			...layer,
			summary: layer.techs.slice(0, 3).join(" · "),
			overflow: layer.techs.length > 3 ? `+${layer.techs.length - 3} more` : "",
		}));
});
</script>