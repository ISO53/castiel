<template>
	<div ref="container" class="h-full w-full min-h-0" />
</template>

<script setup>
import cytoscape from "cytoscape";
import fcose from "cytoscape-fcose";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

try {
	cytoscape.use(fcose);
} catch {
	// Duplicate cytoscape copy. runLayout() falls back to the built-in cose layout.
}

/**
 * Mounts a Cytoscape graph. Re-layouts happen only when the elements actually change
 * (the 15s document polling produces identical arrays most of the time).
 * Emits `select` with the tapped element's raw JSON, or null when the background is hit.
 */
const props = defineProps({
	elements: { type: Array, required: true },
	layout: {
		type: Object,
		default: () => ({ name: "fcose", animate: true, padding: 30, nodeSeparation: 120 }),
	},
});

const emit = defineEmits(["select"]);

const container = ref(null);
let instance = null;
let signature = "";

// Palette mirrors the app's zinc/shadcn tokens.
const STYLESHEET = [
	{
		selector: "core",
		style: { "active-bg-color": "#3f3f46" },
	},
	{
		selector: "node",
		style: {
			label: "data(label)",
			color: "#e4e4e7",
			"font-size": 10,
			"font-family": "inherit",
			"background-color": "#27272a",
			"border-width": 1,
			"border-color": "#52525b",
			shape: "round-rectangle",
			padding: "8px",
		},
	},
	{
		selector: "node.segment",
		style: {
			shape: "round-rectangle",
			"background-color": "rgba(63, 63, 70, 0.12)",
			"border-style": "dashed",
			"border-color": "#52525b",
			"border-opacity": 0.6,
			color: "#a1a1aa",
			"font-size": 9,
			"text-valign": "top",
			"text-halign": "center",
			"padding": "24px",
		},
	},
	{
		selector: "node.up",
		style: { "border-color": "#4ade80" },
	},
	{
		selector: "node.down",
		style: { opacity: 0.45, "border-color": "#71717a" },
	},
	{
		selector: "node.domain",
		style: { shape: "ellipse", "background-color": "#1e293b", "border-color": "#60a5fa" },
	},
	{
		selector: "node.org",
		style: { shape: "round-tag", "background-color": "#1c1917", "border-color": "#d97706" },
	},
	{
		selector: "node.person",
		style: { shape: "ellipse", "background-color": "#27272a", "border-color": "#38bdf8" },
	},
	{
		selector: "node.detail",
		style: { shape: "ellipse", "background-color": "#18181b", "border-color": "#52525b", "font-size": 9 },
	},
	{
		selector: "edge",
		style: {
			width: 1.5,
			"line-color": "#3f3f46",
			"target-arrow-color": "#3f3f46",
			"curve-style": "bezier",
			label: "data(label)",
			"font-size": 8,
			color: "#a1a1aa",
			"text-background-color": "#18181b",
			"text-background-opacity": 1,
			"text-background-padding": 2,
		},
	},
	{
		// DNS-derived edges (A/AAAA records, subdomains) in the domain blue.
		selector: "edge.dns",
		style: { "line-color": "#60a5fa", "target-arrow-color": "#60a5fa" },
	},
	{
		// Explicit cross-cutting relationships (cert reuse, access, trust) dashed amber.
		selector: "edge.rel",
		style: { "line-style": "dashed", "line-color": "#d97706", "target-arrow-color": "#d97706" },
	},
	{
		selector: "node:selected",
		style: { "border-color": "#e4e4e7", "border-width": 2 },
	},
	{
		selector: "edge:selected",
		style: { "line-color": "#e4e4e7", width: 2 },
	},
];

let containerObserver = null;
let didInitialLayout = false;

onMounted(() => {
	instance = cytoscape({
		container: container.value,
		elements: props.elements,
		style: STYLESHEET,
	});
	instance.on("tap", "node, edge", (event) => emit("select", event.target.json()));
	instance.on("tap", (event) => {
		if (event.target === instance) emit("select", null);
	});
	signature = JSON.stringify(props.elements);

	// The canvas may mount at 0x0 (e.g. while its pane is hidden); run the layout
	// once a real size exists, then just re-center on subsequent resizes.
	didInitialLayout = layoutIfVisible();
	containerObserver = new ResizeObserver((entries) => {
		if (!instance) return;
		instance.resize();
		const rect = entries[0]?.contentRect;
		if (!rect || (rect.width === 0 && rect.height === 0)) return;
		if (!didInitialLayout) {
			didInitialLayout = layoutIfVisible();
		} else {
			instance.center();
		}
	});
	containerObserver.observe(container.value);
});

/** Runs the configured layout, falling back to a built-in one if unavailable. */
function layoutIfVisible() {
	const rect = container.value?.getBoundingClientRect();
	if (!rect || rect.width === 0 || rect.height === 0) return false;
	try {
		instance.layout({ ...props.layout }).run();
	} catch {
		// Requested layout (e.g. fcose) failed to register. Degrade gracefully.
		instance.layout({ name: "cose", animate: true, padding: 30 }).run();
	}
	return true;
}

watch(
	() => props.elements,
	(next) => {
		if (!instance) return;
		const nextSignature = JSON.stringify(next);
		if (nextSignature === signature) return;
		signature = nextSignature;
		instance.batch(() => {
			instance.elements().remove();
			instance.add(next);
		});
		layoutIfVisible();
	},
);

onBeforeUnmount(() => {
	containerObserver?.disconnect();
	containerObserver = null;
	if (instance) {
		instance.destroy();
		instance = null;
	}
});
</script>
