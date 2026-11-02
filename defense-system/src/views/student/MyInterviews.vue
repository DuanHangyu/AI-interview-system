<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">我的面试</h1>
        <p class="page-desc">参加已安排的面试，或预约需要选择时间的面试</p>
      </div>
      <Button @click="refresh"><ReloadOutlined /> 刷新</Button>
    </div>

    <div class="stats">
      <button
        v-for="s in statCards"
        :key="s.key"
        type="button"
        class="stat card"
        :class="{ 'is-active': tab === s.key }"
        @click="onStat(s.key)"
      >
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value num">{{ s.value }}</div>
      </button>
    </div>

    <div class="card mt-4">
      <Tabs v-model:activeKey="tab" class="list-tabs" @change="onTabChange">
        <TabPane key="todo" tab="待参加" />
        <TabPane key="appoint" tab="待预约" />
        <TabPane key="analysis" tab="评分中" />
      </Tabs>

      <Spin :spinning="loading">
        <div v-if="!list.length" class="py-16">
          <Empty :description="emptyText" />
        </div>
        <ul v-else class="rows">
          <li v-for="item in list" :key="item.id" class="row">
            <div class="row-main">
              <div class="row-title">{{ item.theme }}</div>
              <div class="row-meta">{{ metaText(item) }}</div>
              <div v-if="tab === 'todo' && item.timePeriod" class="row-slot">
                <CalendarOutlined />
                <span class="num">{{ formatTime(item.timePeriod) }}</span>
                <span v-if="item.location">· {{ item.location }}</span>
              </div>
              <div v-if="tab === 'appoint'" class="row-slot">
                <CalendarOutlined />
                <span>{{ availableSlots(item) ? `${availableSlots(item)} 个时间段可预约` : "暂无可预约时间段" }}</span>
              </div>
            </div>
            <div class="row-side">
              <template v-if="tab === 'todo'">
                <Tag v-if="!canStart(item)" color="default" :bordered="false">未到预约时间</Tag>
                <Popconfirm
                  v-if="item.timePeriod && !canStart(item)"
                  title="确认取消本次预约吗？"
                  ok-text="取消预约"
                  cancel-text="再想想"
                  @confirm="cancel(item)"
                >
                  <Button>取消预约</Button>
                </Popconfirm>
                <Button type="primary" :disabled="!canStart(item)" @click="enter(item)">
                  {{ item.finishDefense ? "继续面试" : "进入面试" }}
                </Button>
              </template>
              <template v-else-if="tab === 'appoint'">
                <Button type="primary" :disabled="!availableSlots(item)" @click="openAppoint(item)">预约</Button>
              </template>
              <template v-else>
                <span class="muted text-[13px]"><LoadingOutlined class="mr-1" />AI 正在生成报告</span>
                <Button disabled>查看报告</Button>
              </template>
            </div>
          </li>
        </ul>
        <div v-if="total > size" class="flex justify-end px-6 pb-4">
          <Pagination v-model:current="page" :page-size="size" :total="total" size="small" @change="load" />
        </div>
      </Spin>
    </div>

    <AppointmentModal v-model:open="appointOpen" :item="appointItem" @success="refresh" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Button, Empty, Pagination, Popconfirm, Spin, TabPane, Tabs, Tag, message } from "ant-design-vue";
import { CalendarOutlined, LoadingOutlined, ReloadOutlined } from "@ant-design/icons-vue";
import dayjs, { Dayjs } from "dayjs";
import {
  cancelAppointment,
  getAppointmentList,
  getAssessmentAnalysis,
  getAssessmentStatistic,
  getAssessmentTodo,
  getMachineTime,
} from "@/api/studentAssessment";
import AppointmentModal from "./components/AppointmentModal.vue";

type TabKey = "todo" | "appoint" | "analysis";

const router = useRouter();
const tab = ref<TabKey>("todo");
const list = ref<Record<string, any>[]>([]);
const total = ref(0);
const page = ref(1);
const size = 10;
const loading = ref(false);
const stat = ref<Record<string, number>>({});
const statLoaded = ref(false);
const serverTime = ref<Dayjs>(dayjs());
const appointOpen = ref(false);
const appointItem = ref<Record<string, any> | null>(null);
let timer: number | undefined;

const statCards = computed(() => [
  { key: "todo", label: "待参加", value: statLoaded.value ? stat.value.todo ?? 0 : "—" },
  { key: "appoint", label: "待预约", value: statLoaded.value ? stat.value.toAppoint ?? 0 : "—" },
  { key: "analysis", label: "评分中", value: statLoaded.value ? stat.value.analysis ?? 0 : "—" },
  { key: "done", label: "已完成", value: statLoaded.value ? stat.value.done ?? 0 : "—" },
]);

const emptyText = computed(
  () => ({ todo: "暂无待参加的面试", appoint: "暂无需要预约的面试", analysis: "暂无评分中的面试" }[tab.value])
);

const fetchers: Record<TabKey, (d: Record<string, any>) => Promise<any>> = {
  todo: getAssessmentTodo,
  appoint: getAppointmentList,
  analysis: getAssessmentAnalysis,
};

function minutes(sec?: number) {
  if (!sec) return "0 分钟";
  return sec % 60 === 0 ? `${sec / 60} 分钟` : `${sec} 秒`;
}

function metaText(item: Record<string, any>) {
  const parts: string[] = [];
  if (item.defense) parts.push(`答辩 ${minutes(item.duration)}`);
  if (item.question) parts.push(`问答 ${item.questionCount ?? 0} 题 · 每题 ${minutes(item.answerTime)}`);
  if (item.score != null) parts.push(`满分 ${item.score}`);
  return parts.join(" · ");
}

function formatTime(t: string) {
  return t ? dayjs(t).format("YYYY-MM-DD HH:mm") : "-";
}

// 已预约的面试从预约时间开始可以进入；不需要预约的面试随时可以进入
function canStart(item: Record<string, any>) {
  return !item.timePeriod || !serverTime.value.isBefore(dayjs(item.timePeriod));
}

function availableSlots(item: Record<string, any>) {
  return (item.canAppointmentTimes || []).filter((t: Record<string, any>) => !t.full).length;
}

async function loadServerTime() {
  try {
    const res: any = await getMachineTime();
    if (res?.data) serverTime.value = dayjs(res.data, "YYYY-MM-DD HH:mm:ss");
  } catch (e) {
    serverTime.value = dayjs();
  }
}

async function loadStat() {
  try {
    const res: any = await getAssessmentStatistic();
    stat.value = res?.data || {};
    statLoaded.value = true;
  } catch (e) {
    // 统计失败不影响列表
  }
}

async function load() {
  loading.value = true;
  try {
    const res: any = await fetchers[tab.value]({ page: page.value, size });
    list.value = res?.data?.records || [];
    total.value = res?.data?.total || 0;
  } finally {
    loading.value = false;
  }
}

function refresh() {
  loadStat();
  loadServerTime();
  load();
}

function onTabChange() {
  page.value = 1;
  list.value = [];
  load();
}

function onStat(key: string) {
  if (key === "done") {
    router.push("/student/reports");
    return;
  }
  tab.value = key as TabKey;
  onTabChange();
}

async function enter(item: Record<string, any>) {
  await loadServerTime();
  if (!canStart(item)) {
    message.info("还没到预约时间，请稍后再来");
    return;
  }
  router.push({ path: "/interview", query: { id: item.id } });
}

async function cancel(item: Record<string, any>) {
  await cancelAppointment(item.id);
  message.success("已取消预约");
  refresh();
}

function openAppoint(item: Record<string, any>) {
  appointItem.value = item;
  appointOpen.value = true;
}

onMounted(async () => {
  await loadServerTime();
  loadStat();
  load();
  // 在服务器时间基础上本地推进，让“未到预约时间”的按钮按时变为可用
  timer = window.setInterval(() => {
    serverTime.value = serverTime.value.add(30, "second");
  }, 30000);
});

onUnmounted(() => window.clearInterval(timer));
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat {
  padding: 16px 20px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s;
}

.stat:hover,
.stat.is-active {
  border-color: var(--c-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--c-text-3);
}

.stat-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
  color: var(--c-text);
}

.stat.is-active .stat-value {
  color: var(--c-primary);
}

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
  gap: 8px;
}

@media (max-width: 760px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
