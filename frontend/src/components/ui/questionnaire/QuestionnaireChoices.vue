<script setup>
import { Primitive } from "reka-ui";
import { cn } from "@/lib/utils";
import { injectQuestionnaireItemContext } from "./useQuestionnaire";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false, default: "div" },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
});

const item = injectQuestionnaireItemContext();
</script>

<template>
  <Primitive
    data-slot="questionnaire-choices"
    :as="props.as"
    :as-child="props.asChild"
    :data-shortcuts="item.shortcuts.value ?? undefined"
    :class="cn('gap-1.5 group/questionnaire-choices grid min-w-0', props.class)"
  >
    <slot :shortcuts="item.shortcuts.value" />
  </Primitive>
</template>
