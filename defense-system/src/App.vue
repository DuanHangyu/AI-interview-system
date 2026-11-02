<template>
  <ConfigProvider :locale="zhCN" :theme="theme">
    <router-view />
  </ConfigProvider>
</template>

<script setup lang="ts">
import zhCN from "ant-design-vue/es/locale/zh_CN";
import { ConfigProvider } from "ant-design-vue";
import type { ThemeConfig } from "ant-design-vue/es/config-provider/context";
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";

dayjs.locale("zh-cn");

// 视觉规范取自大连理工大学马克思主义学院官网：主色为学院红 #c3192a，辅以暖金色点缀
const theme: ThemeConfig = {
  token: {
    colorPrimary: "#c3192a",
    colorInfo: "#c3192a",
    colorLink: "#b4161f",
    colorSuccess: "#16a34a",
    colorWarning: "#d97706",
    colorError: "#dc2626",
    colorText: "#1f1d1b",
    colorTextSecondary: "#57534e",
    colorTextTertiary: "#8a847d",
    colorBorder: "#e7e2dc",
    colorBorderSecondary: "#f1ede8",
    colorBgLayout: "#f6f4f1",
    borderRadius: 6,
    controlHeight: 34,
    fontSize: 14,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif',
  },
  components: {
    Menu: {
      itemSelectedBg: "#fbeceb",
      itemSelectedColor: "#c3192a",
      itemHoverBg: "#f7f2ee",
      itemBorderRadius: 6,
      itemHeight: 42,
    },
    Table: {
      headerBg: "#f8f5f1",
      headerColor: "#57534e",
      rowHoverBg: "#fdf8f6",
    },
    Button: {
      primaryShadow: "0 2px 6px rgba(195, 25, 42, 0.22)",
    },
  },
};
</script>
