import request from "@/utils/request";

export interface ExamSession {
  id?: number;
  startTime: string;
  endTime: string;
  location?: string | null;
  capacity?: number | null;
  assignedCount?: number;
}

export type ExamStatus =
  | "UNSCHEDULED"
  | "WAITING"
  | "OPEN"
  | "READY"
  | "MISSED"
  | "IN_PROGRESS"
  | "ANALYZING"
  | "DONE";

export interface ExamStudentRow {
  studentId: number;
  name: string;
  account: string;
  schoolClass?: string;
  sessionId?: number | null;
  sessionStart?: string | null;
  sessionEnd?: string | null;
  location?: string | null;
  status: ExamStatus;
  statusLabel: string;
  retake?: boolean;
  startTime?: string | null;
  lastActiveTime?: string | null;
  progress?: string | null;
  answeredSteps?: number | null;
  totalSteps?: number | null;
  recordId?: number | null;
  finalScore?: number | null;
  passed?: boolean | null;
  reviewed?: boolean | null;
}

export interface ExamMonitor {
  assessmentId: number;
  theme: string;
  bySession: boolean;
  totalScore?: number;
  passScore?: number;
  /** 学生能否查看成绩报告 */
  showResult?: boolean;
  serverTime: string;
  sessions: ExamSession[];
  counts: Record<ExamStatus, number>;
  students: ExamStudentRow[];
}

export interface MyInterview {
  assessmentId: number;
  theme: string;
  defense?: boolean;
  question?: boolean;
  questionCount?: number;
  answerTime?: number;
  duration?: number;
  totalScore?: number;
  status: ExamStatus;
  statusLabel: string;
  retake?: boolean;
  sessionStart?: string | null;
  sessionEnd?: string | null;
  location?: string | null;
  canEnter: boolean;
  canStart: boolean;
  showResult?: boolean;
}

// 场次列表
export function getExamSessions(assessmentId: number) {
  return request({ url: "/backend/exam/sessions", method: "get", params: { assessmentId } });
}

// 保存全部场次（未出现的场次会被删除）
export function saveExamSessions(assessmentId: number, sessions: ExamSession[]) {
  return request({ url: "/backend/exam/sessions/save", method: "post", data: { assessmentId, sessions } });
}

// 安排学生到场次，sessionId 为空表示取消安排
export function assignExamSession(assessmentId: number, sessionId: number | null, studentIds: number[]) {
  return request({ url: "/backend/exam/assign", method: "post", data: { assessmentId, sessionId, studentIds } });
}

// 把未安排的学生依次分到各场次
export function autoAssignExam(assessmentId: number) {
  return request({ url: "/backend/exam/auto-assign", method: "post", data: { assessmentId } });
}

// 考场监控
export function getExamMonitor(assessmentId: number) {
  return request({ url: "/backend/exam/monitor", method: "get", params: { assessmentId } });
}

// 向学生公布或收回成绩报告
export function setExamShowResult(assessmentId: number, showResult: boolean) {
  return request({ url: "/backend/exam/show-result", method: "post", data: { assessmentId, showResult } });
}

// 学生：我的面试
export function getMyInterviews() {
  return request({ url: "/front/exam/my-interviews", method: "get" });
}
