import { createApp } from "vue";
import "@fontsource-variable/geist";
import App from "./App.vue";
import router from "./router";
import "./assets/main.css";

const app = createApp(App);

app.use(router);

app.mount("#app");
