<template>
	<div class="flex h-full min-h-0 flex-col gap-2">
		<EmptyHint
			v-if="!hasEntities"
			:icon="Users"
			message="No identity or OSINT data collected yet."
			hint="People, organizations and profiles discovered through OSINT will map out here."
		/>
		<CytoscapeCanvas v-else :elements="elements" @select="selected = $event" />
	</div>
</template>

<script setup>
import { computed, ref } from "vue";
import { Users } from "@lucide/vue";
import CytoscapeCanvas from "@/components/views/CytoscapeCanvas.vue";
import EmptyHint from "@/components/views/EmptyHint.vue";

const props = defineProps({ data: { type: Object, default: null } });

const selected = ref(null);

const hasEntities = computed(() => {
	const d = props.data;
	return (
		Boolean(d) &&
		((d.people?.length ?? 0) > 0 || (d.organizations?.length ?? 0) > 0 || (d.emails?.length ?? 0) > 0)
	);
});

function personName(person) {
	return person?.name ?? person?.full_name ?? person?.username ?? "?";
}

const elements = computed(() => {
	const d = props.data;
	if (!d) return [];
	const nodes = [];
	const edges = [];
	const known = new Set();
	let edgeIndex = 0;

	const addEdge = (source, target, label) => {
		if (!source || !target || source === target) return;
		edges.push({ group: "edges", data: { id: `e${edgeIndex++}`, source, target, label } });
	};

	for (const org of d.organizations ?? []) {
		const name = org?.name ?? org?.organization;
		if (!name) continue;
		const id = `org:${name}`;
		known.add(id);
		nodes.push({ group: "nodes", classes: "org", data: { id, label: name, subtitle: org.industry ?? org.description } });
	}

	for (const person of d.people ?? []) {
		const name = personName(person);
		const id = `person:${name}`;
		known.add(id);
		nodes.push({
			group: "nodes",
			classes: "person",
			data: { id, label: name, subtitle: [person.role ?? person.title, person.org].filter(Boolean).join(" · ") },
		});
		const orgName = typeof person.org === "string" ? person.org : person.org?.name;
		if (orgName && known.has(`org:${orgName}`)) addEdge(`person:${name}`, `org:${orgName}`, "member_of");
	}

	for (const email of d.emails ?? []) {
		const address = typeof email === "string" ? email : (email.address ?? email.email);
		if (!address) continue;
		const id = `email:${address}`;
		known.add(id);
		nodes.push({ group: "nodes", classes: "detail", data: { id, label: address } });
		const owner = typeof email === "object" ? email.owner ?? email.person : null;
		if (owner && known.has(`person:${owner}`)) addEdge(id, `person:${owner}`, "email");
		else if (!owner && address.includes("@")) {
			const domainPart = `domain:${address.split("@")[1]}`;
			if (known.has(domainPart)) addEdge(id, domainPart, "address_at");
		}
	}

	for (const profile of d.social_profiles ?? []) {
		const handle = profile?.handle ?? profile?.url ?? profile?.profile;
		if (!handle) continue;
		const id = `profile:${handle}`;
		known.add(id);
		nodes.push({
			group: "nodes",
			classes: "detail",
			data: { id, label: handle, subtitle: [profile.platform ?? profile.site].filter(Boolean).join(" · ") },
		});
		const owner = profile.person ?? profile.owner;
		if (owner && known.has(`person:${personName(typeof owner === "string" ? { name: owner } : owner)}`)) {
			addEdge(id, `person:${personName(typeof owner === "string" ? { name: owner } : owner)}`, "profile");
		}
	}

	for (const leak of d.leaks ?? []) {
		const name = leak?.source ?? leak?.breach ?? leak?.name;
		if (!name) continue;
		const id = `leak:${name}`;
		known.add(id);
		nodes.push({ group: "nodes", classes: "detail", data: { id, label: `⚠ ${name}` } });
		for (const entry of leak.entries ?? leak.people ?? []) {
			const personId = `person:${personName(typeof entry === "string" ? { name: entry } : entry)}`;
			if (known.has(personId)) addEdge(personId, id, "exposed_in");
		}
	}

	for (const relationship of d.relationships ?? []) {
		const endpointOf = (value) => {
			if (!value) return null;
			for (const prefix of ["person:", "org:", "email:", "profile:", "leak:", "host:", "domain:"]) {
				if (known.has(prefix + value)) return prefix + value;
			}
			return null;
		};
		addEdge(endpointOf(relationship.from), endpointOf(relationship.to), relationship.type);
	}

	return [...nodes, ...edges];
});

const selectionSubtitle = computed(() => selected.value?.data?.subtitle ?? null);
</script>