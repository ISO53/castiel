<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { BrainIcon, ChevronDownIcon } from '@lucide/vue'
import { CollapsibleTrigger } from '@/components/ui/collapsible'
import { cn } from '@/lib/utils'
import { computed } from 'vue'
import { Shimmer } from '../shimmer'
import { useReasoningContext } from './context'

interface Props {
  class?: HTMLAttributes['class']
}

const props = defineProps<Props>()

const { isStreaming, isOpen, duration } = useReasoningContext()

const thinkingMessage = computed(() => {
  if (isStreaming.value || duration.value === 0) {
    return 'thinking'
  }
  if (duration.value === undefined) {
    return 'default_done'
  }
  return 'duration_done'
})
</script>

<template>
  <CollapsibleTrigger
    :class="cn(
      'flex w-full items-center gap-2 text-left text-muted-foreground text-xs transition-colors hover:text-foreground',
      props.class,
    )"
  >
    <slot>
      <BrainIcon class="size-3.5 shrink-0" />

      <span class="min-w-0 truncate">
        <template v-if="thinkingMessage === 'thinking'">
          <Shimmer as="span" :duration="1">
            Thinking...
          </Shimmer>
        </template>

        <template v-else-if="thinkingMessage === 'default_done'">
          Thought for a few seconds
        </template>

        <template v-else>
          Thought for {{ duration }} seconds
        </template>
      </span>

      <ChevronDownIcon
        :class="cn(
          'size-3.5 shrink-0 transition-all',
          isOpen ? 'rotate-180' : 'rotate-0',
        )"
      />
    </slot>
  </CollapsibleTrigger>
</template>
