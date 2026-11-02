<template>
  <div class="page">
    <div class="page-head no-print">
      <div>
        <Breadcrumb>
          <BreadcrumbItem><router-link to="/records">面试记录</router-link></BreadcrumbItem>
          <BreadcrumbItem>面试报告</BreadcrumbItem>
        </Breadcrumb>
        <h1 class="page-title mt-1">
          {{ detail?.studentName || "面试报告" }}
          <Tag v-if="detail?.review" color="blue" class="ml-2 align-middle">已复核</Tag>
        </h1>
      </div>
      <div v-if="detail && ready" class="flex gap-2">
        <Popconfirm
          title="重新评分会让 AI 重新生成评分，已有的教师复核将被清除，确定继续？"
          ok-text="重新评分"
          @confirm="reanalyze"
        >
          <Button :loading="reanalyzing">重新评分</Button>
        </Popconfirm>
        <Button @click="reviewOpen = true"><EditOutlined /> 复核评分</Button>
        <Button type="primary" @click="exportPdf"><FilePdfOutlined /> 导出 PDF</Button>
      </div>
    </div>

    <div v-if="loading" class="card card-pad flex justify-center py-24"><Spin /></div>
    <Result
      v-else-if="detail && !ready"
      status="info"
      title="AI 正在生成评分"
      sub-title="录音转写和评分完成后即可查看报告，通常需要 1～3 分钟。"
      class="card"
    >
      <template #extra>
        <Button @click="load">刷新</Button>
      </template>
    </Result>
    <ReportDocument v-else-if="detail" :detail="detail" />

    <ReviewDrawer v-if="detail" v-model:open="reviewOpen" :detail="detail" @saved="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute } from "vue-router";
import { Breadcrumb, BreadcrumbItem, Button, Popconfirm, Result, Spin, Tag, message } from "ant-design-vue";
import { EditOutlined, FilePdfOutlined } from "@ant-design/icons-vue";
import { getStudyDetail, reanalyzeApi } from "@/api/studyRecord";
import ReportDocument from "./ReportDocument.vue";
import ReviewDrawer from "./ReviewDrawer.vue";
import { printReport } from "./usePrintReport";
import type { ReportDetail } from "./types";

const route = useRoute();
const recordId = Number(route.params.recordId);
const detail = ref<ReportDetail | null>(null);
const loading = ref(true);
const reviewOpen = ref(false);
const reanalyzing = ref(false);
let timer: number | undefined;

const ready = computed(() => detail.value?.state === 1 && !!detail.value?.dimensions);

async function load() {
  try {
    const res: any = await getStudyDetail({ id: recordId });
    detail.value = res.data;
  } finally {
    loading.value = false;
  }
  clearTimeout(timer);
  // 评分中时自动轮询
  if (detail.value && !ready.value) timer = window.setTimeout(load, 8000);
}

async function reanalyze() {
  reanalyzing.value = true;
  try {
    await reanalyzeApi({ recordId });
    message.success("已提交重新评分");
    await load();
  } finally {
    reanalyzing.value = false;
  }
}

function exportPdf() {
  const d = detail.value;
  printReport(`${d?.studentName || "学生"}-${d?.theme || "面试"}-面试报告`);
}

onMounted(load);
onUnmounted(() => clearTimeout(timer));
</script>
