import { defineStore } from "pinia";
import { getToken, removeToken, setToken } from "@/utils/auth";
import { getUserInfo, loginApi } from "@/api/common";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: getToken(),
    id: "",
    avatar: "",
    info: {} as Record<string, any>,
    role: ''
  }),

  actions: {
    // 登录
    login(userInfo: Record<string, any>) {
      const account = userInfo.account.trim();
      const password = userInfo.password;
      return new Promise<void>((resolve, reject) => {
        loginApi({
          account,
          password,
        })
          .then((res) => {
            setToken(res.data);
            this.token = res.data;
            resolve();
          })
          .catch((error) => {
            reject(error);
          });
      });
    },
    // 获取用户信息
    getInfo() {
      return new Promise((resolve, reject) => {
        getUserInfo()
          .then((res) => {
            const user = res.data;
            this.id = user.id;
            this.info = user;
            this.role = user?.role
            resolve(res);
          })
          .catch((error) => {
            reject(error);
          });
      });
    },
    logout() {
      return new Promise<void>((resolve, reject) => {
        this.token = "";
        removeToken();
        resolve()
      });
    },
  },
});
