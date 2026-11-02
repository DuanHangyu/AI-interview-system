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

const theme: ThemeConfig = {
  token: {
    colorPrimary: "#3370ff",
    colorSuccess: "#00b42a",
    colorWarning: "#ff7d00",
    colorError: "#f53f3f",
    colorText: "#1f2329",
    colorTextSecondary: "#4e5969",
    colorBorder: "#e5e6eb",
    colorBorderSecondary: "#f0f1f2",
    colorBgLayout: "#f5f6f7",
    borderRadius: 6,
    fontSize: 14,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif',
  },
};
</script>
