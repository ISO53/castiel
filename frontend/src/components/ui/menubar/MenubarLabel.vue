<script setup>
import { reactiveOmit } from "@vueuse/core";
import { MenubarLabel } from "reka-ui";
import { cn } from "@/lib/utils";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  inset: { type: Boolean, required: false },
});
const delegatedProps = reactiveOmit(props, "class", "inset");
</script>

<template>
  <MenubarLabel
    data-slot="menubar-label"
    :data-inset="inset ? '' : undefined"
    v-bind="delegatedProps"
    :class="
      cn(
        'text-muted-foreground px-2 py-1.5 text-xs data-inset:pl-7.5',
        props.class,
      )
    "
  >
    <slot />
  </MenubarLabel>
</template>
