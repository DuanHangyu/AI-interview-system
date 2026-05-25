import { useDynamicTitle } from "@/utils/dynamicTitle";
import { defineStore } from "pinia";

const dynamicTitle = process.env.VUE_APP_TITLE;

const storageSetting = JSON.parse(localStorage.getItem("layout-setting") as string) || "";

const useSettingsStore = defineStore("settings", {
  state: () => ({
    title: "",
    dynamicTitle:
      storageSetting.dynamicTitle === undefined
        ? dynamicTitle
        : storageSetting.dynamicTitle,
  }),
  actions: {
    // 修改布局设置
    changeSetting(data: { key: string; value: string }) {
      const { key, value } = data;
      if (this.hasOwnProperty(key)) {
        this[key as 'title' | 'dynamicTitle'] = value;
      }
    },
    // 设置网页标题
    setTitle(title: string) {
      this.title = title;
      useDynamicTitle();
    },
  },
});

export default useSettingsStore;
