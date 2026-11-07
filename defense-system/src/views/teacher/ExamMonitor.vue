<template>
  <div class="page">
    <div class="page-head">
      <div>
        <Breadcrumb class="mb-1">
          <BreadcrumbItem><router-link to="/assessments">面试管理</router-link></BreadcrumbItem>
          <BreadcrumbItem>考场</BreadcrumbItem>
        </Breadcrumb>
        <h1 class="page-title">{{ monitor?.theme || "考场" }}</h1>
      </div>
      <Space>
        <span class="muted text-[13px]">服务器时间 <span class="num">{{ clock }}</span></span>
        <template v-if="monitor">
          <Tag v-if="monitor.showResult" color="success">成绩已公布</Tag>
          <Tag v-else>成绩未公布</Tag>
          <Popconfirm
            v-if="!monitor.showResult"
            :title="`公布后，${monitor.counts.DONE || 0} 名已完成的学生可以查看自己的分数和报告；之后完成的学生出分后也能立即查看。请先在面试记录中核对成绩。确定公布吗？`"
            ok-text="公布成绩"
            :overlay-style="{ maxWidth: '360px' }"
            @confirm="toggleShowResult(true)"
          >
            <Button type="primary" :loading="publishing">向学生公布成绩</Button>
          </Popconfirm>
          <Popconfirm
            v-else
            title="收回后学生将看不到分数和报告，确定收回吗？"
            ok-text="收回"
            @confirm="toggleShowResult(false)"
          >
            <Button :loading="publishing">收回成绩</Button>
          </Popconfirm>
        </template>
        <Button @click="router.push({ path: '/records', query: { assessmentId } })">面试记录</Button>
      </Space>
    </div>

    <!-- 统计 -->
    <div class="stats">
      <button
        v-for="s in statCards"
        :key="s.key"
        type="button"
        class="stat"
        :class="{ 'is-active': (statusFilter || '') === s.key }"
        @click="statusFilter = s.key && statusFilter !== s.key ? s.key : undefined"
      >
        <span class="stat-label">{{ s.label }}</span>
        <span class="stat-value num" :class="s.cls">{{ s.value }}</span>
      </button>
    </div>

    <div class="card mt-4">
      <Tabs v-model:activeKey="tab" class="px-6">
        <TabPane key="students" tab="考生" />
        <TabPane key="sessions" :tab="`场次设置（${sessions.length}）`" />
      </Tabs>

      <!-- 考生 -->
      <div v-show="tab === 'students'">
        <div class="toolbar">
          <Select v-model:value="sessionFilter" :options="sessionOptions" class="w-[230px]" placeholder="全部场次" allow-clear />
          <Select v-model:value="classFilter" :options="classOptions" class="w-[170px]" placeholder="全部班级" allow-clear />
          <Select v-model:value="statusFilter" :options="statusOptions" class="w-[130px]" placeholder="全部状态" allow-clear />
          <InputSearch v-model:value="keyword" placeholder="姓名 / 学号" allow-clear class="w-[180px]" />
          <span class="ml-auto inline-flex items-center gap-2 text-[13px]">
            <Switch v-model:checked="autoRefresh" size="small" />自动刷新
            <Button size="small" :loading="loading" @click="load()"><ReloadOutlined /></Button>
          </span>
        </div>

        <div v-if="monitor?.bySession" class="batchbar">
          <span class="muted">已选 <span class="num">{{ selected.length }}</span> 人</span>
          <Dropdown :disabled="!selected.length || !sessions.length" :trigger="['click']">
            <Button size="small" type="primary" :disabled="!selected.length || !sessions.length">安排到场次 <DownOutlined /></Button>
            <template #overlay>
              <Menu @click="({ key }) => assignTo(Number(key))">
                <MenuItem v-for="s in sessions" :key="String(s.id)">
                  {{ sessionText(s) }}（{{ s.assignedCount }}{{ s.capacity ? `/${s.capacity}` : "" }} 人）
                </MenuItem>
              </Menu>
            </template>
          </Dropdown>
          <Popconfirm title="确定取消所选学生的场次安排吗？" @confirm="assignTo(null)">
            <Button size="small" :disabled="!selected.length">取消安排</Button>
          </Popconfirm>
          <Button size="small" class="ml-auto" :disabled="!monitor.counts.UNSCHEDULED" @click="autoAssign">
            自动分配未安排的 {{ monitor.counts.UNSCHEDULED || 0 }} 人
          </Button>
        </div>
        <Alert
          v-else-if="monitor"
          class="mx-6 mb-3"
          type="info"
          show-icon
          message="本场面试不分场次，学生随时可以进入。需要分批考试时，在“场次设置”中添加场次。"
        />

        <Table
          size="middle"
          row-key="studentId"
          :columns="columns"
          :data-source="filtered"
          :loading="loading && !monitor"
          :row-selection="monitor?.bySession ? { selectedRowKeys: selected, onChange: (k: any[]) => (selected = k) } : undefined"
          :pagination="{ pageSize: 50, showSizeChanger: true, pageSizeOptions: ['20', '50', '100', '200'], showTotal: (t: number) => `共 ${t} 人` }"
          :scroll="{ x: 1080 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'student'">
              <div class="font-medium">{{ record.name }}</div>
              <div class="text-xs muted mt-0.5">{{ [record.account, record.schoolClass].filter(Boolean).join(" · ") }}</div>
            </template>
            <template v-else-if="column.key === 'session'">
              <template v-if="record.sessionStart">
                <div class="num">{{ rangeText(record.sessionStart, record.sessionEnd) }}</div>
                <div v-if="record.location" class="text-xs muted mt-0.5">{{ record.location }}</div>
              </template>
              <span v-else class="muted">{{ monitor?.bySession && !record.recordId && record.status !== 'IN_PROGRESS' ? "未安排" : "—" }}</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag :bordered="false" :color="statusColor[record.status as ExamStatus]">{{ record.statusLabel }}</Tag>
              <Tag v-if="record.retake" :bordered="false" color="orange">重考</Tag>
            </template>
            <template v-else-if="column.key === 'progress'">
              <template v-if="record.totalSteps">
                <div class="flex items-center gap-2">
                  <Progress
                    :percent="Math.round(((record.answeredSteps || 0) / record.totalSteps) * 100)"
                    :show-info="false"
                    size="small"
                    class="!m-0 w-[90px]"
                  />
                  <span class="num text-xs muted">{{ record.answeredSteps }}/{{ record.totalSteps }}</span>
                </div>
                <div class="text-xs mt-0.5">
                  <span v-if="record.progress">{{ record.progress }}</span>
                  <span v-if="record.status === 'IN_PROGRESS'" :class="idleClass(record)">
                    {{ record.progress ? " · " : "" }}{{ idleText(record) }}
                  </span>
                </div>
              </template>
              <span v-else class="muted">—</span>
            </template>
            <template v-else-if="column.key === 'score'">
              <template v-if="record.status === 'DONE' && record.finalScore != null">
                <span class="num font-medium" :class="record.passed ? 'text-pass' : 'text-fail'">{{ record.finalScore }}</span>
                <span class="muted num"> / {{ monitor?.totalScore ?? "—" }}</span>
                <Tag v-if="record.reviewed" :bordered="false" class="ml-1">已复核</Tag>
              </template>
              <span v-else-if="record.status === 'ANALYZING'" class="muted text-xs">评分中</span>
              <span v-else class="muted">—</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <Space :size="0">
                <Button v-if="record.recordId" type="link" size="small" @click="openReport(record)">报告</Button>
                <Popconfirm
                  v-if="record.status === 'DONE'"
                  :title="retakeTip"
                  ok-text="安排重考"
                  @confirm="retake(record)"
                >
                  <Button type="link" size="small">重考</Button>
                </Popconfirm>
              </Space>
            </template>
          </template>
          <template #emptyText>
            <Empty description="没有符合条件的学生" />
          </template>
        </Table>
      </div>

      <!-- 场次设置 -->
      <div v-show="tab === 'sessions'" class="px-6 pb-6">
        <div class="muted text-[13px] leading-6 mb-4">
          学生只能在自己场次的开始和结束时间之间开始面试，可以提前 10 分钟进入候考页检查麦克风；已经开始的学生超过结束时间也可以答完。
          迟到不扣分，错过场次的学生可以重新安排到其他场次。删除场次后，其中还没开考的学生变为“未安排”。
        </div>
        <div v-for="(s, i) in draftSessions" :key="s.key" class="session-row">
          <span class="num muted w-6 shrink-0">{{ i + 1 }}</span>
          <DatePicker
            v-model:value="s.startTime"
            show-time
            :format="'YYYY-MM-DD HH:mm'"
            value-format="YYYY-MM-DD HH:mm:ss"
            :minute-step="5"
            placeholder="开始时间"
            class="w-[190px]"
            @change="onStartChange(s)"
          />
          <span class="muted">至</span>
          <DatePicker
            v-model:value="s.endTime"
            show-time
            :format="'YYYY-MM-DD HH:mm'"
            value-format="YYYY-MM-DD HH:mm:ss"
            :minute-step="5"
            placeholder="结束时间"
            class="w-[190px]"
          />
          <Input v-model:value="s.location" placeholder="地点（可选）" class="w-[180px]" />
          <InputNumber v-model:value="s.capacity" :min="1" :max="1000" :precision="0" placeholder="不限" addon-after="人" class="w-[120px]" />
          <span class="muted text-[13px] w-[70px]">已安排 {{ s.assignedCount || 0 }}</span>
          <Popconfirm
            :title="s.assignedCount ? `该场次已安排 ${s.assignedCount} 人，删除后未开考的学生变为未安排，确定删除吗？` : '确定删除该场次吗？'"
            @confirm="draftSessions.splice(i, 1)"
          >
            <Button type="text" danger size="small">删除</Button>
          </Popconfirm>
        </div>
        <div v-if="!draftSessions.length" class="py-6"><Empty description="还没有场次，学生可以随时进入面试" /></div>
        <Space class="mt-3">
          <Button @click="addSession"><PlusOutlined /> 添加场次</Button>
          <Button type="primary" :loading="savingSessions" @click="saveSessions">保存场次</Button>
        </Space>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Alert,
  Breadcrumb,
  BreadcrumbItem,
  Button,
  DatePicker,
  Dropdown,
  Empty,
  Input,
  InputNumber,
  InputSearch,
  Menu,
  MenuItem,
  Popconfirm,
  Progress,
  Select,
  Space,
  Switch,
  TabPane,
  Table,
  Tabs,
  Tag,
  message,
} from "ant-design-vue";
import { DownOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons-vue";
import dayjs from "dayjs";
import {
  assignExamSession,
  autoAssignExam,
  getExamMonitor,
  saveExamSessions,
  setExamShowResult,
  type ExamMonitor,
  type ExamSession,
  type ExamStatus,
  type ExamStudentRow,
} from "@/api/exam";
import { retakeApi } from "@/api/studyRecord";

const route = useRoute();
const router = useRouter();
const assessmentId = Number(route.params.id);

const monitor = ref<ExamMonitor | null>(null);
const loading = ref(false);
const publishing = ref(false);
const tab = ref<"students" | "sessions">("students");
const selected = ref<number[]>([]);
const autoRefresh = ref(true);
const serverOffset = ref(0);
const now = ref(Date.now());
let refreshTimer: number | undefined;
let clockTimer: number | undefined;

const statusFilter = ref<string | undefined>();
const sessionFilter = ref<string | undefined>();
const classFilter = ref<string | undefined>();
const keyword = ref("");

const statusColor: Record<ExamStatus, string> = {
  UNSCHEDULED: "default",
  WAITING: "default",
  OPEN: "blue",
  READY: "blue",
  MISSED: "red",
  IN_PROGRESS: "processing",
  ANALYZING: "purple",
  DONE: "success",
};

const retakeTip = "安排重考后，该学生的本次成绩作废，需要重新面试。分场次考试时，请再把他安排到一个未结束的场次。";

const sessions = computed(() => monitor.value?.sessions || []);

const statCards = computed(() => {
  const c = monitor.value?.counts || ({} as Record<string, number>);
  const n = (k: string) => c[k] || 0;
  const cards = [
    { key: "", label: "总人数", value: monitor.value?.students.length ?? "—", cls: "" },
    { key: "WAITING", label: "待考", value: n("WAITING") + n("OPEN") + n("READY"), cls: "" },
    { key: "IN_PROGRESS", label: "考试中", value: n("IN_PROGRESS"), cls: "is-primary" },
    { key: "ANALYZING", label: "评分中", value: n("ANALYZING"), cls: "" },
    { key: "DONE", label: "已完成", value: n("DONE"), cls: "is-success" },
    { key: "MISSED", label: "缺考", value: n("MISSED"), cls: n("MISSED") ? "is-danger" : "" },
  ];
  if (monitor.value?.bySession) {
    cards.splice(1, 0, { key: "UNSCHEDULED", label: "未安排场次", value: n("UNSCHEDULED"), cls: n("UNSCHEDULED") ? "is-warning" : "" });
  }
  return cards;
});

const statusOptions = [
  { label: "未安排场次", value: "UNSCHEDULED" },
  { label: "待考", value: "WAITING" },
  { label: "考试中", value: "IN_PROGRESS" },
  { label: "评分中", value: "ANALYZING" },
  { label: "已完成", value: "DONE" },
  { label: "缺考", value: "MISSED" },
];

const sessionOptions = computed(() => [
  ...sessions.value.map((s) => ({ label: `${sessionText(s)}（${s.assignedCount} 人）`, value: String(s.id) })),
  ...(monitor.value?.bySession ? [{ label: "未安排场次", value: "none" }] : []),
]);

const classOptions = computed(() =>
  Array.from(new Set((monitor.value?.students || []).map((s) => s.schoolClass).filter(Boolean))).map((c) => ({
    label: c as string,
    value: c as string,
  }))
);

const filtered = computed(() => {
  const kw = keyword.value.trim();
  return (monitor.value?.students || []).filter((s) => {
    if (statusFilter.value) {
      // “待考”包含已到场次时间、尚未开始的学生
      const group = statusFilter.value === "WAITING" ? ["WAITING", "OPEN", "READY"] : [statusFilter.value];
      if (!group.includes(s.status)) return false;
    }
    if (sessionFilter.value === "none" && s.sessionId) return false;
    if (sessionFilter.value && sessionFilter.value !== "none" && String(s.sessionId) !== sessionFilter.value) return false;
    if (classFilter.value && s.schoolClass !== classFilter.value) return false;
    if (kw && !s.name?.includes(kw) && !s.account?.includes(kw)) return false;
    return true;
  });
});

const columns = [
  { title: "学生", key: "student", width: 190 },
  { title: "场次", key: "session", width: 190 },
  { title: "状态", key: "status", width: 130 },
  { title: "答题进度", key: "progress", width: 250 },
  { title: "成绩", key: "score", width: 150 },
  { title: "操作", key: "action", width: 120, fixed: "right" as const },
];

const clock = computed(() => dayjs(now.value + serverOffset.value).format("MM-DD HH:mm:ss"));

function sessionText(s: ExamSession) {
  return rangeText(s.startTime, s.endTime);
}

function rangeText(start?: string | null, end?: string | null) {
  if (!start) return "";
  const a = dayjs(start);
  const b = end ? dayjs(end) : null;
  return `${a.format("M月D日 HH:mm")}${b ? `–${b.format(b.isSame(a, "day") ? "HH:mm" : "M月D日 HH:mm")}` : ""}`;
}

function idleMinutes(r: ExamStudentRow) {
  if (!r.lastActiveTime) return 0;
  return Math.max(0, Math.floor((now.value + serverOffset.value - dayjs(r.lastActiveTime).valueOf()) / 60000));
}

function idleText(r: ExamStudentRow) {
  const m = idleMinutes(r);
  return m < 1 ? "刚刚有作答" : `${m} 分钟前有作答`;
}

// 长时间没有新的作答，可能掉线或卡住，标红提示老师关注
function idleClass(r: ExamStudentRow) {
  return idleMinutes(r) >= 10 ? "text-fail" : "muted";
}

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    const res: any = await getExamMonitor(assessmentId);
    monitor.value = res.data;
    serverOffset.value = dayjs(res.data.serverTime).valueOf() - Date.now();
    const ids = new Set(monitor.value!.students.map((s) => s.studentId));
    selected.value = selected.value.filter((id) => ids.has(id));
    if (!sessionsDirty()) resetDraft();
  } finally {
    loading.value = false;
  }
}

function scheduleRefresh() {
  window.clearInterval(refreshTimer);
  if (autoRefresh.value) refreshTimer = window.setInterval(() => tab.value === "students" && load(true), 10000);
}
watch(autoRefresh, scheduleRefresh);

async function assignTo(sessionId: number | null) {
  const res: any = await assignExamSession(assessmentId, sessionId, selected.value);
  const { assigned, skipped } = res.data;
  message.success(
    `${sessionId ? "已安排" : "已取消安排"} ${assigned} 人${skipped ? `，${skipped} 人已开考或已完成，未改动` : ""}`
  );
  selected.value = [];
  load(true);
}

async function autoAssign() {
  const res: any = await autoAssignExam(assessmentId);
  message.success(`已自动安排 ${res.data.assigned} 人`);
  load(true);
}

async function toggleShowResult(show: boolean) {
  publishing.value = true;
  try {
    await setExamShowResult(assessmentId, show);
    message.success(show ? "成绩已公布，学生现在可以查看报告" : "已收回，学生暂时看不到成绩");
    await load(true);
  } finally {
    publishing.value = false;
  }
}

async function retake(r: ExamStudentRow) {
  await retakeApi({ recordId: r.recordId });
  message.success(`已为 ${r.name} 安排重考`);
  load(true);
}

function openReport(r: ExamStudentRow) {
  // 报告页按考场名单顺序翻页
  const ids = filtered.value.map((s) => s.recordId).filter(Boolean);
  sessionStorage.setItem("report-nav", JSON.stringify({ ids, from: route.fullPath }));
  router.push(`/records/${r.recordId}`);
}

// ---------------- 场次设置 ----------------
interface DraftSession extends ExamSession {
  key: string;
}
const draftSessions = ref<DraftSession[]>([]);
const savingSessions = ref(false);
let draftSeq = 0;

// 场次草稿与上次载入时的快照比较：有未保存的修改时，自动刷新不覆盖草稿
let draftBase = "[]";
const draftJson = () => JSON.stringify(draftSessions.value.map(({ key, assignedCount, ...rest }) => rest));
const sessionsDirty = () => draftJson() !== draftBase;

function resetDraft() {
  draftSessions.value = sessions.value.map((s) => ({
    ...s,
    location: s.location ?? undefined,
    capacity: s.capacity ?? undefined,
    key: `s${++draftSeq}`,
  }));
  draftBase = draftJson();
}

function addSession() {
  const last = draftSessions.value[draftSessions.value.length - 1];
  const start = last?.endTime
    ? dayjs(last.endTime)
    : dayjs(now.value + serverOffset.value).add(1, "day").hour(9).minute(0).second(0);
  const duration = last?.startTime && last?.endTime ? dayjs(last.endTime).diff(dayjs(last.startTime), "minute") : 60;
  draftSessions.value.push({
    key: `s${++draftSeq}`,
    startTime: start.format("YYYY-MM-DD HH:mm:ss"),
    endTime: start.add(duration, "minute").format("YYYY-MM-DD HH:mm:ss"),
    location: last?.location,
    capacity: last?.capacity,
    assignedCount: 0,
  });
}

// 修改开始时间时保持场次时长不变
function onStartChange(s: DraftSession) {
  const old = sessions.value.find((x) => x.id === s.id);
  const minutes = old ? dayjs(old.endTime).diff(dayjs(old.startTime), "minute") : 60;
  if (s.startTime && (!s.endTime || !dayjs(s.endTime).isAfter(dayjs(s.startTime)))) {
    s.endTime = dayjs(s.startTime).add(minutes, "minute").format("YYYY-MM-DD HH:mm:ss");
  }
}

async function saveSessions() {
  for (const [i, s] of draftSessions.value.entries()) {
    if (!s.startTime || !s.endTime) return message.error(`请填写第 ${i + 1} 个场次的开始和结束时间`);
    if (!dayjs(s.endTime).isAfter(dayjs(s.startTime))) return message.error(`第 ${i + 1} 个场次的结束时间必须晚于开始时间`);
  }
  savingSessions.value = true;
  try {
    await saveExamSessions(
      assessmentId,
      draftSessions.value.map(({ key, assignedCount, ...s }) => ({ ...s, location: s.location?.trim() || null }))
    );
    message.success("场次已保存");
    draftBase = draftJson();
    await load(true);
    resetDraft();
  } finally {
    savingSessions.value = false;
  }
}

onMounted(async () => {
  await load();
  scheduleRefresh();
  clockTimer = window.setInterval(() => (now.value = Date.now()), 1000);
  if (route.query.tab === "sessions") tab.value = "sessions";
});
onUnmounted(() => {
  window.clearInterval(refreshTimer);
  window.clearInterval(clockTimer);
});
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 1px;
  border: 1px solid var(--c-border);
  background: var(--c-border);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 18px;
  border: 0;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.stat:hover,
.stat.is-active {
  background: var(--c-primary-soft);
}

.stat-label {
  font-size: 13px;
  color: var(--c-text-2);
}

.stat-value {
  font-size: 24px;
  line-height: 32px;
  color: var(--c-text);
}

.stat-value.is-primary {
  color: var(--c-primary);
}

.stat-value.is-success {
  color: var(--c-success);
}

.stat-value.is-warning {
  color: var(--c-warning);
}

.stat-value.is-danger {
  color: var(--c-danger);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 4px 24px 12px;
}

.batchbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 0 24px 12px;
  padding: 8px 12px;
  background: #f7f9fc;
  border: 1px solid var(--c-border-2);
}

.session-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border-2);
}

.text-pass {
  color: var(--c-success);
}

.text-fail {
  color: var(--c-danger);
}
</style>
