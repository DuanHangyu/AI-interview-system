import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import AppLayout from "@/layouts/AppLayout.vue";

const STUDENT = ["STUDENT"];
const ADMIN = ["ADMIN"];
const STAFF = ["TEACHER", "ADMIN"];

const routes: RouteRecordRaw[] = [
  { path: "/login", name: "Login", component: () => import("@/views/Login.vue"), meta: { public: true } },
  {
    path: "/interview",
    name: "InterviewRoom",
    component: () => import("@/views/student/InterviewRoom.vue"),
    meta: { roles: STUDENT, title: "面试中" },
  },
  {
    path: "/",
    component: AppLayout,
    children: [
      {
        path: "student/interviews",
        component: () => import("@/views/student/MyInterviews.vue"),
        meta: { roles: STUDENT, title: "我的面试" },
      },
      {
        path: "student/reports",
        component: () => import("@/views/student/MyReports.vue"),
        meta: { roles: STUDENT, title: "面试报告" },
      },
      {
        path: "student/reports/:assessmentId",
        component: () => import("@/views/report/StudentReport.vue"),
        meta: { roles: STUDENT, title: "面试报告", menu: "/student/reports" },
      },
      {
        path: "records",
        component: () => import("@/views/teacher/Records.vue"),
        meta: { roles: STAFF, title: "面试记录" },
      },
      {
        path: "records/print",
        component: () => import("@/views/report/BatchPrint.vue"),
        meta: { roles: STAFF, title: "批量打印报告", menu: "/records" },
      },
      {
        path: "records/:recordId",
        component: () => import("@/views/report/TeacherReport.vue"),
        meta: { roles: STAFF, title: "面试报告", menu: "/records" },
      },
      {
        path: "assessments",
        component: () => import("@/views/teacher/Assessments.vue"),
        meta: { roles: STAFF, title: "面试管理" },
      },
      {
        path: "assessments/new",
        component: () => import("@/views/teacher/AssessmentEditor.vue"),
        meta: { roles: STAFF, title: "新建面试", menu: "/assessments" },
      },
      {
        path: "assessments/:id/edit",
        component: () => import("@/views/teacher/AssessmentEditor.vue"),
        meta: { roles: STAFF, title: "编辑面试", menu: "/assessments" },
      },
      {
        path: "assessments/:id/exam",
        component: () => import("@/views/teacher/ExamMonitor.vue"),
        meta: { roles: STAFF, title: "考场", menu: "/assessments" },
      },
      {
        path: "students",
        component: () => import("@/views/manage/Students.vue"),
        meta: { roles: STAFF, title: "学生管理" },
      },
      {
        path: "teachers",
        component: () => import("@/views/manage/Teachers.vue"),
        meta: { roles: ADMIN, title: "教师管理" },
      },
    ],
  },
  { path: "/:pathMatch(.*)*", name: "NotFound", component: () => import("@/views/NotFound.vue"), meta: { public: true } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to, from, saved) => saved || { top: 0 },
});

export default router;
