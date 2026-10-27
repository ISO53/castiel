<script setup>
import { useData } from "vitepress";
import DefaultTheme from "vitepress/theme";

import LandingLayout from "./LandingLayout.vue";

// The landing page renders the custom castiel layout; every other page falls
// through to the default VitePress docs layout (sidebar, TOC, search).
const { Layout: DefaultLayout } = DefaultTheme;
const { frontmatter } = useData();
</script>

<template>
	<LandingLayout v-if="frontmatter.layout === 'landing'" />
	<DefaultLayout v-else />
</template>
