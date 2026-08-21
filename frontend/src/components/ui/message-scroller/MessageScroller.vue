<script setup>
import { cn } from "@/lib/utils";
import { useMessageScrollerContext } from "./useMessageScroller";

const props = defineProps({
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});

const { autoscrolling, scrollableAttr } = useMessageScrollerContext();
</script>

<template>
  <div
    data-slot="message-scroller"
    :data-scrollable="scrollableAttr"
    :data-autoscrolling="autoscrolling ? '' : undefined"
    :class="
      cn(
        'group/message-scroller relative flex size-full min-h-0 flex-col overflow-hidden',
        props.class,
      )
    "
  >
    <slot />
  </div>
</template>
