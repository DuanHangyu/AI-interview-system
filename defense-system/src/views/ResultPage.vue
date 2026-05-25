<template>
  <div class="size-full flex space-x-3">
    <div class="right-chart">
      <!-- <div class="horizontal-container">
        <div class="rectangle"></div>
        <h3>总成绩</h3>
      </div> -->
      <div class="chart-group">
        <ScoreGauge :score="score" />
        <ScoreProgress :score="score" />
      </div>
      <div class="chart-group">
        <AbilityPie :abilityData="abilityData" />
      </div>
    </div>

    <!-- 左侧分析与建议 -->
    <div class="left-report">
      <!-- <div class="horizontal-container">
        <div class="rectangle"></div>
        <h3>分析与建议</h3>
      </div> -->
      <div class="chart-group scrollable">
        <pre>{{ reportText }}</pre>
      </div>
    </div>

    <!-- 加载遮罩层 -->
    <div v-if="!isReady" class="loading-overlay">
      <div class="dot-spinner">
        <div class="dot"></div>
        <div class="dot"></div>
        <div class="dot"></div>
      </div>
      <span style="margin-top: 16px">{{ loadingText }}</span>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from "vue";
import axios from "axios";
import { useRoute } from "vue-router";
import AbilityPie from "@/components/AbilityPie.vue";
import ScoreGauge from "@/components/ScoreGauge.vue";
import ScoreProgress from "@/components/ScoreProgress.vue";
import { useAuthStore } from "@/stores/auth";
import { useRouter } from "vue-router";

const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();
const studentId = Number(route.query.id);
const resultId = Number(route.query.resultId);

const reportText = ref("正在加载报告...");
const isReady = ref(false);
const loadingText = ref("评估中，请稍后...");
const abilityData = ref([]);
const score = ref(0);

const logout = async () => {
  authStore.logout();
  router.push({ name: "Login" });
};

onMounted(async () => {
  try {
    if (localStorage.getItem("role") === "teacher") {
      loadingText.value = "加载中，请稍后...";
    }

    const response = await axios.post(
      `${process.env.VUE_APP_API_BASE_URL}/api/Result/result`,
      {
        userId: studentId,
        studentDefenseId: resultId,
      }
    );
    score.value = response.data.Score;
    reportText.value = response.data.Result;
    const result = response.data.Value;

    abilityData.value = Object.entries(result).map(([key, value]) => ({
      name: key,
      value,
    }));
    console.log(abilityData.value);

    isReady.value = true;
  } catch (error) {
    console.error("生成失败：", error);
  }
});
</script>
<style scoped>
.noImg {
  width: 180px;
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
}

/* 右侧成绩 */
.right-chart {
  width: 500px;
  display: flex;
  flex-direction: column;
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
