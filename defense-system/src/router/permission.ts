import router from "./index";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { getToken } from "@/utils/auth";
import { useAuthStore } from "@/stores/auth";
import { homeFor } from "./menus";

NProgress.configure({ showSpinner: false });

const APP_TITLE = "AI 面试评估";

router.beforeEach(async (to) => {
  NProgress.start();
  if (to.meta.public && to.name !== "Login") return true;
  if (!getToken()) {
    return to.name === "Login" ? true : { path: "/login", query: { redirect: to.fullPath } };
  }
  const auth = useAuthStore();
  if (!auth.role) {
    try {
      await auth.getInfo();
    } catch (e) {
      await auth.logout();
      return { path: "/login" };
    }
  }
  const home = homeFor(auth.role);
  if (to.name === "Login" || to.path === "/") return home;
  const roles = to.meta.roles as string[] | undefined;
  if (roles && !roles.includes(auth.role)) return home;
  return true;
});

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - ${APP_TITLE}` : APP_TITLE;
  NProgress.done();
});
