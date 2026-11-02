<template>
  <div class="page">
    <div class="page-head no-print">
      <div>
        <Breadcrumb>
          <BreadcrumbItem><router-link to="/records">面试记录</router-link></BreadcrumbItem>
          <BreadcrumbItem>批量打印报告</BreadcrumbItem>
        </Breadcrumb>
        <h1 class="page-title mt-1">批量打印报告</h1>
      </div>
      <Button type="primary" :disabled="loading || !reports.length" @click="print">
        <FilePdfOutlined /> 打印 / 另存为 PDF
      </Button>
    </div>

    <div class="card card-pad no-print mb-4">
      <template v-if="loading">
        <div class="mb-2">正在载入报告 <span class="num">{{ loaded }}/{{ ids.length }}</span></div>
        <Progress :percent="ids.length ? Math.round((loaded / ids.length) * 100) : 0" :show-info="false" />
      </template>
      <template v-else-if="!ids.length">
        <Empty description="没有要打印的报告，请从面试记录中选择" />
      </template>
      <div v-else class="leading-7">
        共 <span class="num">{{ reports.length }}</span> 份报告，每份从新的一页开始。点击“打印 / 另存为 PDF”，在打印窗口中选择“另存为 PDF”，即可得到一个包含全部报告的 PDF 文件。
        <div v-if="skipped.length" class="text-fail">
          {{ skipped.length }} 份报告还在评分中或无法读取，已跳过：{{ skipped.join("、") }}
        </div>
      </div>
    </div>

    <div v-for="(d, i) in reports" :key="d.id" class="batch-item" :class="{ 'is-first': i === 0 }">
      <ReportDocument :detail="d" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Breadcrumb, BreadcrumbItem, Button, Empty, Progress } from "ant-design-vue";
import { FilePdfOutlined } from "@ant-design/icons-vue";
import dayjs from "dayjs";
import { getStudyDetail } from "@/api/studyRecord";
import ReportDocument from "./ReportDocument.vue";
import { printReport } from "./usePrintReport";
import type { ReportDetail } from "./types";

const CONCURRENCY = 4;

const ids = ref<number[]>([]);
const reports = ref<ReportDetail[]>([]);
const skipped = ref<string[]>([]);
const loaded = ref(0);
const loading = ref(true);

async function loadAll() {
  try {
    ids.value = JSON.parse(sessionStorage.getItem("print-ids") || "[]");
  } catch (e) {
    ids.value = [];
  }
  const results: (ReportDetail | null)[] = new Array(ids.value.length).fill(null);
  let next = 0;
  async function worker() {
    while (next < ids.value.length) {
      const i = next++;
      try {
        const res: any = await getStudyDetail({ id: ids.value[i] });
        const d: ReportDetail = res.data;
        if (d?.state === 1 && d.dimensions) {
          results[i] = d;
        } else {
          skipped.value.push(d?.studentName || `#${ids.value[i]}`);
        }
      } catch (e) {
        skipped.value.push(`#${ids.value[i]}`);
      } finally {
        loaded.value++;
      }
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  reports.value = results.filter((d): d is ReportDetail => !!d);
  loading.value = false;
}

function print() {
  const first = reports.value[0];
  printReport(`${first?.theme || "面试"}-面试报告-${reports.value.length}份-${dayjs().format("YYYYMMDD")}`);
}

onMounted(loadAll);
</script>

<style scoped>
.batch-item + .batch-item {
  margin-top: 24px;
}

.text-fail {
  color: var(--c-danger);
}

@media print {
  .batch-item + .batch-item {
    margin-top: 0;
    break-before: page;
  }
}
</style>
