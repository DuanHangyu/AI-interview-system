import request from "@/utils/request";

// 登录
export function loginApi(data: Record<string, any>) {
  return request({
    url: "/login",
    method: "post",
    data,
  });
}

// 获取用户详情
export function getUserInfo() {
  return request({
    url: "/user-detail",
    method: "get",
  });
}

// 上传文件
export function uploadFile(data: Record<string, any>) {
  return request({
    url: "/file/upload",
    method: "post",
    data,
  });
}

// 获取文件签名URL
export function getFileSign(params: Record<string, any>) {
  return request({
    url: "/file/signed-url",
    method: "get",
    params,
  });
}

// 生成上传URL
export function generateUploadUrl(data: Record<string, any>) {
  return request({
    url: "/file/generate-upload-url",
    method: "get",
    data,
  });
}

// 预览文件
export function previewFile(fileUrl: string) {
  return request({
    url: "/file/preview",
    method: "get",
    params: { fileUrl },
  });
}

// 修改密码
export function changePassword(data: Record<string, any>) {
  return request({
    url: "/change-password",
    method: "post",
    data,
  });
}
