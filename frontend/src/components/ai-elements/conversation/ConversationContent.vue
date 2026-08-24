<script setup>
import { cn } from "@/lib/utils";
import { computed } from "vue";

const props = defineProps({
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});

const classes = computed(() => cn("flex flex-col gap-8 p-4", props.class));
</script>

<template>
  <div :class="classes">
    <slot />
  </div>
</template>
