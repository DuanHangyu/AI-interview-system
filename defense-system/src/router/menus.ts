import { markRaw } from "vue";
import {
  AuditOutlined,
  FileTextOutlined,
  ProfileOutlined,
  SolutionOutlined,
  TeamOutlined,
  UserOutlined,
} from "@ant-design/icons-vue";

export interface MenuEntry {
  path: string;
  label: string;
  icon: unknown;
}

export const ROLE_LABEL: Record<string, string> = {
  STUDENT: "学生",
  TEACHER: "教师",
  ADMIN: "管理员",
};

const MENUS: Record<string, MenuEntry[]> = {
  STUDENT: [
    { path: "/student/interviews", label: "我的面试", icon: markRaw(SolutionOutlined) },
    { path: "/student/reports", label: "面试报告", icon: markRaw(FileTextOutlined) },
  ],
  TEACHER: [
    { path: "/records", label: "面试记录", icon: markRaw(AuditOutlined) },
    { path: "/assessments", label: "面试管理", icon: markRaw(ProfileOutlined) },
    { path: "/students", label: "学生管理", icon: markRaw(TeamOutlined) },
  ],
  ADMIN: [
    { path: "/records", label: "面试记录", icon: markRaw(AuditOutlined) },
    { path: "/assessments", label: "面试管理", icon: markRaw(ProfileOutlined) },
    { path: "/students", label: "学生管理", icon: markRaw(TeamOutlined) },
    { path: "/teachers", label: "教师管理", icon: markRaw(UserOutlined) },
  ],
};

export function menusFor(role: string): MenuEntry[] {
  return MENUS[role] || [];
}

export function homeFor(role: string): string {
  return menusFor(role)[0]?.path || "/login";
}
