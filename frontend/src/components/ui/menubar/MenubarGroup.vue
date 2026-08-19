<script setup>
import { MenubarGroup } from "reka-ui";

const props = defineProps({
  asChild: { type: Boolean, required: false },
  as: { type: null, required: false },
});
</script>

<template>
  <MenubarGroup data-slot="menubar-group" v-bind="props">
    <slot />
  </MenubarGroup>
</template>
