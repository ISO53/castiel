<template>
	<Empty class="h-full select-none">
		<EmptyHeader>
			<EmptyMedia class="text-muted-foreground/60 [&_svg]:size-5">
				<slot />
			</EmptyMedia>
			<EmptyDescription class="text-muted-foreground/60">{{ text }}</EmptyDescription>
		</EmptyHeader>
	</Empty>
</template>

<script>
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia } from "@/components/ui/empty";

// Shared empty state: one faded icon and one sentence, centered in the panel.
// The icon is passed as a slot so it resolves the same way in every view.
export default {
	name: "EmptyState",
	components: { Empty, EmptyDescription, EmptyHeader, EmptyMedia },
	props: {
		text: { type: String, required: true },
	},
};
</script>
