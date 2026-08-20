<script setup>
import { reactiveOmit } from "@vueuse/core";
import { TabsTrigger, useForwardProps } from "reka-ui";
import { cn } from "@/lib/utils";
import { X } from "@lucide/vue";

const props = defineProps({
  value: { type: [String, Number], required: true },
  disabled: { type: Boolean, required: false },
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false },
  closable: { type: Boolean, required: false },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});

const delegatedProps = reactiveOmit(props, "class", "closable");

const forwardedProps = useForwardProps(delegatedProps);
</script>

<template>
  <TabsTrigger
    data-slot="tabs-trigger"
    :class="
      cn(
        'group/trig group-data-vertical/tabs:w-full group-data-vertical/tabs:justify-start relative inline-flex h-full items-center justify-center whitespace-nowrap px-5 text-xs font-medium select-none border-r border-border',
        'group-data-vertical/tabs:py-[calc(--spacing(1.25))] [&_svg:not([class*=size-])]:size-3.5',
        'focus-visible:ring-2 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50',
        'data-active:bg-card data-active:text-foreground text-foreground/60',
        props.class,
      )
    "
    v-bind="forwardedProps"
  >
    <slot />
    <span
      v-if="props.closable"
      role="button"
      tabindex="-1"
      aria-label="Close tab"
      class="absolute right-1 top-1/2 -translate-y-1/2 group-hover/trig:opacity-100 flex size-3.5 items-center justify-center rounded-sm text-foreground/40 opacity-0 hover:bg-muted hover:text-foreground [&_svg]:size-3"
      @mousedown.prevent.stop
      @click.stop.prevent="$emit('close')"
    >
      <X />
    </span>
  </TabsTrigger>
</template>
