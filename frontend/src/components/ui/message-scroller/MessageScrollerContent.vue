<script setup>
import { onBeforeUnmount, onMounted, useTemplateRef } from "vue";
import { cn } from "@/lib/utils";
import { useMessageScrollerContext } from "./useMessageScroller";

const props = defineProps({
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  spacerClass: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});

const {
  handleContentChange,
  handleResize,
  setContentElement,
  setSpacerElement,
} = useMessageScrollerContext();

const contentRef = useTemplateRef("content");
const spacerRef = useTemplateRef("spacer");

let mutationObserver = null;
let resizeObserver = null;
let resizeFrame = 0;

onMounted(() => {
  const content = contentRef.value;
  if (!content) return;

  setContentElement(content);
  setSpacerElement(spacerRef.value ?? null);
  handleContentChange();

  if (typeof MutationObserver !== "undefined") {
    mutationObserver = new MutationObserver(() => handleContentChange());
    mutationObserver.observe(content, { childList: true });
  }

  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver(() => {
      window.cancelAnimationFrame(resizeFrame);
      resizeFrame = window.requestAnimationFrame(handleResize);
    });
    resizeObserver.observe(content);
  }
});

onBeforeUnmount(() => {
  window.cancelAnimationFrame(resizeFrame);
  mutationObserver?.disconnect();
  resizeObserver?.disconnect();
  mutationObserver = null;
  resizeObserver = null;
  setContentElement(null);
  setSpacerElement(null);
});
</script>

<template>
  <div
    ref="content"
    data-slot="message-scroller-content"
    role="log"
    aria-relevant="additions"
    :class="cn('flex h-max min-h-full flex-col gap-8', props.class)"
  >
    <slot />
    <div
      ref="spacer"
      aria-hidden="true"
      data-message-scroller-spacer=""
      hidden
      :class="props.spacerClass"
    />
  </div>
</template>
