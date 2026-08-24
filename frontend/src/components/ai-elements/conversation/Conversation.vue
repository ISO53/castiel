<script setup>
import { cn } from "@/lib/utils";
import { reactiveOmit } from "@vueuse/core";
import { StickToBottom } from "vue-stick-to-bottom";

const props = defineProps({
  ariaLabel: { type: String, required: false, default: "Conversation" },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  initial: { type: [Boolean, String, Object], required: false, default: true },
  resize: { type: [String, Object], required: false },
  damping: { type: Number, required: false, default: 0.7 },
  stiffness: { type: Number, required: false, default: 0.05 },
  mass: { type: Number, required: false, default: 1.25 },
  anchor: { type: String, required: false, default: "none" },
});
const delegatedProps = reactiveOmit(props, "class");
</script>

<template>
  <StickToBottom
    v-bind="delegatedProps"
    :class="cn('relative flex-1 overflow-y-hidden', props.class)"
    role="log"
  >
    <slot />
  </StickToBottom>
</template>
