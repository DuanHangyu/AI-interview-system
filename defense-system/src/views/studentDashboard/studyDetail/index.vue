<template>
  <main class="studentDetail">
    <div class="student-detail-inner">
      <StudentHeader />

      <section class="detail-hero">
        <button class="detail-back" type="button" @click="$router.back()">
          <ArrowLeftOutlined />
        </button>
        <div class="detail-hero-copy">
          <div class="detail-eyebrow">Interview Report</div>
          <h1>{{ detailTitle }}</h1>
          <p>查看本次面试的得分、维度表现、回答细节和 AI 复盘建议。</p>
        </div>
        <div class="detail-score-pill" v-if="showResult">
          <span>{{ info?.defenseScore ?? info?.score ?? "-" }}</span>
          <small>综合得分</small>
        </div>
      </section>

      <section class="detail-grid">
        <aside class="detail-score-column">
          <section class="detail-card score-card">
            <div class="detail-card-title">
              <FundOutlined />
              当前得分
            </div>
            <div class="detail-card-body">
              <div class="detail-locked" v-if="!showResult">
                <LockOutlined />
                <span>教师设置不允许查看</span>
              </div>
              <DqdfChart :info="info" v-else-if="info?.id" />
            </div>
            <p class="detail-note" v-if="showResult">当前得分由系统换算得出</p>
          </section>

          <section class="detail-card score-card">
            <div class="detail-card-title">
              <BarChartOutlined />
              得分明细
            </div>
            <div class="detail-card-body">
              <div class="detail-locked" v-if="!showResult">
                <LockOutlined />
                <span>教师设置不允许查看</span>
              </div>
              <DfmxChart :info="info" v-else-if="info?.id" />
            </div>
          </section>
        </aside>

        <section class="detail-card detail-main-card">
          <header class="detail-main-head">
            <div class="detail-card-title">
              <FileTextOutlined />
              {{ showResult ? "答辩详情" : "建议老师发布" }}
            </div>
            <div class="detail-nav">
              <button type="button" @click="prev" :disabled="list.length <= 1">
                <LeftOutlined />
              </button>
              <button type="button" @click="next" :disabled="list.length <= 1">
                <RightOutlined />
              </button>
            </div>
          </header>

          <div class="detail-card-stage">
            <template v-for="(item, index) in list">
              <div
                class="detail-stack-item"
                v-if="index < 3"
                :class="`index${index}`"
                :key="index"
              >
                <AnalysisCard
                  :info="item"
                  :summary="info?.summary"
                  v-if="item.cardTag == 'analysis'"
                />
                <SuggestionCard
                  :info="item"
                  v-else-if="item.cardTag == 'suggestions'"
                />
                <LdYBzCard
                  :info="item"
                  v-else-if="item.cardTag == 'strengths'"
                />
                <DefenseCard
                  :info="item"
                  v-else-if="item.cardTag == 'defense'"
                />
                <QuestionCard :info="item" :key="index" v-else />
              </div>
            </template>
            <div class="detail-locked empty-card" v-if="!loading && !list.length">
              <FileTextOutlined />
              <span>暂无报告内容</span>
            </div>
          </div>
        </section>
      </section>

      <footer class="detail-footer">本报告由人工智能生成</footer>
    </div>
  </main>
</template>
<script setup lang="ts">
import StudentHeader from "@/components/Layout/componets/StudentHeader.vue";
import DefenseCard from "./components/DefenseCard.vue";
import QuestionCard from "./components/QuestionCard.vue";
import SuggestionCard from "./components/SuggestionCard.vue";
import LdYBzCard from "./components/LdYBzCard.vue";
import AnalysisCard from "./components/AnalysisCard.vue";
import DfmxChart from "./components/DfmxChart.vue";
import DqdfChart from "./components/DqdfChart.vue";
import { useRoute } from "vue-router";
import { computed, onMounted, ref } from "vue";
import { getStudentAssessmentDetail } from "@/api/studentAssessment";
import { getStudyDetail } from "@/api/studyRecord";
import {
  ArrowLeftOutlined,
  BarChartOutlined,
  FileTextOutlined,
  FundOutlined,
  LeftOutlined,
  LockOutlined,
  RightOutlined,
} from "@ant-design/icons-vue";

const colorClasses = [
  "cardColor1",
  "cardColor2",
  "cardColor3",
  "cardColor4",
  "cardColor5",
  "cardColor6",
];
const loading = ref(false);
const route = useRoute();
const info = ref<Recordable>({});
const list = ref<Recordable[]>([]);
const showResult = computed(() => {
  if (route.name == "StudentStudyDetail") {
    return !!info.value?.showResult;
  } else {
    return true;
  }
});
const detailTitle = computed(() => info.value?.theme || "面试复盘报告");
const onLoad = () => {
  loading.value = true;
  (route.name == "StudentStudyDetail"
    ? getStudentAssessmentDetail
    : getStudyDetail)({
    id: route.query?.id,
  })
    .then((res) => {
      info.value = res?.data || {};
      if (route.name == "StudentStudyDetail") {
        if (info.value?.showResult) {
          if (info.value?.defense) {
            list.value?.push({
              cardTag: "defense",
              ...(info.value?.defense || {}),
            });
          }
          list.value?.push(
            ...(res?.data?.result?.map(
              (
                item: { cardTag: string; questionIndex: number },
                index: number
              ) => {
                item.cardTag = "question";
                item.questionIndex = index + 1;
                return item;
              }
            ) || [])
          );
          list.value?.push({
            cardTag: "analysis",
            analysis: res?.data?.analysis?.analysis,
          });
        }
      } else {
        if (info.value?.defense) {
          list.value?.push({
            cardTag: "defense",
            ...(info.value?.defense || {}),
          });
        }
        list.value?.push(
          ...(res?.data?.result?.map(
            (
              item: { cardTag: string; questionIndex: number },
              index: number
            ) => {
              item.cardTag = "question";
              item.questionIndex = index + 1;
              return item;
            }
          ) || [])
        );
        list.value?.push({
          cardTag: "analysis",
          analysis: res?.data?.analysis?.analysis,
        });
      }
      list.value?.push({
        cardTag: "suggestions",
        suggestions: res?.data?.analysis?.suggestions,
      });
      list.value?.push({
        cardTag: "strengths",
        strengths: res?.data?.analysis?.strengths,
        weaknesses: res?.data?.analysis?.weaknesses,
      });
      for (let index = 0; index < list.value.length; index++) {
        const element = list.value[index];
        element.colorClass = colorClasses[index % 6];
      }
    })
    .finally(() => {
      loading.value = false;
    });
};

onMounted(() => {
  onLoad();
});

const prev = () => {
  const data = list.value?.pop();
  list.value?.unshift(data as any);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.add("fade-enter");
  }, 100);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.remove("fade-enter");
  }, 800);
};
const next = () => {
  const data = list.value?.shift();
  list.value?.push(data as any);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.add("fade-enter");
  }, 100);
  setTimeout(() => {
    document.querySelector(".index0")?.classList?.remove("fade-enter");
  }, 800);
};
</script>
<style scoped>
.studentDetail {
  width: 100%;
  min-height: 100vh;
  padding: 24px;
  overflow-y: auto;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.36), rgba(0, 0, 0, 0.18)),
    linear-gradient(135deg, #eeeeec 0%, #d5d5d2 45%, #7f807d 100%);
}

.student-detail-inner {
  width: min(1500px, 100%);
  min-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 0 auto;
}

.detail-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--color-border-light);
  border-radius: 28px;
  background: var(--color-surface);
  box-shadow:
    0 28px 80px rgba(20, 21, 22, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
}

.detail-back,
.detail-nav button {
  border: 0;
  cursor: pointer;
  transition:
    transform 180ms cubic-bezier(0.2, 0.8, 0.2, 1),
    box-shadow 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.detail-back {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: linear-gradient(145deg, #444544, #2f302f);
  color: #ffffff;
  box-shadow: 0 14px 30px rgba(20, 21, 22, 0.22);
}

.detail-back:hover,
.detail-nav button:not(:disabled):hover {
  transform: translateY(-1px);
}

.detail-eyebrow {
  width: fit-content;
  padding: 6px 11px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 800;
}

.detail-hero h1 {
  margin: 10px 0 8px;
  color: var(--color-text-primary);
  font-size: clamp(30px, 3.6vw, 46px);
  font-weight: 850;
  line-height: 1.08;
}

.detail-hero p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.detail-score-pill {
  min-width: 150px;
  min-height: 96px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(145deg, #444544, #2f302f);
  color: #ffffff;
  box-shadow: 0 18px 42px rgba(20, 21, 22, 0.18);
}

.detail-score-pill span {
  font-size: 34px;
  font-weight: 850;
  line-height: 1;
}

.detail-score-pill small {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.58);
  font-size: 12px;
  font-weight: 700;
}

.detail-grid {
  flex: 1;
  min-height: 680px;
  display: grid;
  grid-template-columns: 410px minmax(0, 1fr);
  gap: 16px;
}

.detail-score-column {
  display: grid;
  grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.detail-card {
  min-width: 0;
  min-height: 0;
  padding: 20px;
  border: 1px solid var(--color-border-light);
  border-radius: 26px;
  background: var(--color-surface);
  box-shadow:
    0 20px 52px rgba(20, 21, 22, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
}

.score-card {
  display: flex;
  flex-direction: column;
  min-height: 330px;
}

.detail-card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 850;
}

.detail-card-title :deep(.anticon) {
  color: var(--color-accent);
}

.detail-card-body {
  min-height: 0;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.score-card .detail-card-body {
  min-height: 260px;
}

.detail-note {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.detail-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 220px;
  color: var(--color-text-muted);
  font-size: 15px;
  font-weight: 700;
}

.detail-locked :deep(.anticon) {
  color: var(--color-accent);
  font-size: 28px;
}

.detail-main-card {
  display: flex;
  flex-direction: column;
}

.detail-main-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
}

.detail-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-nav button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--color-text-primary);
}

.detail-nav button:disabled {
  cursor: not-allowed;
  color: var(--color-text-muted);
  opacity: 0.5;
}

.detail-card-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  margin-top: 18px;
}

.detail-stack-item {
  position: absolute;
  width: 100%;
}

.index0 {
  height: calc(100% - 32px);
  top: 32px;
  left: 0;
  z-index: 3;
}

.index1 {
  width: calc(100% - 32px);
  height: calc(100% - 16px);
  top: 16px;
  left: 16px;
  z-index: 2;
}

.index2 {
  width: calc(100% - 64px);
  height: 100%;
  top: 0;
  left: 32px;
  z-index: 1;
}

.empty-card {
  height: 100%;
}

.detail-footer {
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 600;
}

@keyframes scaleEnter {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.03);
  }
  100% {
    transform: scale(1);
  }
}

.fade-enter {
  animation: scaleEnter 0.9s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@media (max-width: 1180px) {
  .detail-grid,
  .detail-hero {
    grid-template-columns: 1fr;
  }

  .detail-score-column {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: 1fr;
  }

  .detail-grid {
    min-height: 1040px;
  }

  .detail-score-pill {
    width: 100%;
  }
}

@media (max-width: 760px) {
  .studentDetail {
    padding: 16px;
  }

  .detail-hero,
  .detail-card {
    padding: 18px;
    border-radius: 24px;
  }

  .detail-score-column {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    min-height: 1260px;
  }
}
</style>
<style>
.cardColor1,
.cardColor2,
.cardColor3,
.cardColor4,
.cardColor5,
.cardColor6 {
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(246, 246, 244, 0.64)),
    linear-gradient(135deg, rgba(231, 95, 73, 0.12), rgba(68, 69, 68, 0.08));
  box-shadow:
    0 18px 42px rgba(20, 21, 22, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
}

.cardColor2 {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(246, 246, 244, 0.64)),
    linear-gradient(135deg, rgba(231, 95, 73, 0.16), rgba(79, 111, 143, 0.08));
}

.cardColor3 {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(246, 246, 244, 0.64)),
    linear-gradient(135deg, rgba(216, 154, 53, 0.13), rgba(68, 69, 68, 0.08));
}

.cardColor4 {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(246, 246, 244, 0.64)),
    linear-gradient(135deg, rgba(79, 111, 143, 0.14), rgba(231, 95, 73, 0.08));
}

.cardColor5 {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(246, 246, 244, 0.64)),
    linear-gradient(135deg, rgba(47, 143, 105, 0.12), rgba(68, 69, 68, 0.08));
}

.cardColor6 {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(246, 246, 244, 0.64)),
    linear-gradient(135deg, rgba(68, 69, 68, 0.13), rgba(231, 95, 73, 0.1));
}

.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.3);
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: #b7b8b6;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #9b9c99;
}

@supports not selector(::-webkit-scrollbar) {
  .custom-scrollbar {
    scrollbar-width: thin;
    scrollbar-color: #b7b8b6 rgba(255, 255, 255, 0.3);
  }
}
</style>
