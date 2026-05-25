import request from "@/utils/request";

// 获取项目类型列表
export function getProjectTypeList() {
    return request({
        url: "/project-type/list",
        method: "get"
    });
}


// 创建项目类型
export function createProjectType(data: Recordable) {
    return request({
        url: "/project-type/create",
        method: "post",
        data
    });
}


// 删除项目类型
export function deleteProjectType(data: Recordable) {
    return request({
        url: "/project-type/delete",
        method: "post",
        data
    });
}

