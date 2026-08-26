<template>
	<Dialog :open="open" @update:open="$emit('update:open', $event)">
		<DialogContent class="sm:max-w-136">
			<DialogHeader>
				<DialogTitle>{{ isCreate ? "New Workspace" : "Open Workspace" }}</DialogTitle>
				<DialogDescription>
					{{
						isCreate
							? "Choose a parent folder and enter a name for the new workspace."
							: "Select an existing directory to use as your workspace."
					}}
				</DialogDescription>
			</DialogHeader>

			<FileViewer v-if="open" :include-files="false" :mode="mode" @workspace-ready="onWorkspaceReady" />
		</DialogContent>
	</Dialog>
</template>

<script setup>
import { computed } from "vue";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import FileViewer from "@/components/FileViewer.vue";

/**
 * The workspace pick/create dialog shared by the Home view and the File menu.
 * {@prop mode} is "new" to create a folder or "open" to pick an existing directory.
 */
const props = defineProps({
	open: { type: Boolean, required: true },
	mode: {
		type: String,
		default: "open",
		validator: (value) => ["new", "open"].includes(value),
	},
});

const emit = defineEmits(["update:open", "workspace-ready"]);

const isCreate = computed(() => props.mode === "new");

function onWorkspaceReady() {
	emit("update:open", false);
	emit("workspace-ready");
}
</script>