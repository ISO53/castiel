import { cva } from "class-variance-authority";

export { default as Tabs } from "./Tabs.vue";
export { default as TabsContent } from "./TabsContent.vue";
export { default as TabsList } from "./TabsList.vue";
export { default as TabsTrigger } from "./TabsTrigger.vue";

export const tabsListVariants = cva(
	"group-data-horizontal/tabs:h-8 group/tabs-list inline-flex w-full items-center justify-start gap-0 text-muted-foreground group-data-vertical/tabs:h-fit group-data-vertical/tabs:flex-col",
	{
		variants: {
			variant: {
				default: "rounded-none border-b border-border bg-transparent",
				line: "gap-1 rounded-none border-b border-border bg-transparent",
			},
		},
		defaultVariants: {
			variant: "default",
		},
	},
);
