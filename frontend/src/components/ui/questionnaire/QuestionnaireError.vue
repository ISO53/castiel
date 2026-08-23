<script setup>
import { Primitive } from "reka-ui";
import { computed, onBeforeUnmount, useId } from "vue";
import { cn } from "@/lib/utils";
import { injectQuestionnaireItemContext } from "./useQuestionnaire";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false, default: "p" },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  id: { type: String, required: false },
});

const item = injectQuestionnaireItemContext();

const errorId = props.id ?? useId();
const unregisterError = item.registerError(errorId);

const fallback = computed(() =>
  item.required.value
    ? "Choose an answer to continue."
    : "Choose an answer or skip this question.",
);

onBeforeUnmount(unregisterError);
</script>

<template>
  <Primitive
    :id="errorId"
    data-slot="questionnaire-error"
    :as="props.as"
    :as-child="props.asChild"
    :data-invalid="item.invalid.value ? '' : undefined"
    :hidden="!item.invalid.value"
    :role="item.invalid.value ? 'alert' : undefined"
    :class="cn('mt-2 text-xs/relaxed text-destructive', props.class)"
  >
    <slot :invalid="item.invalid.value">
      {{ fallback }}
    </slot>
  </Primitive>
</template>
