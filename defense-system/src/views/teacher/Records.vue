<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">面试记录</h1>
        <p class="page-desc">按面试、场次、班级、分数筛选学生成绩，批量导出成绩和报告，复核评分或安排重考</p>
      </div>
    </div>

    <div class="card card-pad filter">
      <Form layout="inline" :model="query" @finish="search">
        <FormItem label="面试" name="assessmentId">
          <Select
            v-model:value="query.assessmentId"
            :options="assessmentOptions"
            placeholder="全部面试"
            allow-clear
            show-search
            option-filter-prop="label"
            class="!w-[240px]"
            @change="onAssessmentChange"
          />
        </FormItem>
        <FormItem v-if="sessionOptions.length" label="场次" name="sessionId">
          <Select v-model:value="query.sessionId" :options="sessionOptions" placeholder="全部场次" allow-clear class="!w-[210px]" />
        </FormItem>
        <FormItem label="班级" name="schoolClass">
          <Select v-model:value="query.schoolClass" :options="classOptions" placeholder="全部班级" allow-clear show-search class="!w-[170px]" />
        </FormItem>
        <FormItem label="学生" name="studentName">
          <Input v-model:value="query.studentName" placeholder="姓名 / 学号" allow-clear class="!w-[150px]" />
        </FormItem>
        <FormItem label="得分">
          <Space :size="4">
            <InputNumber v-model:value="query.minScore" :min="0" :precision="0" placeholder="最低" class="!w-[80px]" />
            <span class="muted">—</span>
            <InputNumber v-model:value="query.maxScore" :min="0" :precision="0" placeholder="最高" class="!w-[80px]" />
          </Space>
        </FormItem>
        <FormItem label="结果" name="passed">
          <Select v-model:value="query.passed" :options="passOptions" placeholder="全部" allow-clear class="!w-[100px]" />
        </FormItem>
        <FormItem label="复核" name="reviewed">
          <Select v-model:value="query.reviewed" :options="reviewOptions" placeholder="全部" allow-clear class="!w-[100px]" />
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
      <div class="tablebar">
        <span class="muted">
          共 <span class="num">{{ total }}</span> 条<template v-if="selectedKeys.length">，已选 <span class="num">{{ selectedKeys.length }}</span> 条</template>
        </span>
        <Space class="ml-auto">
          <Button :loading="exporting" @click="exportExcel">
            <DownloadOutlined /> {{ selectedKeys.length ? `导出选中成绩（${selectedKeys.length}）` : "导出成绩 Excel" }}
          </Button>
          <Button :loading="printing" @click="printReports">
            <FilePdfOutlined /> {{ selectedKeys.length ? `打印选中报告（${selectedKeys.length}）` : "批量打印报告" }}
          </Button>
        </Space>
      </div>
      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        row-key="id"
        :row-selection="{ selectedRowKeys: selectedKeys, onChange: (keys: any[]) => (selectedKeys = keys), preserveSelectedRowKeys: true }"
        :pagination="{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['20', '50', '100', '200'],
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        :scroll="{ x: 1120 }"
        :locale="{ emptyText: '暂无面试记录' }"
        @change="onChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'student'">
            <div class="font-medium">{{ record.studentName }}</div>
            <div v-if="studentSub(record)" class="text-xs muted mt-0.5">{{ studentSub(record) }}</div>
          </template>
          <template v-else-if="column.key === 'theme'">
            <div class="truncate">{{ record.theme }}</div>
            <div v-if="record.sessionLabel" class="text-xs muted mt-0.5">{{ record.sessionLabel }}</div>
          </template>
          <template v-else-if="column.key === 'time'">
            <span class="num">{{ formatTime(record.assessmentTime) }}</span>
          </template>
          <template v-else-if="column.key === 'ai'">
            <span v-if="record.state === 2" class="muted">-</span>
            <span v-else class="num">{{ record.aiScore ?? "-" }}</span>
          </template>
          <template v-else-if="column.key === 'final'">
            <span v-if="record.state === 2" class="muted">-</span>
            <template v-else>
              <span class="num font-semibold" :class="passClass(record)">{{ record.finalScore ?? "-" }}</span>
              <span v-if="record.totalScore != null" class="muted num"> / {{ record.totalScore }}</span>
              <Tag v-if="record.reviewed" color="blue" :bordered="false" class="ml-2">已复核</Tag>
            </template>
          </template>
          <template v-else-if="column.key === 'state'">
            <Badge v-if="record.state === 2" status="processing" text="评分中" />
            <Badge v-else status="success" text="已完成" />
          </template>
          <template v-else-if="column.key === 'action'">
            <Space :size="4">
              <Button type="link" size="small" :disabled="record.state === 2" @click="openReport(record)">查看报告</Button>
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
                  <div class="max-w-[260px]">
                    安排重考后，学生重新进入面试时会清空本次回答并重新抽题。分场次考试时，请在“考场”中把学生安排到一个未结束的场次。
                  </div>
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
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Badge,
  Button,
  Form,
  FormItem,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from "ant-design-vue";
import { DownloadOutlined, FilePdfOutlined } from "@ant-design/icons-vue";
import dayjs from "dayjs";
import saveAs from "file-saver";
import {
  exportStudentRecord,
  getStudyRecordIds,
  getStudyRecordList,
  reanalyzeApi,
  retakeApi,
} from "@/api/studyRecord";
import { getAssessmentList } from "@/api/assessment";
import { getExamSessions } from "@/api/exam";
import { getSchoolClassStudent } from "@/api/student";

const MAX_PRINT = 200;

const route = useRoute();
const router = useRouter();
const emptyQuery = () => ({
  assessmentId: undefined as number | undefined,
  sessionId: undefined as number | undefined,
  schoolClass: undefined as string | undefined,
  studentName: "",
  minScore: undefined as number | undefined,
  maxScore: undefined as number | undefined,
  passed: undefined as boolean | undefined,
  reviewed: undefined as boolean | undefined,
});
const query = reactive(emptyQuery());
const sort = reactive({ field: "time", order: "desc" });
const list = ref<Record<string, any>[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const exporting = ref(false);
const printing = ref(false);
const selectedKeys = ref<any[]>([]);
const assessmentOptions = ref<{ label: string; value: number }[]>([]);
const sessionOptions = ref<{ label: string; value: number }[]>([]);
const classOptions = ref<{ label: string; value: string }[]>([]);
let pollTimer: number | undefined;

const passOptions = [
  { label: "及格", value: true },
  { label: "不及格", value: false },
];
const reviewOptions = [
  { label: "已复核", value: true },
  { label: "未复核", value: false },
];

const columns = computed(() => [
  { title: "学生", key: "student", width: 180 },
  { title: "面试 / 场次", key: "theme", ellipsis: true },
  { title: "面试时间", key: "time", width: 160, sorter: true, sortOrder: sortOrderOf("time") },
  { title: "AI 评分", key: "ai", width: 90 },
  { title: "最终得分", key: "final", width: 180, sorter: true, sortOrder: sortOrderOf("score") },
  { title: "状态", key: "state", width: 100 },
  { title: "操作", key: "action", width: 230, fixed: "right" as const },
]);

function sortOrderOf(field: string) {
  return sort.field === field ? (sort.order === "asc" ? "ascend" : "descend") : null;
}

function studentSub(r: Record<string, any>) {
  return [r.studentAccount, r.schoolClass].filter(Boolean).join(" · ");
}

function passClass(r: Record<string, any>) {
  if (r.passScore == null || r.finalScore == null) return "";
  return r.finalScore >= r.passScore ? "text-pass" : "text-fail";
}

function formatTime(t?: string) {
  return t ? dayjs(t).format("YYYY-MM-DD HH:mm") : "-";
}

function filterParams() {
  return {
    ...query,
    studentName: query.studentName.trim() || undefined,
    sortField: sort.field,
    sortOrder: sort.order,
  };
}

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    const res: any = await getStudyRecordList({ ...filterParams(), page: page.value, size: size.value });
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
  selectedKeys.value = [];
  load();
}

function reset() {
  Object.assign(query, emptyQuery());
  sessionOptions.value = [];
  search();
}

function onChange(p: { current?: number; pageSize?: number }, _f: unknown, sorter: { columnKey?: string; order?: string }) {
  page.value = p.current || 1;
  size.value = p.pageSize || size.value;
  if (sorter && sorter.columnKey) {
    const field = sorter.columnKey === "final" ? "score" : "time";
    if (sorter.order) {
      sort.field = field;
      sort.order = sorter.order === "ascend" ? "asc" : "desc";
    } else {
      sort.field = "time";
      sort.order = "desc";
    }
  }
  load();
}

async function onAssessmentChange(id?: number) {
  query.sessionId = undefined;
  sessionOptions.value = [];
  if (!id) return;
  const res: any = await getExamSessions(id);
  sessionOptions.value = (res?.data || []).map((s: Record<string, any>) => {
    const a = dayjs(s.startTime);
    const b = dayjs(s.endTime);
    return { label: `${a.format("M月D日 HH:mm")}–${b.format(b.isSame(a, "day") ? "HH:mm" : "M月D日 HH:mm")}`, value: s.id };
  });
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

// 当前筛选条件下的全部记录 id（或选中的记录），按列表顺序
async function targetIds(): Promise<number[]> {
  if (selectedKeys.value.length) {
    return [...selectedKeys.value];
  }
  const res: any = await getStudyRecordIds({ ...filterParams(), state: 1 });
  return res?.data || [];
}

async function openReport(r: Record<string, any>) {
  try {
    const res: any = await getStudyRecordIds({ ...filterParams(), state: 1 });
    sessionStorage.setItem("report-nav", JSON.stringify({ ids: res?.data || [], from: route.fullPath }));
  } catch (e) {
    sessionStorage.removeItem("report-nav");
  }
  router.push(`/records/${r.id}`);
}

async function printReports() {
  printing.value = true;
  try {
    const ids = await targetIds();
    if (!ids.length) {
      message.warning("没有可打印的报告（评分中的记录不能打印）");
      return;
    }
    if (ids.length > MAX_PRINT) {
      message.warning(`一次最多打印 ${MAX_PRINT} 份报告，请缩小筛选范围（当前 ${ids.length} 份）`);
      return;
    }
    sessionStorage.setItem("print-ids", JSON.stringify(ids));
    window.open(router.resolve("/records/print").href, "_blank");
  } finally {
    printing.value = false;
  }
}

async function exportExcel() {
  exporting.value = true;
  try {
    const params = selectedKeys.value.length ? { recordIds: selectedKeys.value } : filterParams();
    const res: any = await exportStudentRecord(params);
    let fileName = "面试成绩.xlsx";
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
  } finally {
    exporting.value = false;
  }
}

async function loadOptions() {
  const [aRes, cRes]: any[] = await Promise.all([
    getAssessmentList({ page: 1, size: 1000 }),
    getSchoolClassStudent().catch(() => null),
  ]);
  assessmentOptions.value = (aRes?.data?.records || []).map((a: Record<string, any>) => ({ label: a.theme, value: a.id }));
  classOptions.value = (cRes?.data || [])
    .map((c: Record<string, any>) => c.schoolClass)
    .filter(Boolean)
    .map((c: string) => ({ label: c, value: c }));
}

onMounted(async () => {
  const fromExam = Number(route.query.assessmentId);
  if (fromExam) {
    query.assessmentId = fromExam;
    onAssessmentChange(fromExam);
  }
  load();
  loadOptions();
});
onUnmounted(() => window.clearTimeout(pollTimer));
</script>

<style scoped>
.filter :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.tablebar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--c-border-2);
}

.text-pass {
  color: var(--c-success);
}

.text-fail {
  color: var(--c-danger);
}
</style>
