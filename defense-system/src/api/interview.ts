import request from "@/utils/request";

export type InterviewStatus =
  | "NOT_STARTED"
  | "DEFENSE"
  | "QUESTIONS"
  | "READY_TO_SUBMIT"
  | "SUBMITTED";

export interface InterviewQuestion {
  index: number;
  followUp: boolean;
  /** 0 为主问题，1、2… 为第几个追问 */
  followIndex: number;
  /** 本题的追问总数 */
  followTotal: number;
  content: string;
}

export interface InterviewState {
  assessmentId: number;
  theme: string;
  defenseEnabled: boolean;
  questionEnabled: boolean;
  followUpEnabled: boolean;
  duration: number;
  answerTime: number;
  status: InterviewStatus;
  defenseDone: boolean;
  total: number;
  answeredCount: number;
  current: InterviewQuestion | null;
  serverTime?: string;
  /** 分场次考试、尚未开始时：学生所在场次 */
  sessionStart?: string | null;
  sessionEnd?: string | null;
  /** 暂时不能开始的原因（未到场次时间等），为空表示可以开始 */
  startBlockedReason?: string | null;
}

// 录音上传可能较大，给足超时时间
const UPLOAD_TIMEOUT = 120000;

// 获取面试状态（刷新页面后据此恢复）
export function getInterviewState(assessmentId: number | string) {
  return request({
    url: "/front/interview/state",
    method: "get",
    params: { assessmentId },
  });
}

// 开始面试（幂等：已开始则直接返回当前状态，服务端完成抽题）
export function startInterview(assessmentId: number | string) {
  return request({
    url: "/front/interview/start",
    method: "post",
    data: { assessmentId },
  });
}

// 上传答辩录音
export function uploadDefenseAudio(
  assessmentId: number | string,
  audio: Blob,
  onProgress?: (percent: number) => void
) {
  const form = new FormData();
  form.append("assessmentId", String(assessmentId));
  form.append("audio", audio, "defense.wav");
  return request({
    url: "/front/interview/defense",
    method: "post",
    data: form,
    timeout: UPLOAD_TIMEOUT,
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress: (e) => {
      if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
    },
  });
}

// 上传题目回答录音（同一题位重复上传幂等）
export function uploadAnswerAudio(
  assessmentId: number | string,
  index: number,
  followIndex: number,
  audio: Blob,
  onProgress?: (percent: number) => void
) {
  const form = new FormData();
  form.append("assessmentId", String(assessmentId));
  form.append("index", String(index));
  form.append("followUp", followIndex > 0 ? "true" : "false");
  form.append("followIndex", String(followIndex));
  form.append("audio", audio, `answer-${index}${followIndex > 0 ? `-follow${followIndex}` : ""}.wav`);
  return request({
    url: "/front/interview/answer",
    method: "post",
    data: form,
    timeout: UPLOAD_TIMEOUT,
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress: (e) => {
      if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
    },
  });
}

// 提交面试，进入评估
export function submitInterview(assessmentId: number | string) {
  return request({
    url: "/front/interview/submit",
    method: "post",
    data: { assessmentId },
  });
}

// 获取题目播报音频（wav 二进制）
export function getQuestionAudio(
  assessmentId: number | string,
  index: number,
  followIndex: number
) {
  return request({
    url: "/front/interview/question-audio",
    method: "get",
    params: { assessmentId, index, followUp: followIndex > 0, followIndex },
    responseType: "blob",
    timeout: 8000,
  });
}
