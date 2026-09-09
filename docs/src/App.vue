<script setup>
import NavBar from "@/components/NavBar.vue";
</script>

<template>
	<!-- Navbar is global; pages render below it. -->
	<NavBar />
	<RouterView />
</template>
