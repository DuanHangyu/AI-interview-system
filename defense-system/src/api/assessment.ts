import request from "@/utils/request";

// 删除考核
export function removeAssessment(data: Record<string, any>) {
  return request({
    url: "/backend/assessment/remove",
    method: "post",
    data,
  });
}

// 修改考核
export function modifyAssessment(data: Record<string, any>) {
  return request({
    url: "/backend/assessment/modify",
    method: "post",
    data,
  });
}

// 获取考核设置列表
export function getAssessmentList(data: Record<string, any>) {
  return request({
    url: "/backend/assessment/list",
    method: "post",
    data,
  });
}

// 创建考核
export function createAssessment(data: Record<string, any>) {
  return request({
    url: "/backend/assessment/create",
    method: "post",
    data,
  });
}

// AI 生成题目草稿（耗时较长）
export function generateQuestionDraft(data: Record<string, any>) {
  return request({
    url: "/backend/assessment/question/ai-draft",
    method: "post",
    data,
    timeout: 120000,
  });
}

// 获取考核信息
export function getAssessmentDetail(assessmentId: number) {
  return request({
    url: "/assessment/detail",
    method: "get",
    params: { assessmentId },
  });
}

