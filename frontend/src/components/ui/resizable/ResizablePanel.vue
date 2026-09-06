<script setup>
import { ref } from "vue";
import { SplitterPanel, useForwardPropsEmits } from "reka-ui";

const props = defineProps({
	collapsedSize: { type: Number, required: false },
	collapsible: { type: Boolean, required: false },
	defaultSize: { type: Number, required: false },
	id: { type: String, required: false },
	maxSize: { type: Number, required: false },
	minSize: { type: Number, required: false },
	order: { type: Number, required: false },
	sizeUnit: { type: String, required: false },
	asChild: { type: Boolean, required: false },
	as: { type: null, required: false },
});
const emits = defineEmits(["collapse", "expand", "resize"]);

const forwarded = useForwardPropsEmits(props, emits);

// Ref to the underlying reka-ui SplitterPanel. We re-expose its programmatic
// API (collapse/expand/isCollapsed/...) so callers can drive the panel via a
// template ref on this wrapper. e.g. <ResizablePanel ref="x"> then x.collapse().
// Getters keep isCollapsed/isExpanded as plain booleans (no ref-unwrap issues).
const panel = ref(null);

defineExpose({
	collapse: () => panel.value?.collapse(),
	expand: () => panel.value?.expand(),
	resize: (size) => panel.value?.resize(size),
	getSize: () => panel.value?.getSize(),
	get isCollapsed() {
		return !!panel.value?.isCollapsed;
	},
	get isExpanded() {
		return !!panel.value?.isExpanded;
	},
});
</script>

<template>
  <SplitterPanel
    ref="panel"
    v-slot="slotProps"
    data-slot="resizable-panel"
    v-bind="forwarded"
  >
    <slot v-bind="slotProps" />
  </SplitterPanel>
</template>
