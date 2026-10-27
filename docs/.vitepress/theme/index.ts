import DefaultTheme from "vitepress/theme";

import Layout from "./Layout.vue";
import "@fontsource-variable/geist";
import "@/assets/main.css";
import "./custom.css";

export default {
	extends: DefaultTheme,
	Layout,
};
