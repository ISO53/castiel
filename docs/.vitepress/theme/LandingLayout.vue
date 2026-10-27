<script setup>
import NavBar from "@/components/NavBar.vue";
import FooterSection from "@/components/FooterSection.vue";
import HomeView from "@/views/HomeView.vue";
</script>

<template>
	<!-- Same stack as the previous SPA App.vue: navbar on top, page below, footer last. -->
	<NavBar />
	<HomeView />
	<FooterSection />
</template>
