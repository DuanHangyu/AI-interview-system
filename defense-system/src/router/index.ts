import { createRouter, createWebHistory } from "vue-router";
import { Layout } from "@/components/Layout";
import StudentContainter from "@/components/Layout/componets/StudentContainter.vue";

const routes = [
  {
    path: "/login",
    name: "Login",
    component: () => import(/* webpackPreload: true */ "@/views/Login.vue"),
  },
  {
    path: "/:pathMatch(.*)*",
    component: () =>
      import(/* webpackPrefetch: true */ "@/views/error/404.vue"),
    hidden: true,
  },
];

export const studnetRoutes = [
  {
    path: "/",
    component: StudentContainter,
    redirect: "/studentDashboard",
    children: [
      {
        path: "/dashboard",
        name: "Dashboard",
        component: () =>
          import(/* webpackPreload: true */ "@/views/studentDashboard/defense/index2.vue"),
        meta: { role: ["student"] },
      },
      {
        path: "/question",
        name: "QuestionPage",
        component: () =>
          import(/* webpackPrefetch: true */ "@/views/studentDashboard/questions/index2.vue"),
          // import(/* webpackPrefetch: true */ "@/views/QuestionPage.vue"),
        meta: { role: ["student"] },
      },
      {
        path: "/result",
        name: "ResultPage",
        component: () =>
          import(/* webpackPrefetch: true */ "@/views/ResultPage.vue"),
        meta: { role: ["student"] },
      },
      {
        path: "/resultOver",
        name: "ResultOverPage",
        component: () =>
          import(/* webpackPrefetch: true */ "@/views/ResultOverPage.vue"),
        meta: { role: ["student"] },
      },
      {
        path: "/studentDashboard",
        name: "StudentDashboard",
        component: () =>
          import(
            /* webpackPrefetch: true */ "@/views/studentDashboard/index.vue"
          ),
        meta: { role: ["student"] },
      },
      {
        path: "/studyDetail",
        name: "StudentStudyDetail",
        component: () =>
          import(
            /* webpackPrefetch: true */ "@/views/studentDashboard/studyDetail/index.vue"
          ),
        meta: { role: ["student"] },
      },
    ],
  },
];

export const teacherRoutes = [
  {
    path: "/",
    component: Layout,
    redirect: "/teacherDashboard",
    children: [
      {
        path: "/teacherDashboard",
        name: "TeacherDashboard",
        component: () =>
          import(
            /* webpackPreload: true */ "@/views/teacherDashboard/index.vue"
          ),
        meta: { role: ["teacher"] },
      },
    ],
  },
  {
    path: "/studyDetail",
    name: "StudyDetail",
    component: () =>
      import(/* webpackPrefetch: true */ "@/views/studyDetail/index.vue"),
    meta: { role: ["teacher"] },
  },
];

export const adminRoutes = [
  {
    path: "/",
    component: Layout,
    redirect: "/adminDashboard",
    children: [
      {
        path: "/adminDashboard",
        name: "AdminDashboard",
        component: () =>
          import(/* webpackPreload: true */ "@/views/adminDashboard/home.vue"),
        meta: { role: ["admin"] },
      },
    ],
  },
  {
    path: "/studyDetail",
    name: "StudyDetail",
    component: () =>
      import(/* webpackPrefetch: true */ "@/views/studyDetail/index.vue"),
    meta: { role: ["admin"] },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    return { top: 0 };
  },
});

export default router;
