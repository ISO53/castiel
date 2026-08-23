<script setup>
import { Primitive } from "reka-ui";
import { onBeforeUnmount, onMounted, ref, useId } from "vue";
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

const primitiveRef = ref(null);
const fallbackId = props.id ?? useId();
const descriptionId = ref(fallbackId);

let unregisterDescription = item.registerDescription(descriptionId.value);

onMounted(() => {
  // With `as-child` the rendered child can bring its own id, for example a
  // DialogDescription. Adopt it so both descriptions point at one element.
  const element = primitiveRef.value?.$el;
  const renderedId = element?.id;

  if (!renderedId) {
    if (element) {
      element.id = fallbackId;
    }

    return;
  }

  if (renderedId !== descriptionId.value) {
    unregisterDescription();
    descriptionId.value = renderedId;
    unregisterDescription = item.registerDescription(renderedId);
  }
});

onBeforeUnmount(() => unregisterDescription());
</script>

<template>
  <Primitive
    v-bind="props.asChild ? {} : { id: descriptionId }"
    ref="primitiveRef"
    data-slot="questionnaire-description"
    :as="props.as"
    :as-child="props.asChild"
    :class="
      cn('text-xs/relaxed text-pretty text-muted-foreground', props.class)
    "
  >
    <slot />
  </Primitive>
</template>
