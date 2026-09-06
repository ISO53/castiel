<template>
	<EmptyHint
		v-if="sections.length === 0"
		:icon="Boxes"
		message="No technologies fingerprinted yet."
		hint="The service stack will assemble here. Frontend on top, infrastructure below."
	/>
	<ScrollArea v-else class="h-full min-h-0"><div class="p-3">
		<h2 class="mb-3 text-sm font-bold uppercase tracking-widest text-foreground">Tech Stack</h2>

		<section v-for="section in sections" :key="section.name" class="mb-5">
			<div class="mb-2 border-b border-border/60 pb-1">
				<h3 class="text-xs font-semibold uppercase tracking-widest text-muted-foreground">{{ section.name }}</h3>
			</div>

			<div class="min-w-fit pt-2.5 pb-2.5">
				<div
					v-for="(tech, index) in section.techs"
					:key="tech.name"
					class="relative h-10"
				>
					<svg class="absolute left-14 -top-2.5 h-[60px] w-36 overflow-visible" viewBox="0 0 160 64">
						<polygon
							points="80,2 156,32 80,62 4,32"
							:fill="section.color"
							:fill-opacity="diamondOpacity(index)"
							:stroke="section.color"
							stroke-width="1"
						>
							<title>{{ [tech.desc, tech.evidence].filter(Boolean).join(" - ") || tech.name }}</title>
						</polygon>
					</svg>
					<div class="absolute left-52 top-1/2 -translate-y-1/2 w-72 sm:w-96 min-w-0">
						<p class="truncate text-xs font-semibold text-foreground" :title="tech.name">{{ tech.name }}</p>
						<p v-if="tech.desc" class="truncate text-[10px] leading-tight text-muted-foreground" :title="tech.desc">
							{{ tech.desc }}
						</p>
					</div>
				</div>
			</div>
		</section>
	</div>
	</ScrollArea>
</template>

<script setup>
import { computed } from "vue";
import { Boxes } from "@lucide/vue";
import EmptyHint from "@/components/views/EmptyHint.vue";
import { ScrollArea } from "@/components/ui/scroll-area";

const props = defineProps({ data: { type: Object, default: null } });

/** Ordered top → bottom; first keyword hit wins. Unknown techs land in Backend. */
const SECTION_DEFS = [
	{
		name: "Frontend",
		keywords: ["next", "vue", "react", "nuxt", "svelte", "angular", "tailwind", "css", "frontend", "spa", "ssr"],
		color: "#ef4444",
	},
	{
		name: "Backend",
		keywords: ["api", "express", "node", "rest", "backend", "server", "auth", "oauth", "application", "service"],
		color: "#a78bfa",
	},
	{
		name: "Edge / CDN / Proxy",
		keywords: ["cloudflare", "cdn", "proxy", "waf", "edge", "cache", "nginx", "apache", "load balancer"],
		color: "#f59e0b",
	},
	{
		name: "Network / DNS / Hosting",
		keywords: ["dns", "hostinger", "mx", "spf", "dmarc", "dkim", "registrar", "rdap", "mail", "hosting"],
		color: "#4ade80",
	},
];

/** Technology entries may be plain strings or { name|technology, ...meta } objects. */
function techName(entry) {
	if (typeof entry === "string") return entry;
	return entry?.name ?? entry?.technology ?? null;
}

const sections = computed(() => {
	const byName = new Map(); // name -> meta (evidence / category / version)
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

	const buckets = SECTION_DEFS.map((def) => ({ ...def, techs: [] }));
	for (const [name, meta] of byName) {
		const lowered = name.toLowerCase();
		const section = buckets.find(({ keywords }) => keywords.some((keyword) => lowered.includes(keyword)));
		const target = section ?? buckets[1];
		target.techs.push({
			name,
			desc: [meta.category, meta.version].filter(Boolean).join(" · "),
			evidence: meta.evidence ?? "",
		});
	}
	return buckets.filter((section) => section.techs.length > 0);
});

/** Overlapping diamonds get progressively more opaque, like the reference art. */
function diamondOpacity(index) {
	return Math.min(0.4 + index * 0.15, 0.95);
}
</script>
