<template>
	<Menubar class="relative">
		<h1 class="app-name">castiel</h1>
		<MenubarMenu>
			<MenubarTrigger>File</MenubarTrigger>
			<MenubarContent>
				<MenubarItem @click="newWorkspace">
					<FolderPlus />
					<span>New Workspace</span>
				</MenubarItem>
				<MenubarItem @click="openWorkspace">
					<FolderOpen />
					<span>Open Workspace</span>
				</MenubarItem>
				<MenubarSeparator />
				<MenubarItem inset>Close App</MenubarItem>
			</MenubarContent>
		</MenubarMenu>

		<MenubarMenu>
			<MenubarTrigger>Window</MenubarTrigger>
			<MenubarContent>
				<MenubarItem @click="docks.openView('left', 'files')">
					<FolderTree />
					<span>File Tree</span>
				</MenubarItem>
				<MenubarItem @click="docks.openView('left', 'views')">
					<LayoutGrid />
					<span>Engagement Views</span>
				</MenubarItem>
				<MenubarItem @click="docks.openView('right', 'chat')">
					<MessageSquare />
					<span>Chat</span>
				</MenubarItem>
				<MenubarItem @click="docks.openView('right', 'history')">
					<History />
					<span>Chat History</span>
				</MenubarItem>
				<MenubarItem @click="docks.openView('bottom', 'processes')">
					<Terminal />
					<span>Background Processes</span>
				</MenubarItem>
				<MenubarItem @click="docks.openView('bottom', 'agents')">
					<Bot />
					<span>Sub Agents</span>
				</MenubarItem>
				<MenubarSeparator />
				<MenubarItem inset @click="docks.toggle('left')">Toggle left dock</MenubarItem>
				<MenubarItem inset @click="docks.toggle('right')">Toggle right dock</MenubarItem>
				<MenubarItem inset @click="docks.toggle('bottom')">Toggle bottom dock</MenubarItem>
			</MenubarContent>
		</MenubarMenu>

		<MenubarMenu>
			<MenubarTrigger>Help</MenubarTrigger>
			<MenubarContent>
				<MenubarItem @click="openSettings">
					<Settings />
					<span>Settings</span>
				</MenubarItem>
				<MenubarSeparator />
				<MenubarItem inset @click="showOnboarding">Show Onboarding</MenubarItem>
				<MenubarItem inset @click="showWelcome">Show Welcome</MenubarItem>
				<MenubarSeparator />
				<MenubarItem inset @click="requestFeature">Request Feature</MenubarItem>
				<MenubarItem inset @click="fileIssue">File Issue</MenubarItem>
				<MenubarSeparator />
				<MenubarItem @click="aboutOpen = true">
					<Info />
					<span>About castiel</span>
				</MenubarItem>
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
				:title="workspace.cwd ? item.label : `${item.label}. Open a workspace to engage`"
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
import {
	Bot,
	Check,
	FolderOpen,
	FolderPlus,
	FolderTree,
	History,
	Info,
	LayoutGrid,
	MessageSquare,
	Settings,
	Terminal,
} from "@lucide/vue";
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
		AboutDialog,
		Bot,
		Check,
		FolderOpen,
		FolderPlus,
		FolderTree,
		History,
		Info,
		LayoutGrid,
		Menubar,
		MenubarContent,
		MenubarItem,
		MenubarMenu,
		MenubarSeparator,
		MenubarTrigger,
		MessageSquare,
		Settings,
		Terminal,
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
		};
	},
	watch: {
		"workspace.cwd": {
			immediate: true,
			handler(cwd) {
				if (!cwd) return;
				this.engagement.fetch();
			},
		},
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
