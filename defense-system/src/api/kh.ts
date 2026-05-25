import request from "@/utils/request";

// 开始答辩
export function startDefense(data: Record<string, any>) {
  return request({
    url: "/front/student-assess-process/start-defense",
    method: "post",
    data,
  });
}

// 开始考核
export function startAssessmentApi(data: Record<string, any>) {
  return request({
    url: "/front/student-assess-process/start-assessment",
    method: "post",
    data,
  });
}
// 开始答题
export function startAnswer(data: Record<string, any>) {
  return request({
    url: "/front/student-assess-process/start-answer",
    method: "post",
    data,
  });
}
// 生成问题
export function generateQuestionApi(data: Record<string, any>) {
  return request({
    url: "/front/student-assess-process/generate-question",
    method: "post",
    data,
  });
}
// 结束答辩
export function endDefense(data: Record<string, any>) {
  return request({
    url: "/front/student-assess-process/end-defense",
    method: "post",
    data,
  });
}
// 结束考核
export function endAssessment(data: Record<string, any>) {
  return request({
    url: "/front/student-assess-process/end-assessment",
    method: "post",
    data,
  });
}
// 结束答题
export function endAnswer(data: Record<string, any>) {
  return request({
    url: "/front/student-assess-process/end-answer",
    method: "post",
    data,
  });
}
