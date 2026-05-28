<template>
  <main class="result-page">
    <div class="result-page-inner">
      <StudentHeader />

      <section class="result-page-hero">
        <div>
          <div class="result-eyebrow">Interview Analysis</div>
          <h1>综合评估报告</h1>
          <p>按总分、能力维度和文字建议复盘本次面试表现。</p>
        </div>
        <div class="result-score">
          <span>{{ score }}</span>
          <small>总成绩</small>
        </div>
      </section>

      <section class="result-layout">
        <aside class="result-chart-column">
          <section class="result-panel">
            <h2>总成绩</h2>
            <div class="chart-stack">
              <ScoreGauge :score="score" />
              <ScoreProgress :score="score" />
            </div>
          </section>
          <section class="result-panel">
            <h2>能力分布</h2>
            <AbilityPie :abilityData="abilityData" />
          </section>
        </aside>

        <section class="result-panel result-report">
          <h2>分析与建议</h2>
          <div class="report-scroll">
            <pre>{{ reportText }}</pre>
          </div>
        </section>
      </section>
    </div>

    <div v-if="!isReady" class="loading-overlay">
      <div class="dot-spinner">
        <div class="dot"></div>
        <div class="dot"></div>
        <div class="dot"></div>
      </div>
      <span style="margin-top: 16px">{{ loadingText }}</span>
    </div>
  </main>
</template>
<script setup>
import { ref, onMounted } from "vue";
import axios from "axios";
import { useRoute } from "vue-router";
import AbilityPie from "@/components/AbilityPie.vue";
import ScoreGauge from "@/components/ScoreGauge.vue";
import ScoreProgress from "@/components/ScoreProgress.vue";
import StudentHeader from "@/components/Layout/componets/StudentHeader.vue";

const route = useRoute();
const studentId = Number(route.query.id);
const resultId = Number(route.query.resultId);

const reportText = ref("正在加载报告...");
const isReady = ref(false);
const loadingText = ref("评估中，请稍后...");
const abilityData = ref([]);
const score = ref(0);

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
.result-page {
  min-height: 100vh;
  width: 100%;
  padding: 24px;
  overflow-y: auto;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.36), rgba(0, 0, 0, 0.18)),
    linear-gradient(135deg, #eeeeec 0%, #d5d5d2 45%, #7f807d 100%);
}

.result-page-inner {
  width: min(1500px, 100%);
  margin: 0 auto;
}

.result-page-hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 18px;
  margin-top: 16px;
  padding: 28px;
  border: 1px solid var(--color-border-light);
  border-radius: 28px;
  background: var(--color-surface);
  box-shadow:
    0 28px 80px rgba(20, 21, 22, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
}

.result-eyebrow {
  width: fit-content;
  padding: 6px 11px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 800;
}

.result-page-hero h1 {
  margin: 10px 0 8px;
  color: var(--color-text-primary);
  font-size: clamp(30px, 3.6vw, 46px);
  font-weight: 850;
  line-height: 1.08;
}

.result-page-hero p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.result-score {
  min-width: 150px;
  min-height: 112px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(145deg, #444544, #2f302f);
  color: #ffffff;
  box-shadow: 0 18px 42px rgba(20, 21, 22, 0.18);
}

.result-score span {
  font-size: 36px;
  font-weight: 850;
  line-height: 1;
}

.result-score small {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.58);
  font-size: 12px;
  font-weight: 700;
}

.result-layout {
  display: grid;
  grid-template-columns: 430px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.result-chart-column {
  display: grid;
  gap: 16px;
}

.result-panel {
  min-width: 0;
  min-height: 320px;
  padding: 20px;
  border: 1px solid var(--color-border-light);
  border-radius: 26px;
  background: var(--color-surface);
  box-shadow:
    0 20px 52px rgba(20, 21, 22, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
}

.result-panel h2 {
  margin: 0 0 14px;
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 850;
}

.chart-stack {
  display: grid;
  gap: 12px;
}

.result-report {
  min-height: 656px;
  display: flex;
  flex-direction: column;
}

.report-scroll {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
}

.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(235, 235, 232, 0.72);
  backdrop-filter: blur(12px);
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
  background-color: var(--color-accent);
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
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1100px) {
  .result-layout {
    grid-template-columns: 1fr;
  }

  .result-page-hero {
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .result-page {
    padding: 16px;
  }

  .result-page-hero,
  .result-panel {
    padding: 18px;
    border-radius: 24px;
  }
}
</style>
