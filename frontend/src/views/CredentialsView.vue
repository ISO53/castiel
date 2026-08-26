<template>
	<DocumentShell doc-id="credentials" v-slot="{ data }">
		<DataTable
			:columns="columns"
			:data="data?.credentials ?? []"
			search-placeholder="Filter credentials…"
			empty-message="No credentials captured yet."
		>
			<template #cell-secret="{ row }">
				<span class="inline-flex items-center gap-1.5">
					<code class="font-mono text-[11px]">{{ revealed.has(secretKey(row)) ? secretOf(row) : "••••••••" }}</code>
					<button
						type="button"
						class="text-muted-foreground hover:text-foreground"
						:aria-label="revealed.has(secretKey(row)) ? 'Hide secret' : 'Reveal secret'"
						@click="toggleReveal(row)"
					>
						<EyeOff v-if="revealed.has(secretKey(row))" class="size-3" />
						<Eye v-else class="size-3" />
					</button>
				</span>
			</template>
		</DataTable>
	</DocumentShell>
</template>

<script setup>
import { Eye, EyeOff } from "@lucide/vue";
import { reactive } from "vue";
import DataTable from "@/components/views/DataTable.vue";
import DocumentShell from "@/components/views/DocumentShell.vue";
import { firstOf } from "@/lib/documents";

const revealed = reactive(new Set());

/**
 * Credential entries are agent-authored; accept the natural field spellings
 * (username/principal, secret/password/hash/token/key, …).
 */
function secretOf(credential) {
	return String(firstOf(credential, ["secret", "password", "hash", "token", "key"], ""));
}

function secretKey(credential) {
	return `${firstOf(credential, ["id", "title", "username", "user", "principal"], "")}:${secretOf(credential)}`;
}

function toggleReveal(credential) {
	const key = secretKey(credential);
	if (revealed.has(key)) revealed.delete(key);
	else revealed.add(key);
}

const columns = [
	{
		id: "principal",
		header: "Principal",
		accessorFn: (row) => String(firstOf(row, ["username", "user", "principal", "account"], "?")),
	},
	{
		id: "secret",
		header: "Secret",
		enableSorting: false,
		accessorFn: (row) => secretOf(row),
	},
	{
		id: "type",
		header: "Type",
		accessorFn: (row) => String(firstOf(row, ["type", "kind"], "password")),
	},
	{
		id: "foundAt",
		header: "Found At",
		accessorFn: (row) => {
			const value = firstOf(row, ["found_at", "found_on", "location", "source"], "");
			return value ? String(value) : "—";
		},
	},
	{
		id: "validFor",
		header: "Valid For",
		accessorFn: (row) => {
			const value = firstOf(row, ["valid_for", "valid_on", "works_on"], []);
			return Array.isArray(value) ? value.join(", ") || "—" : value ? String(value) : "—";
		},
	},
];
</script>