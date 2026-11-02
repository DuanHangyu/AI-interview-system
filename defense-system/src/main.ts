import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { createPinia } from "pinia";
import "ant-design-vue/dist/reset.css";
import "./assets/styles/global.css";
import Antd, { message } from "ant-design-vue";
import "./router/permission";

const app = createApp(App);

message.config({ duration: 3, maxCount: 3 });
app.use(createPinia());
app.use(Antd);
app.use(router);
app.mount("#app");
