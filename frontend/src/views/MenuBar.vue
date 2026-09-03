<template>
	<Menubar class="relative">
		<h1 class="app-name">Castiel</h1>
		<MenubarMenu>
			<MenubarTrigger>File</MenubarTrigger>
			<MenubarContent>
				<MenubarItem @click="newWorkspace">New Workspace</MenubarItem>
				<MenubarItem @click="openWorkspace">Open Workspace</MenubarItem>
				<MenubarSeparator />
				<MenubarItem>Close App</MenubarItem>
			</MenubarContent>
		</MenubarMenu>

		<MenubarMenu>
			<MenubarTrigger>Window</MenubarTrigger>
			<MenubarContent>
				<MenubarItem @click="docks.toggle('left')">Toggle left dock</MenubarItem>
				<MenubarItem @click="docks.toggle('right')">Toggle right dock</MenubarItem>
				<MenubarItem @click="docks.toggle('bottom')">Toggle bottom dock</MenubarItem>
			</MenubarContent>
		</MenubarMenu>

		<MenubarMenu>
			<MenubarTrigger>Help</MenubarTrigger>
			<MenubarContent>
				<MenubarItem @click="openSettings">Settings</MenubarItem>
				<MenubarSeparator />
				<MenubarItem @click="showOnboarding">Show Onboarding</MenubarItem>
				<MenubarItem @click="showWelcome">Show Welcome</MenubarItem>
				<MenubarSeparator />
				<MenubarItem @click="requestFeature">Request Feature</MenubarItem>
				<MenubarItem @click="fileIssue">File Issue</MenubarItem>
				<MenubarSeparator />
				<MenubarItem @click="aboutOpen = true">About Castiel</MenubarItem>
			</MenubarContent>
		</MenubarMenu>

		<!-- Engagement phase stepper, centered in the menu bar -->
		<div
			class="pointer-events-none absolute left-1/2 top-1/2 z-10 flex -translate-x-1/2 -translate-y-1/2 items-center gap-0.5"
		>
			<button
				v-for="item in ENGAGEMENT_PHASES"
				:key="item.value"
				type="button"
				class="pointer-events-auto flex h-6 items-center gap-1.5 rounded-md px-2 py-0 text-xs transition-colors disabled:cursor-default disabled:opacity-60"
				:class="chipClasses(item)"
				:title="workspace.cwd ? item.label : `${item.label} — open a workspace to engage`"
				:disabled="!workspace.cwd || engagement.loading"
				@click="select(item)"
			>
				<span
					class="grid size-4 shrink-0 place-items-center rounded-full border text-[10px] leading-none font-semibold"
					:class="stepClasses(item)"
				>
					<Check v-if="isDone(item)" class="size-3" />
					<template v-else>{{ item.value }}</template>
				</span>
				<span class="whitespace-nowrap font-medium leading-none">{{ item.short }}</span>
			</button>
		</div>

		<WorkspaceDialog v-model:open="workspaceDialogOpen" :mode="workspaceMode" />
		<AboutDialog v-model:open="aboutOpen" />
	</Menubar>
</template>

<script>
import { Check } from "@lucide/vue";
import { Menubar, MenubarContent, MenubarItem, MenubarMenu, MenubarSeparator, MenubarTrigger } from "@/components/ui/menubar";
import { ENGAGEMENT_PHASES, useEngagementStore } from "@/stores/engagement";
import { useDocksStore } from "@/stores/docks";
import { useTabsStore } from "@/stores/tabs";
import { useWorkspaceStore } from "@/stores/workspace";
import AboutDialog from "@/components/AboutDialog.vue";
import WorkspaceDialog from "@/components/WorkspaceDialog.vue";

const GITHUB_REPO = "https://github.com/iso53/castiel";

export default {
	name: "AppMenu",
	components: {
		Check,
		Menubar,
		MenubarContent,
		MenubarItem,
		MenubarMenu,
		MenubarSeparator,
		MenubarTrigger,
		AboutDialog,
		WorkspaceDialog,
	},
	data() {
		return {
			ENGAGEMENT_PHASES,
			tabs: useTabsStore(),
			engagement: useEngagementStore(),
			workspace: useWorkspaceStore(),
			docks: useDocksStore(),
			workspaceDialogOpen: false,
			workspaceMode: "open",
			aboutOpen: false,
			pollTimer: null,
		};
	},
	watch: {
		"workspace.cwd": {
			immediate: true,
			handler(cwd) {
				this.stopPolling();

				if (!cwd) return;

				this.engagement.fetch();
				this.pollTimer = setInterval(() => {
					this.engagement.fetch();
				}, 15000);
			},
		},
	},
	beforeUnmount() {
		this.stopPolling();
	},
	methods: {
		newWorkspace() {
			this.workspaceMode = "new";
			this.workspaceDialogOpen = true;
		},
		openWorkspace() {
			this.workspaceMode = "open";
			this.workspaceDialogOpen = true;
		},
		showOnboarding() {
			this.tabs.openTab({
				value: "onboarding",
				label: "Onboarding",
				component: "OnboardingView",
				closable: true,
			});
		},
		showWelcome() {
			this.tabs.openTab({
				value: "home",
				label: "Home",
				component: "HomeView",
				closable: true,
			});
		},
		requestFeature() {
			window.open(
				`${GITHUB_REPO}/issues/new?labels=enhancement`,
				"_blank",
				"noopener,noreferrer",
			);
		},
		fileIssue() {
			window.open(
				`${GITHUB_REPO}/issues/new`,
				"_blank",
				"noopener,noreferrer",
			);
		},
		stopPolling() {
			if (this.pollTimer) {
				clearInterval(this.pollTimer);
				this.pollTimer = null;
			}
		},
		isDone(item) {
			return (
				typeof this.engagement.phase === "number" &&
				item.value < this.engagement.phase
			);
		},
		chipClasses(item) {
			if (
				!this.workspace.cwd ||
				typeof this.engagement.phase !== "number"
			) {
				return "text-muted-foreground/50 hover:text-foreground";
			}

			if (this.engagement.phase === item.value) {
				return "bg-primary text-primary-foreground";
			}

			return "text-muted-foreground hover:text-foreground";
		},
		stepClasses(item) {
			if (
				!this.workspace.cwd ||
				typeof this.engagement.phase !== "number"
			) {
				return "border-current";
			}

			if (this.engagement.phase === item.value) {
				return "border-current";
			}

			if (this.isDone(item)) {
				return "border-muted-foreground text-muted-foreground";
			}

			return "border-muted-foreground/40";
		},
		async select(item) {
			if (this.engagement.phase === item.value) {
				return;
			}

			try {
				await this.engagement.setPhase(item.value);
			} catch (err) {
				console.error(err.message);
				this.engagement.error = err.message;
			}
		},
		openSettings() {
			this.tabs.openTab({
				value: "settings",
				label: "Settings",
				component: "SettingsView",
				closable: true,
			});
		},
	},
};
</script>

<style scoped>
.app-name {
	font-size: 0.8rem;
	margin-right: 14px;
	user-select: none;
}
</style>
