<script setup>
import { Primitive } from "reka-ui";
import { computed } from "vue";
import { cn } from "@/lib/utils";
import { buttonVariants } from '@/components/ui/button';
import { injectQuestionnaireRootContext } from "./useQuestionnaire";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false, default: "button" },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  disabled: { type: Boolean, required: false, default: false },
  size: { type: null, required: false, default: "default" },
  variant: { type: null, required: false, default: "outline" },
});

const emits = defineEmits(["click"]);

const root = injectQuestionnaireRootContext();

const visible = computed(() => root.activeItemRequired.value === false);

function handleClick(event) {
  emits("click", event);

  // `disabled` does not block clicks once `as` or `as-child` renders something
  // other than a button.
  if (props.disabled) {
    event.preventDefault();
    return;
  }

  if (!event.defaultPrevented) {
    root.skipCurrent();
  }
}
</script>

<template>
  <Primitive
    data-slot="questionnaire-skip"
    type="button"
    :aria-hidden="!visible || undefined"
    :aria-disabled="props.disabled || undefined"
    :as="props.as"
    :as-child="props.asChild"
    :data-disabled="props.disabled ? '' : undefined"
    :data-hidden="visible ? undefined : ''"
    :data-size="props.size"
    :data-status="root.activeItemStatus.value ?? undefined"
    :data-variant="props.variant"
    :data-visible="visible ? '' : undefined"
    :disabled="props.disabled"
    :hidden="!visible"
    :inert="!visible"
    :tabindex="visible ? undefined : -1"
    :class="
      cn(
        buttonVariants({ size: props.size, variant: props.variant }),
        'col-start-2 row-start-1 min-h-11 justify-self-end sm:min-h-0',
        props.class,
      )
    "
    @click="handleClick"
  >
    <slot>Skip</slot>
  </Primitive>
</template>
