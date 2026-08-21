<script setup>
import { Primitive } from "reka-ui";
import { cn } from "@/lib/utils";

const props = defineProps({
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  align: { type: String, required: false, default: "start" },
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false, default: "div" },
});
</script>

<template>
  <Primitive
    data-slot="message"
    :data-align="align"
    :as="as"
    :as-child="asChild"
    :class="
      cn(
        'text-xs/relaxed gap-1.5 group/message relative flex w-full min-w-0 data-[align=end]:flex-row-reverse',
        props.class,
      )
    "
  >
    <slot />
  </Primitive>
</template>
