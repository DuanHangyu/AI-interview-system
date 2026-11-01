import request from "@/utils/request";

// 获取项目类型列表
export function getProjectTypeList() {
    return request({
        url: "/project-type/list",
        method: "get"
    });
}

