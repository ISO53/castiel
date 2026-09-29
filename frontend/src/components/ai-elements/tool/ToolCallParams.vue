<script setup>
// One wrapped line per tool argument, instead of a formatted JSON block.
import { computed } from "vue";

const props = defineProps({
	// Parsed tool arguments; a non-object renders as a single, keyless value.
	input: { type: [Object, Array, String, Number, Boolean, null], default: () => ({}) },
});

function stringify(value) {
	if (typeof value === "string") return value;
	if (value === null || value === undefined) return "";
	try {
		return JSON.stringify(value);
	} catch {
		// Circular or otherwise unserialisable; raw form beats throwing mid-render.
		return String(value);
	}
}

// Flattened to `[name, text]` pairs, one per line. Computed because the arguments arrive
// as a partial JSON string while the call streams, and grow as it completes.
const entries = computed(() => {
	if (props.input === null || props.input === undefined) return [];
	if (typeof props.input !== "object") return [[null, String(props.input)]];
	return Object.entries(props.input).map(([name, value]) => [name, stringify(value)]);
});
</script>

<template>
	<!-- A two-column grid so every value starts at the same offset, whatever the key happens to be. -->
	<dl
		v-if="entries.length"
		class="grid min-w-0 grid-cols-[minmax(0,max-content)_minmax(0,1fr)] items-baseline gap-x-2"
	>
		<template v-for="[name, text] in entries" :key="name ?? ''">
			<!-- An empty key is still rendered, so a keyless value keeps its column. -->
			<dt class="max-w-[18ch] truncate font-bold">
				<template v-if="name !== null">{{ name }}:</template>
			</dt>
			<!-- Long value never grows the row. The title carries the untruncated text -->
			<dd class="min-w-0 truncate" :title="text">{{ text }}</dd>
		</template>
	</dl>
</template>
