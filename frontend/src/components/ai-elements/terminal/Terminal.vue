<script setup>
import { computed, getCurrentInstance, provide } from "vue";
import { TerminalKey } from "./context";
import TerminalActions from "./TerminalActions.vue";
import TerminalClearButton from "./TerminalClearButton.vue";
import TerminalContent from "./TerminalContent.vue";
import TerminalCopyButton from "./TerminalCopyButton.vue";
import TerminalHeader from "./TerminalHeader.vue";
import TerminalStatus from "./TerminalStatus.vue";
import TerminalTitle from "./TerminalTitle.vue";

const props = defineProps({
	output: { type: String, default: "" },
	isStreaming: { type: Boolean, default: false },
	autoScroll: { type: Boolean, default: true },
});

const emit = defineEmits(["clear"]);

const instance = getCurrentInstance();
const hasClear = computed(() => !!instance?.vnode.props?.onClear);

provide(TerminalKey, {
	output: computed(() => props.output),
	isStreaming: computed(() => props.isStreaming),
	autoScroll: computed(() => props.autoScroll),
	hasClear,
	onClear: () => emit("clear"),
});
</script>

<template>
	<div
		class="flex min-h-0 flex-col overflow-hidden bg-background"
		v-bind="$attrs"
	>
		<TerminalHeader>
			<TerminalTitle />
			<div class="flex items-center gap-1">
				<TerminalStatus />
				<TerminalActions>
					<TerminalCopyButton />
					<TerminalClearButton v-if="hasClear" />
				</TerminalActions>
			</div>
		</TerminalHeader>
		<TerminalContent />
	</div>
</template>
