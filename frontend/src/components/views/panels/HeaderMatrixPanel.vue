<template>
	<EmptyHint
		v-if="sites.length === 0"
		:icon="Globe"
		message="No HTTP security headers collected yet."
		hint="Response headers captured during recon will build this matrix automatically."
	/>
	<div v-else class="h-full min-h-0 overflow-auto rounded-md border">
		<table class="w-full border-collapse text-xs">
			<thead class="sticky top-0 z-10 bg-muted">
				<tr>
					<th class="border-b px-2.5 py-1.5 text-left font-medium text-muted-foreground">Site</th>
					<th
						v-for="header in headerNames"
						:key="header"
						class="border-b px-2.5 py-1.5 text-center font-medium whitespace-nowrap text-muted-foreground"
					>
						{{ header }}
					</th>
				</tr>
			</thead>
			<tbody>
				<tr v-for="row in matrix" :key="row.site" class="hover:bg-muted/40">
					<td class="max-w-56 truncate border-b border-border/40 px-2.5 py-1.5 font-mono text-[11px]" :title="row.site">
						{{ row.site }}
					</td>
					<td
						v-for="header in headerNames"
						:key="header"
						class="border-b border-border/40 px-2.5 py-1.5 text-center"
						:title="row.values[header] ?? 'not present'"
					>
						<span v-if="row.values[header] !== undefined" class="font-semibold text-green-500">✓</span>
						<span v-else class="text-muted-foreground/50">✗</span>
					</td>
				</tr>
			</tbody>
		</table>
	</div>
</template>

<script setup>
import { computed } from "vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

const SECURITY_HEADERS = [
	"Strict-Transport-Security",
	"Content-Security-Policy",
	"X-Content-Type-Options",
	"X-Frame-Options",
	"Referrer-Policy",
	"Permissions-Policy",
];

const sites = computed(() => (props.data?.sites ?? []).filter((site) => site?.url));

/** Normalizes header entries that may be {name|key, value} pairs or plain strings. */
function headerEntries(site) {
	return (site.headers ?? [])
		.map((entry) => {
			if (typeof entry === "string") return { name: entry, value: "" };
			return { name: entry.name ?? entry.key ?? "", value: entry.value ?? "" };
		})
		.filter((entry) => entry.name);
}

const headerNames = computed(() => {
	const names = new Set(SECURITY_HEADERS);
	for (const site of sites.value) {
		for (const entry of headerEntries(site)) names.add(entry.name);
	}
	return [...names];
});

const matrix = computed(() =>
	sites.value.map((site) => ({
		site: site.url,
		values: Object.fromEntries(headerEntries(site).map((entry) => [entry.name, entry.value])),
	})),
);
</script>