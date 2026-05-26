<template>
  <div class="result-over-page">
    <div class="result-card">
      <img src="../assets/resultover.png" alt="" class="result-image" />
      <p class="result-title">{{ statusTitle }}</p>
      <p class="result-description">{{ statusDescription }}</p>
      <div class="result-actions">
        <Button v-if="canViewResult" type="primary" @click="goStudyDetail">
          查看分析结果
        </Button>
        <Button @click="goDashboard">返回主界面</Button>
        <Button v-if="status === 'error'" type="link" @click="pollDetail">
          重新检查
        </Button>
      </div>
    </div>
    <LoadingOverlay v-if="checking && !loaded" text="正在确认分析状态，请稍后..." />
  </div>
</template>

<script setup lang="ts">
import { Button } from "ant-design-vue";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import LoadingOverlay from "@/components/LoadingOverlay/index.vue";
import { getStudentAssessmentDetail } from "@/api/studentAssessment";

type AnalysisStatus = "pending" | "retrying" | "done" | "error";

const POLL_INTERVAL = 5000;
const route = useRoute();
const router = useRouter();
const status = ref<AnalysisStatus>("pending");
const loaded = ref(false);
const checking = ref(false);
const detail = ref<Recordable>({});
let pollTimer: number | undefined;

const canViewResult = computed(
  () => status.value === "done" && !!detail.value?.showResult
);

const statusTitle = computed(() => {
  if (status.value === "done") {
    return "考核分析已完成";
  }
  if (status.value === "retrying") {
    return "分析暂未完成";
  }
  if (status.value === "error") {
    return "分析状态确认失败";
  }
  return "考核已提交，正在分析";
});

const statusDescription = computed(() => {
  if (status.value === "done") {
    return detail.value?.showResult
      ? "系统已生成本次面试结果，可以查看分析详情。"
      : "系统已完成本次面试分析，结果暂不对学生展示。";
  }
  if (status.value === "retrying") {
    return "系统会自动重试生成分析结果，请稍后回到主界面查看。";
  }
  if (status.value === "error") {
    return "暂时无法获取分析状态，请检查网络后重新检查。";
  }
  return "系统正在整理录音、转写内容和评分结果，请不要重复进入面试。";
});

function clearPollTimer() {
  if (pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = undefined;
  }
}

function scheduleNextPoll() {
  clearPollTimer();
  if (status.value !== "done") {
    pollTimer = window.setTimeout(pollDetail, POLL_INTERVAL);
  }
}

async function pollDetail() {
  const assessmentId = route.query?.id;
  if (!assessmentId) {
    status.value = "error";
    loaded.value = true;
    return;
  }

  checking.value = true;
  try {
    const res = await getStudentAssessmentDetail({ id: assessmentId });
    detail.value = res?.data || {};
    const hasAnalysisResult =
      !!detail.value?.analysis ||
      !!detail.value?.defense ||
      (Array.isArray(detail.value?.result) && detail.value.result.length > 0);
    if (detail.value?.state === 1 || hasAnalysisResult) {
      status.value = "done";
      clearPollTimer();
    } else if (detail.value?.state === 2 && detail.value?.reAnalysis === false) {
      status.value = "retrying";
      scheduleNextPoll();
    } else {
      status.value = "pending";
      scheduleNextPoll();
    }
  } catch (error) {
    console.error("确认分析状态失败", error);
    status.value = "error";
    scheduleNextPoll();
  } finally {
    loaded.value = true;
    checking.value = false;
  }
}

function goStudyDetail() {
  router.push({
    name: "StudentStudyDetail",
    query: route.query,
  });
}

function goDashboard() {
  router.push("/");
}

onMounted(() => {
  pollDetail();
});

onUnmounted(() => {
  clearPollTimer();
});
</script>

<style scoped>
.result-over-page {
  width: 100%;
  height: 100%;
  padding: 24px 128px;
  box-sizing: border-box;
}

.result-card {
  width: 100%;
  min-height: calc(100% - 200px);
  border: 2px solid #ccc;
  border-radius: 10px;
  box-sizing: border-box;
  background-color: rgba(255, 255, 255, 0.68);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 24px;
}

.result-image {
  width: 280px;
  max-width: 42vw;
}

.result-title {
  margin-top: 20px;
  font-size: 24px;
  line-height: 32px;
  font-weight: 700;
  color: #1f2937;
}

.result-description {
  margin-top: 12px;
  max-width: 520px;
  font-size: 16px;
  line-height: 28px;
  color: #4b5563;
}

.result-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  flex-wrap: wrap;
}
</style>
