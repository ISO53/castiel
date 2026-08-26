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
				<MenubarItem @click="docks.bottom = !docks.bottom">Toggle bottom dock</MenubarItem>
			</MenubarContent>
		</MenubarMenu>

		<MenubarMenu>
			<MenubarTrigger>Help</MenubarTrigger>
			<MenubarContent>
				<MenubarItem @click="openSettings">Settings</MenubarItem>
				<MenubarSeparator />
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
				class="pointer-events-auto flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs transition-colors disabled:cursor-default disabled:opacity-60"
				:class="chipClasses(item)"
				:title="workspace.cwd ? item.label : `${item.label} — open a workspace to engage`"
				:disabled="!workspace.cwd || engagement.loading"
				@click="select(item)"
			>
				<span
					class="flex size-4 shrink-0 items-center justify-center rounded-full border text-[10px] font-semibold"
					:class="stepClasses(item)"
				>
					<Check v-if="isDone(item)" class="size-3" />
					<template v-else>{{ item.value }}</template>
				</span>
				<span class="whitespace-nowrap font-medium">{{ item.short }}</span>
			</button>
		</div>

		<WorkspaceDialog v-model:open="workspaceDialogOpen" :mode="workspaceMode" />
		<AboutDialog v-model:open="aboutOpen" />
	</Menubar>
</template>

<script setup>
import { Check } from "@lucide/vue";
import { onBeforeUnmount, ref, watch } from "vue";
import {
	Menubar,
	MenubarContent,
	MenubarItem,
	MenubarMenu,
	MenubarSeparator,
	MenubarTrigger,
} from "@/components/ui/menubar";
import { ENGAGEMENT_PHASES, useEngagementStore } from "@/stores/engagement";
import { useDocksStore } from "@/stores/docks";
import { useTabsStore } from "@/stores/tabs";
import { useWorkspaceStore } from "@/stores/workspace";
import AboutDialog from "@/components/AboutDialog.vue";
import WorkspaceDialog from "@/components/WorkspaceDialog.vue";

const tabs = useTabsStore();
const engagement = useEngagementStore();
const workspace = useWorkspaceStore();
const docks = useDocksStore();

const GITHUB_REPO = "https://github.com/iso53/castiel";

const workspaceDialogOpen = ref(false);
const workspaceMode = ref("open");
const aboutOpen = ref(false);

function newWorkspace() {
	workspaceMode.value = "new";
	workspaceDialogOpen.value = true;
}

function openWorkspace() {
	workspaceMode.value = "open";
	workspaceDialogOpen.value = true;
}

function showWelcome() {
	tabs.openTab({ value: "home", label: "Home", component: "HomeView", closable: true });
}

function requestFeature() {
	window.open(`${GITHUB_REPO}/issues/new?labels=enhancement`, "_blank", "noopener,noreferrer");
}

function fileIssue() {
	window.open(`${GITHUB_REPO}/issues/new`, "_blank", "noopener,noreferrer");
}

let pollTimer = null;

function stopPolling() {
	if (pollTimer) {
		clearInterval(pollTimer);
		pollTimer = null;
	}
}

// The agent edits engagement.json directly; light polling keeps the stepper in sync.
watch(
	() => workspace.cwd,
	(cwd) => {
		stopPolling();
		if (!cwd) return;
		engagement.fetch();
		pollTimer = setInterval(() => engagement.fetch(), 15000);
	},
	{ immediate: true },
);

onBeforeUnmount(stopPolling);

function isDone(item) {
	return typeof engagement.phase === "number" && item.value < engagement.phase;
}

function chipClasses(item) {
	if (!workspace.cwd || typeof engagement.phase !== "number") return "text-muted-foreground/50 hover:text-foreground";
	if (engagement.phase === item.value) return "bg-primary text-primary-foreground";
	return "text-muted-foreground hover:text-foreground";
}

function stepClasses(item) {
	if (!workspace.cwd || typeof engagement.phase !== "number") return "border-current";
	if (engagement.phase === item.value) return "border-current";
	if (isDone(item)) return "border-muted-foreground text-muted-foreground";
	return "border-muted-foreground/40";
}

async function select(item) {
	if (engagement.phase === item.value) return;
	try {
		await engagement.setPhase(item.value);
	} catch (err) {
		console.error(err.message);
		engagement.error = err.message;
	}
}

function openSettings() {
	tabs.openTab({
		value: "settings",
		label: "Settings",
		component: "SettingsView",
		closable: true,
	});
}
</script>

<style scoped>
.app-name {
	font-size: 0.8rem;
	margin-right: 14px;
	user-select: none;
}
</style>
