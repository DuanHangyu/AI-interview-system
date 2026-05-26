import axios from "axios";
import { notification, message, Modal } from "ant-design-vue";
import { getToken } from "@/utils/auth";
import errorCode from "@/utils/errorCode";

import { useAuthStore } from "@/stores/auth";
// 是否显示重新登录
export let isRelogin = { show: false };
const DATABASE_CONNECTION_ERROR =
  "数据库连接失败，请检查本地数据库隧道或 DB_JDBC_URL 配置";
const DB_RETRY_DELAY = 1500;
const DB_RETRY_LIMIT = 2;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function retryDatabaseRequest(config: any) {
  const retryCount = config.__dbRetryCount || 0;
  if (retryCount >= DB_RETRY_LIMIT) {
    return null;
  }
  config.__dbRetryCount = retryCount + 1;
  return sleep(DB_RETRY_DELAY).then(() => service(config));
}

// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: process.env.VUE_APP_BASE_API,
  // 超时
  timeout: 30000,
  headers: {
    "Content-Type": "application/json;charset=utf-8",
  },
});

// request拦截器
service.interceptors.request.use(
  (config) => {
    // 是否需要设置 token
    // const isToken = (config.headers || {}).isToken === false;
    // if (getToken() && !isToken) {
    if (getToken()) {
      config.headers["Authorization"] = `Bearer ${getToken()}`;
    }
    if (typeof config?.data == "object") {
      for (const key in config.data) {
        if (Object.prototype.hasOwnProperty.call(config.data, key)) {
          const element = config.data?.[key];
          if (!element && typeof element != "number" && element !== false)
            delete config.data?.[key];
        }
      }
    }
    return config;
  },
  (error) => {
    console.log(error);
    Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (res) => {
    // 未设置状态码则默认成功状态
    const code = res.data.code || 200;
    // 获取错误信息
    const msg =
      res.data.message ||
      errorCode[code as keyof typeof errorCode] ||
      errorCode["default"];
    // 二进制数据则直接返回
    if (
      res.request.responseType === "blob" ||
      res.request.responseType === "arraybuffer"
    ) {
      return res;
    }
    if (code === 401) {
      if (!isRelogin.show) {
        isRelogin.show = true;
        Modal.confirm({
          title: "系统提示",
          content: "登录状态已过期，您可以继续留在该页面，或者重新登录",
          okText: "重新登录",
          cancelText: "取消",
          type: "warning",
          onOk: () => {
            isRelogin.show = false;
            useAuthStore().logout();
            window.location.href = "/login";
          },
        });
      }
      return Promise.reject("无效的会话，或者会话已过期，请重新登录。");
    } else if (code === 500 && msg === DATABASE_CONNECTION_ERROR) {
      const retryResponse = retryDatabaseRequest(res.config);
      if (retryResponse) {
        return retryResponse;
      }
      message.error({
        duration: 3,
        content: msg,
      });
      return Promise.reject(new Error(msg));
    } else if (code === 500 || code == 400) {
      message.error({
        duration: 3,
        content: msg,
      });
      return Promise.reject(new Error(msg));
    } else if (code === 601) {
      message.warn({
        duration: 3,
        content: msg,
      });
      return Promise.reject(new Error(msg));
    } else if (code !== 200) {
      notification.error({ message: msg });
      return Promise.reject("error");
    } else {
      return Promise.resolve(res.data);
    }
  },
  (error) => {
    console.log("err" + error);
    let { message: msg } = error;
    if (msg == "Network Error") {
      msg = "后端接口连接异常";
    } else if (msg.includes("timeout")) {
      msg = "系统接口请求超时";
    } else if (msg.includes("Request failed with status code")) {
      msg = "系统接口" + msg.substr(msg.length - 3) + "异常";
    }
    message.error({
      duration: 3,
      content: msg,
    });
    return Promise.reject(error);
  }
);

export default service;
