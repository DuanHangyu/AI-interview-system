<template>
  <div class="page">
    <div class="page-head no-print">
      <div>
        <Breadcrumb>
          <BreadcrumbItem><router-link to="/student/reports">面试报告</router-link></BreadcrumbItem>
          <BreadcrumbItem>报告详情</BreadcrumbItem>
        </Breadcrumb>
        <h1 class="page-title mt-1">{{ detail?.theme || "面试报告" }}</h1>
      </div>
      <Button v-if="visible" type="primary" @click="exportPdf"><FilePdfOutlined /> 导出 PDF</Button>
    </div>

    <div v-if="loading" class="card card-pad flex justify-center py-24"><Spin /></div>
    <Result
      v-else-if="detail && detail.state !== 1"
      status="info"
      title="报告生成中"
      sub-title="系统正在转写录音并评分，通常需要 1～3 分钟，完成后即可查看。"
      class="card"
    >
      <template #extra><Button @click="load">刷新</Button></template>
    </Result>
    <Result
      v-else-if="detail && !visible"
      status="success"
      title="面试已完成"
      sub-title="老师暂未公布本次面试的评分结果。"
      class="card"
    >
      <template #extra>
        <Button @click="router.push('/student/reports')">返回</Button>
      </template>
    </Result>
    <ReportDocument v-else-if="detail" :detail="detail" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Breadcrumb, BreadcrumbItem, Button, Result, Spin } from "ant-design-vue";
import { FilePdfOutlined } from "@ant-design/icons-vue";
import { getStudentAssessmentDetail } from "@/api/studentAssessment";
import ReportDocument from "./ReportDocument.vue";
import { printReport } from "./usePrintReport";
import type { ReportDetail } from "./types";

const route = useRoute();
const router = useRouter();
const assessmentId = Number(route.params.assessmentId);
const detail = ref<ReportDetail | null>(null);
const loading = ref(true);
let timer: number | undefined;

const visible = computed(
  () => detail.value?.state === 1 && detail.value?.showResult !== false && detail.value?.score != null
);

async function load() {
  try {
    const res: any = await getStudentAssessmentDetail({ id: assessmentId });
    detail.value = res.data;
  } finally {
    loading.value = false;
  }
  clearTimeout(timer);
  if (detail.value && detail.value.state !== 1) timer = window.setTimeout(load, 8000);
}

function exportPdf() {
  printReport(`${detail.value?.theme || "面试"}-面试报告`);
}

onMounted(load);
onUnmounted(() => clearTimeout(timer));
</script>
