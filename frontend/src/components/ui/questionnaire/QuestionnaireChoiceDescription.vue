<script setup>
import { cn } from "@/lib/utils";

const props = defineProps({
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});
</script>

<template>
  <span
    data-slot="questionnaire-choice-description"
    :class="cn('text-muted-foreground', props.class)"
  >
    <slot />
  </span>
</template>
