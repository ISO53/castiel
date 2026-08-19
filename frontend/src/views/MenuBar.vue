<template>
	<Menubar>
	<h1 class="app-name">Castiel</h1>
		<MenubarMenu>
			<MenubarTrigger>File</MenubarTrigger>
			<MenubarContent>
				<MenubarItem>New Project</MenubarItem>
				<MenubarItem>Open Project</MenubarItem>
				<MenubarSeparator />
				<MenubarItem>Close App</MenubarItem>
			</MenubarContent>
		</MenubarMenu>

		<MenubarMenu>
			<MenubarTrigger>Help</MenubarTrigger>
			<MenubarContent>
				<MenubarItem>Settings</MenubarItem>
				<MenubarItem>About</MenubarItem>
				<MenubarItem>Report Issue</MenubarItem>
			</MenubarContent>
		</MenubarMenu>
	</Menubar>
</template>

<script setup>
import {
	Menubar,
	MenubarContent,
	MenubarItem,
	MenubarMenu,
	MenubarSeparator,
	MenubarTrigger,
} from "@/components/ui/menubar";
</script>

<style scoped>
.app-name {
	font-size: 0.8rem;
	margin-right: 14px;
	user-select: none;
}
</style>
