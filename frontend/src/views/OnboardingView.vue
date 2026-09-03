<template>
	<ScrollArea class="h-full">
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
						<StepperDescription class="hidden text-[10px] sm:block">
							{{ item.description }}
						</StepperDescription>
					</div>
				</StepperItem>
			</Stepper>

			<div class="space-y-3 rounded-lg border border-border p-5">
				<div class="space-y-1">
					<h2 class="flex items-center gap-2 text-base font-semibold text-foreground">
						<component :is="current.icon" class="size-4 text-muted-foreground" />
						{{ current.title }}
					</h2>
					<p class="text-xs text-muted-foreground">{{ current.description }}</p>
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
	StepperDescription,
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
		StepperDescription,
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
					title: "Welcome",
					description: "Meet Castiel",
					icon: Sparkles,
					body:
						"Castiel is an AI-powered pentesting harness. It gives an agent safe access to your " +
						"machine, files and terminal so it can work engagement phases with you. This tour " +
						"covers the setup steps before your first session.",
				},
				{
					step: 2,
					title: "LLM Provider",
					description: "Connect a model",
					icon: Bot,
					action: "Open Settings",
					tab: "settings",
					body:
						"Castiel needs a language model to talk to. Open the settings view and connect one of " +
						"the supported providers under LLM Providers — a local llama.cpp or Ollama server, or " +
						"a cloud endpoint through OpenRouter.",
				},
				{
					step: 3,
					title: "MCP Servers",
					description: "Add tool servers",
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
					description: "Open a workspace",
					icon: FolderOpen,
					body:
						"The agent works inside a workspace — a folder that holds the files, notes and " +
						"evidence of an engagement. Open or create one from the File menu before chatting " +
						"with the agent.",
				},
				{
					step: 5,
					title: "All Set",
					description: "Start working",
					icon: Rocket,
					body:
						"You are ready to go. Use the right dock to talk to your agent, watch it run tools " +
						"in the bottom dock, and track the engagement phase from the menu bar. You can " +
						"reopen this tour anytime from Help → Show Onboarding.",
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
