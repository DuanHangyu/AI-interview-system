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

// 视觉规范参照大连理工大学官网：大工蓝 #0041b7 + 白色，直角、细边框、少装饰
const theme: ThemeConfig = {
  token: {
    colorPrimary: "#0041b7",
    colorInfo: "#0041b7",
    colorLink: "#0041b7",
    colorSuccess: "#1a8f3c",
    colorWarning: "#d48806",
    colorError: "#cf1322",
    colorText: "#222222",
    colorTextSecondary: "#555555",
    colorTextTertiary: "#888888",
    colorBorder: "#d9dee6",
    colorBorderSecondary: "#e8ebf0",
    colorBgLayout: "#f2f4f7",
    borderRadius: 2,
    borderRadiusLG: 2,
    fontSize: 14,
    fontFamily: '"Microsoft YaHei", "PingFang SC", -apple-system, BlinkMacSystemFont, "Helvetica Neue", Arial, sans-serif',
  },
  components: {
    Table: {
      headerBg: "#f3f6fa",
      headerColor: "#333333",
      rowHoverBg: "#f5f8fd",
    },
    Button: {
      primaryShadow: "none",
      defaultShadow: "none",
      dangerShadow: "none",
    },
  },
};
</script>
