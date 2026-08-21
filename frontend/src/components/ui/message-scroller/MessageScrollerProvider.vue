<script setup>
import { provideMessageScroller } from './useMessageScroller';
const props = defineProps();
provideMessageScroller(props);
</script>

<template>
  <slot />
</template>
