<template>
  <div class="page">
    <WelcomeBanner :suffix="auth.role === 'TEACHER' ? '老师' : ''">
      在这里查看学生面试结果，复核 AI 评分并导出成绩。
    </WelcomeBanner>

    <div class="page-head">
      <div>
        <h1 class="page-title">面试记录</h1>
        <p class="page-desc">查看学生的面试结果，复核评分、重新评分或安排重考</p>
      </div>
      <Button :disabled="!selectedKeys.length" :loading="exporting" @click="exportExcel">
        <DownloadOutlined /> 导出 Excel{{ selectedKeys.length ? `（${selectedKeys.length}）` : "" }}
      </Button>
    </div>

    <div class="card card-pad filter">
      <Form layout="inline" :model="query" @finish="search">
        <FormItem label="学生姓名" name="studentName">
          <Input v-model:value="query.studentName" placeholder="请输入" allow-clear class="!w-[200px]" />
        </FormItem>
        <FormItem label="面试名称" name="theme">
          <Input v-model:value="query.theme" placeholder="请输入" allow-clear class="!w-[240px]" />
        </FormItem>
        <FormItem>
          <Space>
            <Button type="primary" html-type="submit">查询</Button>
            <Button @click="reset">重置</Button>
          </Space>
        </FormItem>
      </Form>
    </div>

    <div class="card mt-4">
      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        row-key="id"
        :row-selection="{ selectedRowKeys: selectedKeys, onChange: (keys: any[]) => (selectedKeys = keys) }"
        :pagination="{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        :scroll="{ x: 1000 }"
        :locale="{ emptyText: '暂无面试记录' }"
        @change="onChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'student'">
            <div class="font-medium">{{ record.studentName }}</div>
            <div v-if="studentSub(record)" class="text-xs muted mt-0.5">{{ studentSub(record) }}</div>
          </template>
          <template v-else-if="column.key === 'time'">
            <span class="num">{{ formatTime(record.assessmentTime) }}</span>
          </template>
          <template v-else-if="column.key === 'ai'">
            <span v-if="record.state === 2" class="muted">-</span>
            <span v-else class="num">{{ record.score ?? "-" }}</span>
          </template>
          <template v-else-if="column.key === 'final'">
            <span v-if="record.state === 2" class="muted">-</span>
            <template v-else>
              <span class="num font-semibold" :class="passClass(record)">{{ finalScore(record) }}</span>
              <span v-if="record.totalScore != null" class="muted num"> / {{ record.totalScore }}</span>
              <Tag v-if="record.reviewed" color="gold" :bordered="false" class="ml-2">已复核</Tag>
            </template>
          </template>
          <template v-else-if="column.key === 'state'">
            <Badge v-if="record.state === 2" status="processing" text="评分中" />
            <Badge v-else status="success" text="已完成" />
          </template>
          <template v-else-if="column.key === 'action'">
            <Space :size="4">
              <Button
                type="link"
                size="small"
                :disabled="record.state === 2"
                @click="router.push(`/records/${record.id}`)"
                >查看报告</Button
              >
              <Popconfirm
                title="重新调用 AI 对该学生的回答评分？"
                ok-text="重新评分"
                :disabled="record.state === 2"
                @confirm="reanalyze(record)"
              >
                <Button type="link" size="small" :disabled="record.state === 2">重新评分</Button>
              </Popconfirm>
              <Popconfirm ok-text="安排重考" @confirm="retake(record)">
                <template #title>
                  <div class="max-w-[240px]">安排重考后，学生重新进入面试时会清空本次回答并重新抽题。</div>
                </template>
                <Button type="link" size="small" danger>重考</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Badge, Button, Form, FormItem, Input, Popconfirm, Space, Table, Tag, message } from "ant-design-vue";
import { DownloadOutlined } from "@ant-design/icons-vue";
import dayjs from "dayjs";
import saveAs from "file-saver";
import { exportStudentRecord, getStudyRecordList, reanalyzeApi, retakeApi } from "@/api/studyRecord";
import WelcomeBanner from "@/components/WelcomeBanner.vue";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();

const router = useRouter();
const query = reactive({ studentName: "", theme: "" });
const list = ref<Record<string, any>[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const loading = ref(false);
const exporting = ref(false);
const selectedKeys = ref<any[]>([]);
let pollTimer: number | undefined;

const columns = [
  { title: "学生", key: "student", width: 180 },
  { title: "面试名称", dataIndex: "theme", key: "theme", ellipsis: true },
  { title: "面试时间", key: "time", width: 160 },
  { title: "AI 评分", key: "ai", width: 100 },
  { title: "最终得分", key: "final", width: 190 },
  { title: "状态", key: "state", width: 100 },
  { title: "操作", key: "action", width: 230, fixed: "right" as const },
];

function studentSub(r: Record<string, any>) {
  return [r.studentAccount, r.schoolClass].filter(Boolean).join(" · ");
}

function finalScore(r: Record<string, any>) {
  if (r.finalScore != null) return r.finalScore;
  return r.checkScore > 0 ? r.checkScore : r.score ?? "-";
}

function passClass(r: Record<string, any>) {
  const s = Number(finalScore(r));
  if (r.passScore == null || Number.isNaN(s)) return "";
  return s >= r.passScore ? "text-pass" : "text-fail";
}

function formatTime(t?: string) {
  return t ? dayjs(t).format("YYYY-MM-DD HH:mm") : "-";
}

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    const res: any = await getStudyRecordList({
      studentName: query.studentName.trim(),
      theme: query.theme.trim(),
      page: page.value,
      size: size.value,
    });
    list.value = res?.data?.records || [];
    total.value = res?.data?.total || 0;
  } finally {
    loading.value = false;
    schedulePoll();
  }
}

// 有记录在评分中时每 10 秒静默刷新一次
function schedulePoll() {
  window.clearTimeout(pollTimer);
  if (list.value.some((r) => r.state === 2)) {
    pollTimer = window.setTimeout(() => load(true), 10000);
  }
}

function search() {
  page.value = 1;
  load();
}

function reset() {
  query.studentName = "";
  query.theme = "";
  search();
}

function onChange(p: { current?: number; pageSize?: number }) {
  page.value = p.current || 1;
  size.value = p.pageSize || size.value;
  load();
}

async function reanalyze(r: Record<string, any>) {
  await reanalyzeApi({ recordId: r.id });
  message.success("已提交重新评分，完成后自动更新");
  load();
}

async function retake(r: Record<string, any>) {
  await retakeApi({ recordId: r.id });
  message.success(`已为 ${r.studentName} 安排重考`);
  load();
}

async function exportExcel() {
  exporting.value = true;
  try {
    const res: any = await exportStudentRecord({ recordIds: selectedKeys.value });
    let fileName = "面试记录.xlsx";
    const disposition = res?.headers?.["content-disposition"];
    const match = disposition?.match(/filename\*=UTF-8''([^;]+)/i);
    if (match?.[1]) {
      try {
        fileName = decodeURIComponent(match[1]);
      } catch (e) {
        // 使用默认文件名
      }
    }
    saveAs(new Blob([res.data]), fileName);
    selectedKeys.value = [];
  } finally {
    exporting.value = false;
  }
}

onMounted(load);
onUnmounted(() => window.clearTimeout(pollTimer));
</script>

<style scoped>
.filter :deep(.ant-form-item) {
  margin-bottom: 0;
}

.text-pass {
  color: var(--c-success);
}

.text-fail {
  color: var(--c-danger);
}
</style>
