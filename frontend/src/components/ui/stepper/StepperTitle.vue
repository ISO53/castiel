<script setup>
import { reactiveOmit } from "@vueuse/core";
import { StepperTitle, useForwardProps } from "reka-ui";
import { cn } from "@/lib/utils";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});

const delegatedProps = reactiveOmit(props, "class");

const forwarded = useForwardProps(delegatedProps);
</script>

<template>
  <StepperTitle
    v-bind="forwarded"
    :class="cn('text-md font-semibold whitespace-nowrap', props.class)"
  >
    <slot />
  </StepperTitle>
</template>
