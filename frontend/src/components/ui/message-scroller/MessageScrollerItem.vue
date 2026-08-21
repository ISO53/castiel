<script setup>
import { onBeforeUnmount, onMounted, useTemplateRef, watch } from "vue";
import { cn } from "@/lib/utils";
import { useMessageScrollerRegister } from "./useMessageScroller";

const props = defineProps({
  messageId: { type: String, required: false },
  scrollAnchor: { type: Boolean, required: false, default: false },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});

const register = useMessageScrollerRegister();

const itemEl = useTemplateRef("item");

onMounted(() => {
  if (props.messageId && itemEl.value)
    register(props.messageId, itemEl.value, null);
});

watch(
  () => props.messageId,
  (messageId, previousMessageId) => {
    const element = itemEl.value;
    if (!element) return;
    if (previousMessageId) register(previousMessageId, null, element);
    if (messageId) register(messageId, element, null);
  },
);

onBeforeUnmount(() => {
  if (props.messageId && itemEl.value)
    register(props.messageId, null, itemEl.value);
});
</script>

<template>
  <div
    ref="item"
    data-slot="message-scroller-item"
    :data-message-id="messageId"
    :data-scroll-anchor="scrollAnchor ? 'true' : 'false'"
    :class="
      cn(
        'min-w-0 shrink-0 [contain-intrinsic-size:auto_10rem] [content-visibility:auto]',
        props.class,
      )
    "
  >
    <slot />
  </div>
</template>
