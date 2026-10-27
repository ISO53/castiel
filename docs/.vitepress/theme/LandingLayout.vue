<script setup>
import NavBar from "@/components/NavBar.vue";
import FooterSection from "@/components/FooterSection.vue";
import HomeView from "@/views/HomeView.vue";
</script>

<template>
	<!-- Same stack as the previous SPA App.vue: navbar on top, page below, footer last.
	     The .landing wrapper scopes the SPA palette: custom.css points body color at
	     the VitePress text ramp for the docs pages, and this class restores --fg
	     inheritance for the landing page only. -->
	<div class="landing">
		<NavBar />
		<HomeView />
		<FooterSection />
	</div>
</template>
