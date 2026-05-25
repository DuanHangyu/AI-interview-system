import router, { adminRoutes, studnetRoutes, teacherRoutes } from "./index";
import { message } from 'ant-design-vue';
import NProgress from 'nprogress';
import "nprogress/nprogress.css";
import { getToken } from "@/utils/auth";
import { isPathMatch } from "@/utils/validate";
import { isRelogin } from "../utils/request";
import useSettingsStore from "@/stores/settings";
import { useAuthStore } from "@/stores/auth";

NProgress.configure({ showSpinner: false });

const whiteList = ["/login"];

const isWhiteList = (path: string) => {
  return whiteList.some((pattern) => isPathMatch(pattern, path));
};

router.beforeEach((to, from, next) => {
  NProgress.start();
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta?.title as string);
    /* has token*/
    if (to.path === "/login") {
      next({ path: "/" });
      NProgress.done();
    } else if (isWhiteList(to.path)) {
      next();
    } else {
      if (!useAuthStore().id) {
        isRelogin.show = true;
        // 判断当前用户是否已拉取完user_info信息
        useAuthStore()
          .getInfo()
          .then((e: any) => {
            if (e?.data?.role == 'ADMIN') {
              for (let index = 0; index < adminRoutes.length; index++) {
                const element = adminRoutes[index];
                router.addRoute(element)
              }
            }
            if (e?.data?.role == 'TEACHER') {
              for (let index = 0; index < teacherRoutes.length; index++) {
                const element = teacherRoutes[index];
                router.addRoute(element)
              }
            }
            if (e?.data?.role == 'STUDENT') {
              for (let index = 0; index < studnetRoutes.length; index++) {
                const element = studnetRoutes[index];
                router.addRoute(element)
              }
            }
            isRelogin.show = false;
            next({ ...to, replace: true });
          })
          .catch((err) => {
            useAuthStore()
              .logout()
              .then(() => {
                message.error(err);
                next({ path: "/" });
              });
          });
      } else {
        next();
      }
    }
  } else {
    // 没有token
    if (isWhiteList(to.path)) {
      // 在免登录白名单，直接进入
      next();
    } else {
      next(`/login?redirect=${to.fullPath}`); // 否则全部重定向到登录页
      NProgress.done();
    }
  }
});

router.afterEach(() => {
  NProgress.done();
});
