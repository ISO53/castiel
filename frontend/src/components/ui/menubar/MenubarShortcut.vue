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
    data-slot="menubar-shortcut"
    :class="
      cn(
        'text-muted-foreground group-focus/menubar-item:text-accent-foreground text-[0.625rem] tracking-widest ml-auto',
        props.class,
      )
    "
  >
    <slot />
  </span>
</template>
