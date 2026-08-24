<script setup>
import { cn } from "@/lib/utils";
import LoaderIcon from "./LoaderIcon.vue";

const props = defineProps({
  size: { type: Number, required: false, default: 16 },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});
</script>

<template>
  <div
    :class="
      cn('inline-flex animate-spin items-center justify-center', props.class)
    "
    aria-label="Loading"
    aria-live="polite"
    role="status"
    v-bind="$attrs"
  >
    <LoaderIcon :size="props.size" />
  </div>
</template>
