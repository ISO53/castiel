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
  variant: { type: null, required: false, default: "default" },
});

const emits = defineEmits(["click"]);

const root = injectQuestionnaireRootContext();

const visible = computed(() => root.total.value > 1 && !root.last.value);
const shortcut = computed(() =>
  visible.value && !props.disabled ? "Enter" : null,
);

function handleClick(event) {
  emits("click", event);

  // `disabled` does not block clicks once `as` or `as-child` renders something
  // other than a button.
  if (props.disabled) {
    event.preventDefault();
    return;
  }

  if (!event.defaultPrevented) {
    root.goNext();
  }
}
</script>

<template>
  <Primitive
    data-slot="questionnaire-next"
    type="button"
    :aria-hidden="!visible || undefined"
    :aria-disabled="props.disabled || undefined"
    :aria-keyshortcuts="shortcut ?? undefined"
    :as="props.as"
    :as-child="props.asChild"
    :data-disabled="props.disabled ? '' : undefined"
    :data-hidden="visible ? undefined : ''"
    :data-shortcut="shortcut ?? undefined"
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
        'col-start-3 row-start-1 min-h-11 justify-self-end sm:min-h-0',
        props.class,
      )
    "
    @click="handleClick"
  >
    <slot>Next</slot>
  </Primitive>
</template>
