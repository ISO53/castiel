<script setup>
import { Primitive } from "reka-ui";
import { onBeforeUnmount, onMounted, ref, useId } from "vue";
import { cn } from "@/lib/utils";
import { injectQuestionnaireItemContext } from "./useQuestionnaire";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false, default: "legend" },
  class: {
    type: [Boolean, null, String, Object, Array],
    required: false,
    skipCheck: true,
  },
  id: { type: String, required: false },
});

const item = injectQuestionnaireItemContext();

const primitiveRef = ref(null);
const fallbackId = props.id ?? useId();

let unregisterTitle = null;

onMounted(() => {
  const element = primitiveRef.value?.$el;

  // A legend already names the fieldset. Anything else, for example a
  // DialogTitle rendered through `as-child`, has to name it explicitly.
  if (!element || element.tagName === "LEGEND") {
    return;
  }

  if (!element.id) {
    element.id = fallbackId;
  }

  unregisterTitle = item.registerTitle(element.id);
});

onBeforeUnmount(() => unregisterTitle?.());
</script>

<template>
  <Primitive
    v-bind="props.id ? { id: props.id } : {}"
    ref="primitiveRef"
    data-slot="questionnaire-title"
    :as="props.as"
    :as-child="props.asChild"
    :class="
      cn(
        'text-sm font-semibold [&:not(:has(~[data-slot=questionnaire-description]))]:mb-3 cn-font-heading text-pretty',
        props.class,
      )
    "
  >
    <slot />
  </Primitive>
</template>
