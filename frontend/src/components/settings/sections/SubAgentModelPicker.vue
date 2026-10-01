<template>
	<Popover v-model:open="open">
		<PopoverTrigger as-child>
			<Button
				variant="outline"
				role="combobox"
				:aria-expanded="open"
				:disabled="disabled"
				class="w-52 justify-between font-normal"
			>
				<span class="truncate" :class="modelValue ? '' : 'text-muted-foreground'">
					{{ modelValue || "Not set" }}
				</span>
				<ChevronsUpDown class="shrink-0 opacity-50" />
			</Button>
		</PopoverTrigger>
		<PopoverContent class="w-(--reka-popover-trigger-width) p-0">
			<Command>
				<CommandInput class="h-9" :placeholder="searchPlaceholder" />
				<CommandList>
					<CommandEmpty>Nothing found.</CommandEmpty>
					<CommandGroup>
						<CommandItem v-for="option in options" :key="option" :value="option"
								@select="onSelect">
								{{ option }}
								<Check
									:class="['ml-auto', modelValue === option ? 'opacity-100' : 'opacity-0']" />
							</CommandItem>
					</CommandGroup>
				</CommandList>
			</Command>
		</PopoverContent>
	</Popover>
</template>

<script setup>
import { ref } from "vue";
import { Check, ChevronsUpDown } from "@lucide/vue";
import {
	Command,
	CommandEmpty,
	CommandGroup,
	CommandInput,
	CommandItem,
	CommandList,
} from "@/components/ui/command";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

/**
 * A searchable single-choice control. One provider picker and one model picker
 * per sub-agent kind, so this stays a dumb control: the parent owns the value,
 * the option list and what to clear when the other pick changes.
 */
defineProps({
	modelValue: { type: String, default: "" },
	options: { type: Array, default: () => [] },
	disabled: { type: Boolean, default: false },
	searchPlaceholder: { type: String, default: "Search..." },
});

const emit = defineEmits(["update:modelValue"]);
const open = ref(false);

// CommandItem emits a CustomEvent; the chosen value lives on its detail.
function onSelect(event) {
	emit("update:modelValue", String(event.detail.value));
	open.value = false;
}
</script>

