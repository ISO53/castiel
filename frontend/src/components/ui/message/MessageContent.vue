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
    data-slot="message-content"
    :as="as"
    :as-child="asChild"
    :class="
      cn(
        'gap-2 group-data-[align=end]/message:*:data-slot:self-end flex w-full min-w-0 flex-col wrap-break-word',
        props.class,
      )
    "
  >
    <slot />
  </Primitive>
</template>
