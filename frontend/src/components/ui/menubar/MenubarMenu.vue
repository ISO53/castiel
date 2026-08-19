<script setup>
import { MenubarMenu } from "reka-ui";

const props = defineProps({
  value: { type: String, required: false },
});
</script>

<template>
  <MenubarMenu data-slot="menubar-menu" v-bind="props">
    <slot />
  </MenubarMenu>
</template>
