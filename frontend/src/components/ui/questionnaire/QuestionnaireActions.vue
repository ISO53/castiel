<script setup>
import { Primitive } from "reka-ui";
import { cn } from "@/lib/utils";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false, default: "div" },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});
</script>

<template>
  <Primitive
    data-slot="questionnaire-actions"
    :as="props.as"
    :as-child="props.asChild"
    :class="
      cn(
        'gap-1.5 sm:min-h-7 grid min-h-11 w-full grid-cols-[minmax(0,1fr)_auto_auto] items-center',
        props.class,
      )
    "
  >
    <slot />
  </Primitive>
</template>
