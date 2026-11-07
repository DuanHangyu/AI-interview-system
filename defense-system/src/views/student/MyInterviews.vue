<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">我的面试</h1>
        <p class="page-desc">请在老师安排的考试时间内进入面试，可以提前 10 分钟进入候考检查麦克风</p>
      </div>
      <Button :loading="loading" @click="load"><ReloadOutlined /> 刷新</Button>
    </div>

    <div class="card">
      <Tabs v-model:activeKey="tab" class="list-tabs">
        <TabPane key="todo" :tab="`待参加（${todo.length}）`" />
        <TabPane key="done" :tab="`已完成（${done.length}）`" />
      </Tabs>

      <Spin :spinning="loading && !items.length">
        <div v-if="!shown.length" class="py-16">
          <Empty :description="tab === 'todo' ? '暂无待参加的面试' : '暂无已完成的面试'" />
        </div>
        <ul v-else class="rows">
          <li v-for="item in shown" :key="item.assessmentId" class="row">
            <div class="row-main">
              <div class="row-title">
                {{ item.theme }}
                <Tag v-if="item.retake" color="orange" :bordered="false" class="ml-1">重考</Tag>
              </div>
              <div class="row-meta">{{ metaText(item) }}</div>
              <div v-if="item.sessionStart" class="row-slot">
                <CalendarOutlined />
                <span>考试时间</span>
                <span class="num">{{ rangeText(item) }}</span>
                <span v-if="item.location">· {{ item.location }}</span>
              </div>
            </div>
            <div class="row-side">
              <template v-if="view(item).kind === 'start'">
                <span v-if="view(item).hint" class="hint">{{ view(item).hint }}</span>
                <Button type="primary" @click="enter(item)">{{ view(item).label }}</Button>
              </template>
              <template v-else-if="view(item).kind === 'wait'">
                <span class="hint num">{{ view(item).hint }}</span>
                <Button :type="view(item).enterable ? 'primary' : 'default'" :disabled="!view(item).enterable" @click="enter(item)">
                  {{ view(item).label }}
                </Button>
              </template>
              <template v-else-if="view(item).kind === 'report'">
                <Button @click="router.push(`/student/reports/${item.assessmentId}`)">查看报告</Button>
              </template>
              <span v-else class="hint" :class="view(item).cls">
                <LoadingOutlined v-if="item.status === 'ANALYZING'" class="mr-1" />{{ view(item).hint }}
              </span>
            </div>
          </li>
        </ul>
      </Spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, Spin, TabPane, Tabs, Tag } from "ant-design-vue";
import { CalendarOutlined, LoadingOutlined, ReloadOutlined } from "@ant-design/icons-vue";
import dayjs from "dayjs";
import { getMyInterviews, type MyInterview } from "@/api/exam";

const EARLY_MINUTES = 10;

const router = useRouter();
const tab = ref<"todo" | "done">("todo");
const items = ref<MyInterview[]>([]);
const loading = ref(false);
const serverOffset = ref(0);
const now = ref(Date.now());
let tick: number | undefined;
let refreshTimer: number | undefined;

const todo = computed(() => items.value.filter((i) => i.status !== "DONE" && i.status !== "ANALYZING"));
const done = computed(() => items.value.filter((i) => i.status === "DONE" || i.status === "ANALYZING"));
const shown = computed(() => (tab.value === "todo" ? todo.value : done.value));

function serverNow() {
  return dayjs(now.value + serverOffset.value);
}

function minutes(sec?: number) {
  if (!sec) return "0 分钟";
  return sec % 60 === 0 ? `${sec / 60} 分钟` : `${sec} 秒`;
}

function metaText(item: MyInterview) {
  const parts: string[] = [];
  if (item.defense) parts.push(`答辩 ${minutes(item.duration)}`);
  if (item.question) parts.push(`问答 ${item.questionCount ?? 0} 题 · 每题 ${minutes(item.answerTime)}`);
  if (item.totalScore != null) parts.push(`满分 ${item.totalScore}`);
  return parts.join(" · ");
}

function rangeText(item: MyInterview) {
  const a = dayjs(item.sessionStart!);
  const b = item.sessionEnd ? dayjs(item.sessionEnd) : null;
  return `${a.format("M月D日 HH:mm")}${b ? ` – ${b.format(b.isSame(a, "day") ? "HH:mm" : "M月D日 HH:mm")}` : ""}`;
}

function countdown(target: dayjs.Dayjs) {
  const sec = Math.max(0, target.diff(serverNow(), "second"));
  const d = Math.floor(sec / 86400);
  const h = Math.floor((sec % 86400) / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  if (d > 0) return `${d} 天 ${h} 小时后开始`;
  if (h > 0) return `${h} 小时 ${m} 分后开始`;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")} 后开始`;
}

interface RowView {
  kind: "start" | "wait" | "report" | "text";
  label?: string;
  hint?: string;
  enterable?: boolean;
  cls?: string;
}

// 按服务器时间在本地推进状态：场次到点后按钮自动变为可用，无需刷新
function view(item: MyInterview): RowView {
  const t = serverNow();
  const start = item.sessionStart ? dayjs(item.sessionStart) : null;
  const end = item.sessionEnd ? dayjs(item.sessionEnd) : null;
  let status = item.status;
  if (status === "WAITING" && start && !t.isBefore(start)) status = "OPEN";
  if (status === "OPEN" && end && t.isAfter(end)) status = "MISSED";
  switch (status) {
    case "IN_PROGRESS":
      return { kind: "start", label: "继续面试", hint: "面试未完成" };
    case "OPEN":
      return { kind: "start", label: "进入面试", hint: end ? `${end.format("HH:mm")} 前开始` : undefined };
    case "READY":
      return { kind: "start", label: "进入面试" };
    case "WAITING": {
      const enterable = !!start && !t.isBefore(start.subtract(EARLY_MINUTES, "minute"));
      return { kind: "wait", label: enterable ? "进入候考" : "未到考试时间", hint: start ? countdown(start) : "", enterable };
    }
    case "UNSCHEDULED":
      return { kind: "text", hint: "老师尚未安排考试时间" };
    case "MISSED":
      return { kind: "text", hint: "考试时间已过，请联系老师重新安排", cls: "text-fail" };
    case "ANALYZING":
      return { kind: "text", hint: "AI 正在生成报告" };
    case "DONE":
      return item.showResult ? { kind: "report" } : { kind: "text", hint: "已完成，成绩待老师公布" };
    default:
      return { kind: "text" };
  }
}

async function load() {
  loading.value = true;
  try {
    const res: any = await getMyInterviews();
    items.value = res?.data?.items || [];
    if (res?.data?.serverTime) serverOffset.value = dayjs(res.data.serverTime).valueOf() - Date.now();
  } finally {
    loading.value = false;
  }
}

function enter(item: MyInterview) {
  router.push({ path: "/interview", query: { id: item.assessmentId } });
}

onMounted(() => {
  load();
  tick = window.setInterval(() => (now.value = Date.now()), 1000);
  // 评分中的面试完成后自动出现在“已完成”中
  refreshTimer = window.setInterval(load, 60000);
});

onUnmounted(() => {
  window.clearInterval(tick);
  window.clearInterval(refreshTimer);
});
</script>

<style scoped>
.list-tabs :deep(.ant-tabs-nav) {
  margin: 0;
  padding: 0 24px;
}

.rows {
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 24px;
  border-bottom: 1px solid var(--c-border-2);
}

.row:last-child {
  border-bottom: 0;
}

.row-main {
  min-width: 0;
}

.row-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.row-meta {
  margin-top: 4px;
  font-size: 13px;
  color: var(--c-text-3);
}

.row-slot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--c-text-2);
}

.row-side {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
}

.hint {
  font-size: 13px;
  color: var(--c-text-3);
}

.text-fail {
  color: var(--c-danger);
}

@media (max-width: 760px) {
  .row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
