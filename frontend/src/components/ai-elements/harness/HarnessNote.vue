<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { cn } from '@/lib/utils'
import { SettingsIcon } from '@lucide/vue'
import { useVModel } from '@vueuse/core'

interface Props {
  class?: HTMLAttributes['class']
  content?: string
  open?: boolean
  defaultOpen?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  defaultOpen: false,
})

const emit = defineEmits<{ (e: 'update:open', value: boolean): void }>()

const isOpen = useVModel(props, 'open', emit, {
  defaultValue: props.defaultOpen,
  passive: true,
})
</script>

<template>
  <!-- Operator styling on purpose: centered with hairline rules, monospace, and a gear glyph.
       Deliberately not a user bubble and not an agent bubble, so it never reads as dialogue. -->
  <Collapsible
    v-model:open="isOpen"
    :class="cn('not-prose my-1 w-full self-center', props.class)"
  >
    <div class="flex w-full items-center gap-3">
      <hr class="h-px min-w-0 flex-1 border-0 bg-primary" />

      <CollapsibleTrigger
        class="group/note flex shrink-0 items-center gap-1.5 text-muted-foreground transition-colors hover:text-foreground"
      >
        <SettingsIcon class="size-3 shrink-0" />
        <span class="font-mono text-[11px] tracking-tight">harness checkpoint reminder</span>
      </CollapsibleTrigger>

      <hr class="h-px min-w-0 flex-1 border-0 bg-primary" />
    </div>

    <CollapsibleContent
      class="mt-2 font-mono text-[11px] leading-relaxed text-muted-foreground data-[state=closed]:animate-out data-[state=open]:animate-in"
    >
      <pre class="m-0 whitespace-pre-wrap break-words">{{ props.content }}</pre>
    </CollapsibleContent>
  </Collapsible>
</template>
