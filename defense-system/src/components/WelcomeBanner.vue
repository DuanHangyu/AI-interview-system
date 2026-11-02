<template>
  <section class="welcome">
    <img :src="emblemWhite" alt="" class="welcome-emblem" />
    <div class="welcome-body">
      <div class="welcome-date">{{ today }}</div>
      <h1 class="welcome-title">{{ greeting }}，{{ name }}</h1>
      <p class="welcome-desc"><slot>欢迎使用{{ BRAND.college }}{{ BRAND.product }}</slot></p>
    </div>
    <div class="welcome-actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import dayjs from "dayjs";
import { useAuthStore } from "@/stores/auth";
import { BRAND } from "@/config/brand";
import emblemWhite from "@/assets/brand/emblem-white.svg";

const props = defineProps<{ suffix?: string }>();
const auth = useAuthStore();

const name = computed(() => (auth.info?.name || auth.info?.account || "") + (props.suffix || ""));
const today = dayjs().format("YYYY年M月D日 dddd");
const greeting = computed(() => {
  const h = dayjs().hour();
  if (h < 6) return "夜深了";
  if (h < 11) return "上午好";
  if (h < 13) return "中午好";
  if (h < 18) return "下午好";
  return "晚上好";
});
</script>

<style scoped>
.welcome {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
  padding: 26px 32px;
  border-radius: 12px;
  color: #fff;
  background:
    linear-gradient(115deg, transparent 0 62%, rgba(255, 255, 255, 0.08) 62% 68%, transparent 68% 74%,
      rgba(255, 255, 255, 0.06) 74% 77%, transparent 77%),
    radial-gradient(circle at 0% 0%, rgba(255, 210, 160, 0.22), transparent 50%),
    linear-gradient(120deg, #a5101e 0%, #c3192a 55%, #9b0a18 100%);
  box-shadow: 0 8px 24px rgba(155, 10, 24, 0.18);
  overflow: hidden;
}

.welcome::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--c-gold), rgba(201, 160, 99, 0));
}

.welcome-emblem {
  position: absolute;
  right: 120px;
  top: 50%;
  width: 220px;
  height: 220px;
  transform: translateY(-50%);
  opacity: 0.08;
  pointer-events: none;
}

.welcome-body {
  position: relative;
  min-width: 0;
}

.welcome-date {
  font-size: 13px;
  letter-spacing: 1px;
  color: #f3d7a8;
}

.welcome-title {
  margin: 6px 0 0;
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 1px;
}

.welcome-desc {
  margin: 6px 0 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.82);
}

.welcome-actions {
  position: relative;
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.welcome-actions :deep(.ant-btn-default) {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.1);
}

.welcome-actions :deep(.ant-btn-default:hover) {
  color: #fff;
  border-color: #fff;
  background: rgba(255, 255, 255, 0.2);
}

@media (max-width: 640px) {
  .welcome {
    flex-direction: column;
    align-items: flex-start;
    padding: 20px;
  }
  .welcome-emblem {
    right: -40px;
  }
}
</style>
