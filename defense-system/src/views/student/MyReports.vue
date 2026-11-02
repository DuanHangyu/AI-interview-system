<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">面试报告</h1>
        <p class="page-desc">已完成的面试及评估结果，可以查看报告或导出 PDF</p>
      </div>
      <Button :loading="loading" @click="load"><ReloadOutlined /> 刷新</Button>
    </div>

    <div class="card">
      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        row-key="assessmentId"
        :scroll="{ x: 640 }"
        :pagination="{ pageSize: 20, hideOnSinglePage: true, showTotal: (t: number) => `共 ${t} 条` }"
        :locale="{ emptyText: '暂无已完成的面试' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'theme'">
            <div class="font-medium">{{ record.theme }}</div>
            <div v-if="record.sessionStart" class="text-xs muted mt-0.5 num">
              {{ dayjs(record.sessionStart).format("YYYY-MM-DD HH:mm") }}
            </div>
          </template>
          <template v-else-if="column.key === 'state'">
            <Badge v-if="record.status === 'ANALYZING'" status="processing" text="评分中" />
            <Badge v-else-if="record.showResult" status="success" text="已出报告" />
            <Badge v-else status="default" text="老师未开放报告" />
          </template>
          <template v-else-if="column.key === 'action'">
            <Space v-if="record.status === 'DONE' && record.showResult">
              <Button type="link" size="small" class="!px-0" @click="open(record)">查看报告</Button>
              <Button type="link" size="small" class="!px-0" @click="open(record, true)">
                <FilePdfOutlined /> 导出 PDF
              </Button>
            </Space>
            <span v-else class="muted">—</span>
          </template>
        </template>
      </Table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Badge, Button, Space, Table } from "ant-design-vue";
import { FilePdfOutlined, ReloadOutlined } from "@ant-design/icons-vue";
import dayjs from "dayjs";
import { getMyInterviews, type MyInterview } from "@/api/exam";

const router = useRouter();
const items = ref<MyInterview[]>([]);
const loading = ref(false);
let timer: number | undefined;

const list = computed(() => items.value.filter((i) => i.status === "DONE" || i.status === "ANALYZING"));

const columns = [
  { title: "面试", key: "theme" },
  { title: "状态", key: "state", width: 150 },
  { title: "操作", key: "action", width: 200 },
];

async function load() {
  loading.value = true;
  try {
    const res: any = await getMyInterviews();
    items.value = res?.data?.items || [];
  } finally {
    loading.value = false;
  }
  window.clearTimeout(timer);
  if (list.value.some((i) => i.status === "ANALYZING")) timer = window.setTimeout(load, 15000);
}

// 导出：打开报告页后自动弹出打印窗口，选择“另存为 PDF”
function open(item: MyInterview, print = false) {
  router.push({ path: `/student/reports/${item.assessmentId}`, query: print ? { print: "1" } : {} });
}

onMounted(load);
onUnmounted(() => window.clearTimeout(timer));
</script>
