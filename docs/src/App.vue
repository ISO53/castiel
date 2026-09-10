<script setup>
import NavBar from "@/components/NavBar.vue";
import FooterSection from "@/components/FooterSection.vue";
</script>

<template>
	<!-- Navbar is global; pages render below it. -->
	<NavBar />
	<RouterView />
	<FooterSection />
</template>
