import request from "@/utils/request";
import { AxiosProgressEvent } from "axios";

// 上传答辩文件
export function uploadAssessmentFile(data: any) {
  return request({
    url: "/front/student-assessment/upload-file",
    method: "post",
    data,
  });
}

// 获取考核信息
export function getStudentAssessmentDetail(params: any) {
  return request({
    url: "/front/student-assessment/assessment-detail",
    method: "get",
    params,
  });
}

