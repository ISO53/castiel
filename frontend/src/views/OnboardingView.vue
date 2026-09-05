<template>
	<ScrollArea class="h-full bg-card">
		<div class="mx-auto flex max-w-2xl flex-col gap-8 p-6">
			<div class="space-y-1">
				<h1 class="text-xl font-semibold tracking-tight text-foreground">Welcome to Castiel</h1>
				<p class="text-xs text-muted-foreground">
					A short tour of the things to set up before your first engagement.
				</p>
			</div>

			<Stepper v-model="stepIndex" class="flex items-start gap-2">
				<StepperItem
					v-for="item in steps"
					:key="item.step"
					:step="item.step"
					class="relative flex w-full flex-col items-center justify-center"
				>
					<StepperTrigger>
						<StepperIndicator class="bg-muted">
							<component :is="item.icon" class="size-4" />
						</StepperIndicator>
					</StepperTrigger>
					<StepperSeparator
						v-if="item.step !== steps[steps.length - 1].step"
						class="absolute left-[calc(50%+20px)] right-[calc(-50%+10px)] top-5 block h-0.5 shrink-0 rounded-full bg-muted group-data-[state=completed]:bg-primary"
					/>
					<div class="flex flex-col items-center">
						<StepperTitle class="text-xs">{{ item.title }}</StepperTitle>
					</div>
				</StepperItem>
			</Stepper>

			<div class="space-y-3 rounded-lg border border-border p-5">
				<div class="space-y-1">
					<h2 class="text-base font-semibold text-foreground">{{ current.title }}</h2>
				</div>
				<p class="text-xs leading-relaxed text-muted-foreground">{{ current.body }}</p>
				<Button v-if="current.action" size="sm" variant="outline" @click="runAction(current)">
					{{ current.action }}
				</Button>
			</div>

			<div class="flex items-center justify-between">
				<Button variant="outline" size="sm" :disabled="stepIndex <= 1" @click="stepIndex--">
					Back
				</Button>
				<Button size="sm" @click="advance">
					{{ stepIndex >= steps.length ? "Done" : "Next" }}
				</Button>
			</div>
		</div>
	</ScrollArea>
</template>

<script>
import { Bot, FolderOpen, Plug, Rocket, Sparkles } from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
	Stepper,
	StepperIndicator,
	StepperItem,
	StepperSeparator,
	StepperTitle,
	StepperTrigger,
} from "@/components/ui/stepper";
import { useTabsStore } from "@/stores/tabs";

const ONBOARDING_SEEN_KEY = "castiel.onboardingComplete";

export default {
	name: "OnboardingView",
	components: {
		Button,
		ScrollArea,
		Stepper,
		StepperIndicator,
		StepperItem,
		StepperSeparator,
		StepperTitle,
		StepperTrigger,
	},
	data() {
		return {
			stepIndex: 1,
			steps: [
				{
					step: 1,
					title: "Welcome p3ntester",
					icon: Sparkles,
					body:
						"Castiel is an AI-powered pentesting harness built to give AI agents a powerful set of " +
						"tools. It helps uncover vulnerabilities, investigate targets, and automate the tedious " +
						"parts of pentesting. Fast, capable, and built to go where other tools stop."
				},
				{
					step: 2,
					title: "LLM Provider",
					icon: Bot,
					action: "Open Settings",
					tab: "settings",
					body:
						"Castiel needs a language model to talk to. Open the settings view and connect one of " +
						"the supported providers under LLM Providers."
				},
				{
					step: 3,
					title: "MCP Servers",
					icon: Plug,
					action: "Open Settings",
					tab: "settings",
					body:
						"MCP servers give the agent extra tools such as a headless browser or proxy access. " +
						"Start them yourself, then register them in the settings view under MCP Servers. " +
						"Castiel highly recommends adding the Obscura and Caido MCP servers for a better " +
						"pentesting environment.",
				},
				{
					step: 4,
					title: "Workspace",
					icon: FolderOpen,
					body:
						"The agent works inside a workspace. A folder that holds the files, notes and " +
						"evidence of an engagement. Open or create one from the File menu before chatting " +
						"with the agent.",
				},
				{
					step: 5,
					title: "All Set",
					icon: Rocket,
					body:
						"You are ready to go. Talk to your agent in the right dock, and browse your workspace " +
						"files in the left dock's file tree. The menu bar lets you track and select the " +
						"current engagement phase, and the tabbed area hosts the different views of your " +
						"engagement. Long-running commands and background processes show up in the bottom dock. " +
						"You can reopen this tour anytime from Help > Show Onboarding.",
				},
			],
		};
	},
	computed: {
		current() {
			return this.steps[Math.max(0, Math.min(this.stepIndex, this.steps.length) - 1)];
		},
	},
	mounted() {
		// The tour is shown once; reopening it from the menu does not reschedule it.
		localStorage.setItem(ONBOARDING_SEEN_KEY, "1");
	},
	methods: {
		advance() {
			if (this.stepIndex >= this.steps.length) {
				this.close();
				return;
			}
			this.stepIndex++;
		},
		runAction(item) {
			if (item.tab === "settings") {
				useTabsStore().openTab({
					value: "settings",
					label: "Settings",
					component: "SettingsView",
				});
			}
		},
		close() {
			useTabsStore().closeTab("onboarding");
		},
	},
};
</script>
