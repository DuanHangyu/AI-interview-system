import request from "@/utils/request";

// 获取学习记录列表
export function getStudyRecordList(data: Record<string, any>) {
  return request({
    url: "/backend/study-record/list",
    method: "post",
    data,
  });
}

// 修改学生记录得分
export function changeScore(data: Record<string, any>) {
  return request({
    url: "/backend/study-record/change-score",
    method: "post",
    data,
  });
}

// 获取考核信息
export function getStudyDetail(params: Record<string, any>) {
  return request({
    url: "/backend/study-record/assessment-detail",
    method: "get",
    params,
  });
}

// 导出学习记录
export function exportStudentRecord(data: Record<string, any>) {
  return request({
    url: "/backend/study-record/export-record",
    method: "post",
    data,
    responseType: "blob",
  });
}

// 重新考核
export function retakeApi(data: Record<string, any>) {
  return request({
    url: "/backend/study-record/retake",
    method: "post",
    data,
  });
}
// 重新评分（重新调用 AI 分析）
export function reanalyzeApi(data: Record<string, any>) {
  return request({
    url: "/backend/study-record/reanalyze",
    method: "post",
    data,
  });
}

// 教师复核：按维度调整分数并填写评语
export function reviewRecord(data: {
  recordId: number;
  dimensions: { name: string; score: number }[];
  comment?: string;
}) {
  return request({
    url: "/backend/study-record/review",
    method: "post",
    data,
  });
}

// 按筛选条件返回全部记录 id（批量打印报告、报告翻页）
export function getStudyRecordIds(data: Record<string, any>) {
  return request({
    url: "/backend/study-record/ids",
    method: "post",
    data,
  });
}
