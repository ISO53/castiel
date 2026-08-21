<script setup>
import { Primitive } from "reka-ui";
import { cn } from "@/lib/utils";

const props = defineProps({
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false, default: "div" },
});
</script>

<template>
  <Primitive
    data-slot="message-header"
    :as="as"
    :as-child="asChild"
    :class="
      cn(
        'text-[0.625rem] font-medium text-muted-foreground px-2.5 group-has-data-[variant=ghost]/message:px-0 flex max-w-full min-w-0 items-center',
        props.class,
      )
    "
  >
    <slot />
  </Primitive>
</template>
