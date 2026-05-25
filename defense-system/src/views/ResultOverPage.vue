<template>
  <div class="size-full px-32 pt-6">
    <div class="card-box">
      <img src="../assets/resultover.png" alt="" class="noImg" />
      <p v-if="isReady" class="text-2xl font-bold mt-4">考核已完成</p>
      <p v-if="isReady" class="text-base mt-4">
        <Button type="primary" @click="$router.push('/')">返回主界面</Button>
      </p>
    </div>
    <!-- 加载遮罩层 -->
    <LoadingOverlay v-if="!isReady" text="加载中，请稍后..." />
  </div>
</template>
<script setup>
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import LoadingOverlay from "@/components/LoadingOverlay/index.vue";
import { Button } from "ant-design-vue";

const isReady = ref(false);
const route = useRoute();

onMounted(() => {
  isReady.value = true;
});
</script>
<style scoped>
.card-box {
  border: 2px solid #ccc; /* 边框 */
  border-radius: 10px; /* 圆角可选 */
  width: 100%; /* 减去左右 margin */
  height: calc(100% - 200px); /* 减去上下 margin */
  box-sizing: border-box; /* 边框和内边距包含在 width/height 中 */
  display: flex;
  flex-direction: column;
  align-items: center;
  background-color: rgba(255, 255, 255, 0.6);
  justify-content: center; /* 垂直居中内容 */
  text-align: center;
  overflow: hidden;
}
.noImg {
  width: 280px;
}

.horizontal-container {
  display: flex;
  align-items: center; /* 垂直居中对齐 */
  gap: 8px; /* 元素之间间距，也可以用 margin 代替 */
  margin-left: 12px;
}

.rectangle {
  width: 10px;
  height: 35px;
  background-color: #409eff; /* 任意颜色 */
}

/* 左侧分析 */
.left-report {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin: 0px 0px 20px 0px;
}

/* 右侧成绩 */
.right-chart {
  width: 500px;
  display: flex;
  flex-direction: column;
  margin: 0px 0px 20px 20px;
}

/* 卡片统一样式 */
.chart-group {
  flex: 1;
  background-color: white;
  border-radius: 30px;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  padding: 20px;
  display: flex;
  flex-direction: column;
  margin: 6px;
  box-sizing: border-box;
  overflow: hidden;
}

/* 左侧滚动区域 */
.chart-group.scrollable {
  overflow-y: auto;
}

/* 加载遮罩层 */
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(2px);
  z-index: 9999;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  pointer-events: none;
}

/* 加载动画 */
.dot-spinner {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
}

.dot-spinner .dot {
  width: 12px;
  height: 12px;
  background-color: #409eff;
  border-radius: 50%;
  animation: dotPulse 1.4s infinite ease-in-out;
}

.dot-spinner .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot-spinner .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dotPulse {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.6;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

pre {
  font-size: 16px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
