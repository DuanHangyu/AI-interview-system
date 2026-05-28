<template>
  <main class="student-page">
    <section class="student-shell">
      <header class="student-topbar">
        <button class="brand-lockup" type="button" @click="handleSideNav('home')">
          <span class="brand-mark">
            <img src="/logo.png" alt="AI 面试学生端" />
          </span>
          <span class="brand-copy">
            <strong>AI 面试学生端</strong>
            <small>Student Interview Workspace</small>
          </span>
        </button>

        <div class="topbar-actions">
          <button
            class="icon-button"
            type="button"
            aria-label="通知"
            @click="noticeOpen = true"
          >
            <BellOutlined />
            <span class="notice-dot" v-if="unreadNoticeCount">{{ unreadNoticeCount }}</span>
          </button>
          <Dropdown placement="bottomRight" trigger="click" :arrow="false">
            <button class="user-chip" type="button">
              <span>{{ studentInitial }}</span>
              <strong>{{ studentName }}</strong>
              <DownOutlined />
            </button>
            <template #overlay>
              <Menu class="student-user-menu" @click="handleUserCommand">
                <MenuItem key="password">修改密码</MenuItem>
                <MenuItem key="logout">退出登录</MenuItem>
              </Menu>
            </template>
          </Dropdown>
        </div>
      </header>

      <div class="student-workspace">
        <aside class="student-sidebar">
          <section class="profile-card">
            <div class="profile-avatar">{{ studentInitial }}</div>
            <h2>{{ studentName }}</h2>
            <p>学号：{{ studentNo }}</p>
            <span>{{ studentStage }}</span>
          </section>

          <nav class="sidebar-nav" aria-label="学生端导航">
            <button
              v-for="item in sidebarNavItems"
              :key="item.key"
              type="button"
              :class="{ active: sidebarActive === item.key }"
              @click="handleSideNav(item.key)"
            >
              <component :is="item.icon" />
              <span>{{ item.label }}</span>
            </button>
          </nav>

          <section class="trust-card">
            <SafetyCertificateOutlined />
            <strong>安全可靠</strong>
            <span>AI 技术保障公平公正</span>
          </section>
        </aside>

        <section class="student-main" v-loading="loading && !list.length">
          <nav class="state-tabs" aria-label="考核状态">
            <button
              v-for="item in tabItems"
              :key="item.key"
              type="button"
              :class="{ active: tab === item.key }"
              @click="changeTab(item.key)"
            >
              <component :is="item.icon" />
              <span>{{ item.label }}</span>
              <strong>{{ tabCount(item.key) }}</strong>
            </button>
          </nav>

          <section class="welcome-card">
            <div class="welcome-copy">
              <span class="soft-pill">欢迎回来</span>
              <h1>{{ studentName }}同学，下午好！</h1>
              <p>在这里完成预约、进入面试、查看分析进度，并复盘每一次考核表现。</p>
            </div>
            <img class="welcome-visual" :src="heroChair" alt="" />
            <div class="progress-dock">
              <div class="progress-percent">
                <small>当前完成度</small>
                <strong>{{ progressPercent }}%</strong>
              </div>
              <div class="progress-track">
                <i :style="{ width: `${progressPercent}%` }"></i>
              </div>
              <div class="progress-total">
                <small>总进度</small>
                <strong>{{ progressDoneCount }}/{{ progressTotalCount }}</strong>
              </div>
            </div>
          </section>

          <section class="next-card">
            <div class="section-title">
              <div>
                <h2>{{ nextSectionTitle }}</h2>
                <p>{{ activeTabInfo?.desc }}</p>
              </div>
              <span>{{ activeTabInfo?.label }}</span>
            </div>

            <div class="next-task" v-if="featuredTask">
              <div class="task-icon">
                <CalendarOutlined v-if="tab === 1" />
                <PlayCircleOutlined v-else-if="tab === 2" />
                <InboxOutlined v-else-if="isAnalysisTask(featuredTask)" />
                <FileSearchOutlined v-else />
              </div>
              <div class="task-copy">
                <span>{{ statusText(featuredTask) }}</span>
                <h3>{{ featuredTask?.theme || "未命名考核" }}</h3>
                <p>考察产品思维、表达结构、分析能力与问题解决能力。</p>
                <div class="task-meta">
                  <small>预计时长 {{ durationLabel(featuredTask) }}</small>
                  <small>{{ modeLabel(featuredTask) }}</small>
                  <small>{{ featuredDeadline }}</small>
                </div>
              </div>
              <div class="task-actions">
                <button
                  class="primary-action"
                  type="button"
                  :disabled="primaryDisabled(featuredTask)"
                  @click="handlePrimaryAction(featuredTask)"
                >
                  {{ primaryActionText(featuredTask) }}
                  <RightOutlined />
                </button>
                <button
                  class="secondary-action"
                  type="button"
                  @click="handleSecondaryAction(featuredTask)"
                >
                  查看详情
                </button>
                <button
                  class="plain-action"
                  type="button"
                  v-if="shouldShowCancel(featuredTask)"
                  @click="cancelSubscribe(featuredTask)"
                >
                  解除预约
                </button>
              </div>
            </div>

            <div class="next-empty" v-else-if="isResult">
              <CalendarOutlined />
              <h3>暂无考核安排</h3>
              <p>当前状态下还没有新的任务，后续考核会在这里展示。</p>
            </div>
          </section>

          <section class="schedule-card">
            <div class="section-title compact">
              <div>
                <h2>近期安排</h2>
                <p>优先展示最近需要处理的面试任务</p>
              </div>
                <button type="button" @click="handleSideNav('assessment')">
                查看全部
                <RightOutlined />
              </button>
            </div>
            <article class="schedule-row" v-if="scheduleTask">
              <time>
                <strong>{{ scheduleDate(scheduleTask).day }}</strong>
                <span>{{ scheduleDate(scheduleTask).week }} {{ scheduleDate(scheduleTask).time }}</span>
              </time>
              <div>
                <h3>{{ scheduleTask?.theme || "待安排考核" }}</h3>
                <p>{{ durationLabel(scheduleTask) }} · {{ modeLabel(scheduleTask) }}</p>
              </div>
              <span>{{ statusText(scheduleTask) }}</span>
            </article>
          </section>

          <section class="quick-grid">
            <article class="quick-card">
              <header>
                <CalendarOutlined />
                <button type="button" @click="handleSideNav('schedule')">
                  查看全部
                  <RightOutlined />
                </button>
              </header>
              <h3>日程安排</h3>
              <strong>{{ scheduleDate(scheduleTask).day }}</strong>
              <p>{{ scheduleTask?.theme || "暂无预约任务" }}</p>
              <span>{{ activeTabInfo?.label }}</span>
            </article>

            <article class="quick-card">
              <header>
                <FileSearchOutlined />
                <button type="button" @click="handleSideNav('report')">
                  查看全部
                  <RightOutlined />
                </button>
              </header>
              <h3>报告与复盘</h3>
              <div class="mini-list">
                <div v-for="item in reportPreview" :key="item?.id">
                  <span>{{ item?.theme || "未命名考核" }}</span>
                  <small>{{ statusText(item) }}</small>
                </div>
                <p class="mini-empty" v-if="!reportPreview.length">暂无报告</p>
              </div>
            </article>

            <article class="quick-card device-card">
              <header>
                <VideoCameraOutlined />
                <button type="button" @click="openDeviceCheck">
                  查看详情
                  <RightOutlined />
                </button>
              </header>
              <h3>设备检测</h3>
              <p>检测摄像头、麦克风、扬声器与网络状态。</p>
              <div class="device-icons">
                <span v-for="item in deviceItems" :key="item.label">
                  <component :is="item.icon" />
                  <i></i>
                  <small>{{ item.label }}</small>
                </span>
              </div>
              <button class="device-action" type="button" @click="openDeviceCheck">
                设备检测
              </button>
            </article>
          </section>

          <div class="student-pagination" v-if="total > 6">
            <button type="button" :disabled="page <= 1" @click="preFn">
              <LeftOutlined />
            </button>
            <span>{{ page }} / {{ Math.ceil(total / 6) }}</span>
            <button type="button" :disabled="page * 6 >= total" @click="nextFn">
              <RightOutlined />
            </button>
          </div>
        </section>

        <aside class="progress-panel">
          <section class="progress-card">
            <header>
              <span>考核进度</span>
              <strong>{{ progressDoneCount }}/{{ progressTotalCount }}</strong>
            </header>
            <div class="dark-progress">
              <i :style="{ width: `${progressPercent}%` }"></i>
            </div>
            <p>{{ progressPercent }}% 已完成</p>
          </section>

          <section class="flow-card">
            <h3>考核流程</h3>
            <div class="flow-list">
              <div
                v-for="item in tabItems"
                :key="item.key"
                :class="{ active: tab === item.key }"
              >
                <span>
                  <CheckCircleOutlined />
                </span>
                <strong>{{ item.label }}</strong>
                <small>{{ item.desc }}</small>
              </div>
            </div>
          </section>

          <section class="todo-card">
            <header>
              <h3>待办任务</h3>
              <span>{{ todoItems.length }} 项</span>
            </header>
            <div class="todo-list">
              <article v-for="item in todoItems" :key="item.title">
                <span>
                  <component :is="item.icon" />
                </span>
                <div>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.desc }}</small>
                </div>
                <time>{{ item.time }}</time>
              </article>
            </div>
            <button type="button" @click="handleSideNav('assessment')">
              查看全部任务
              <RightOutlined />
            </button>
          </section>
        </aside>
      </div>
    </section>

    <AppointModal
      ref="appointRef"
      @success="() => refreshDashboard({ forceStatistic: true })"
    />
    <UpdatePassword ref="updatePass" />

    <Modal
      v-model:open="noticeOpen"
      title="消息通知"
      :footer="null"
      width="520px"
      class="student-action-modal"
    >
      <div class="modal-list">
        <div class="notice-toolbar">
          <span>{{ unreadNoticeCount ? `${unreadNoticeCount} 条未读消息` : "消息已全部读完" }}</span>
          <button type="button" :disabled="!unreadNoticeCount" @click="markAllNoticesRead">
            全部已读
          </button>
        </div>
        <article
          v-for="item in noticeItems"
          :key="item.id"
          :class="{ read: isNoticeRead(item.id) }"
        >
          <span>
            <component :is="item.icon" />
          </span>
          <div>
            <strong>{{ item.title }}</strong>
            <small>{{ item.desc }}</small>
          </div>
          <div class="notice-actions">
            <button
              type="button"
              class="read-action"
              :disabled="isNoticeRead(item.id)"
              @click="markNoticeRead(item.id)"
            >
              {{ isNoticeRead(item.id) ? "已读" : "标为已读" }}
            </button>
            <button type="button" @click="handleNoticeAction(item)">处理</button>
          </div>
        </article>
      </div>
    </Modal>

    <Modal
      v-model:open="taskDetailOpen"
      title="考核详情"
      :footer="null"
      width="620px"
      class="student-action-modal"
    >
      <section class="task-detail-modal" v-if="detailTask">
        <header>
          <span>{{ statusText(detailTask) }}</span>
          <h3>{{ detailTask?.theme || "未命名考核" }}</h3>
          <p>考察产品思维、表达结构、分析能力与问题解决能力。</p>
        </header>
        <div class="detail-grid-list">
          <div>
            <small>考核模式</small>
            <strong>{{ modeLabel(detailTask) }}</strong>
          </div>
          <div>
            <small>预计时长</small>
            <strong>{{ durationLabel(detailTask) }}</strong>
          </div>
          <div>
            <small>考核时间</small>
            <strong>{{ detailTimeLabel(detailTask) }}</strong>
          </div>
          <div>
            <small>当前状态</small>
            <strong>{{ statusText(detailTask) }}</strong>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" class="secondary-action" @click="taskDetailOpen = false">
            关闭
          </button>
          <button
            type="button"
            class="primary-action"
            :disabled="primaryDisabled(detailTask)"
            @click="handleDetailPrimary"
          >
            {{ primaryActionText(detailTask) }}
            <RightOutlined />
          </button>
        </div>
      </section>
    </Modal>

    <Modal
      v-model:open="deviceOpen"
      title="设备检测"
      :footer="null"
      width="620px"
      class="student-action-modal"
    >
      <section class="device-modal">
        <p>进入正式面试前，请确认浏览器可以访问摄像头、麦克风，且网络连接正常。</p>
        <div class="device-check-list">
          <article v-for="item in deviceCheckItems" :key="item.key">
            <span :class="item.state">
              <component :is="item.icon" />
            </span>
            <div>
              <strong>{{ item.label }}</strong>
              <small>{{ item.message }}</small>
            </div>
          </article>
        </div>
        <div class="modal-actions">
          <button type="button" class="secondary-action" @click="deviceOpen = false">
            关闭
          </button>
          <button
            type="button"
            class="primary-action"
            :disabled="deviceChecking"
            @click="runDeviceCheck"
          >
            {{ deviceChecking ? "检测中..." : "开始检测" }}
            <RightOutlined />
          </button>
        </div>
      </section>
    </Modal>

    <Modal
      v-model:open="helpOpen"
      title="帮助中心"
      :footer="null"
      width="580px"
      class="student-action-modal"
    >
      <section class="help-modal">
        <article v-for="item in helpItems" :key="item.title">
          <strong>{{ item.title }}</strong>
          <p>{{ item.desc }}</p>
        </article>
      </section>
    </Modal>
  </main>
</template>

<script setup lang="ts">
import {
  getAssessmentTodo,
  getAssessmentAnalysis,
  getAssessmentDone,
  getAppointmentList,
  getAssessmentStatistic,
  getMachineTime,
  cancelAppointment,
} from "@/api/studentAssessment";
import dayjs from "dayjs";
import { computed, createVNode, markRaw, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import AppointModal from "./AppointModal.vue";
import UpdatePassword from "@/components/Layout/componets/UpdatePassword.vue";
import { Dropdown, Menu, MenuItem, message, Modal } from "ant-design-vue";
import heroChair from "@/assets/student-dashboard/hero-chair.jpg";
import {
  AudioOutlined,
  BellOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CustomerServiceOutlined,
  DownOutlined,
  ExclamationCircleOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  GlobalOutlined,
  HomeOutlined,
  InboxOutlined,
  LeftOutlined,
  PlayCircleOutlined,
  ProfileOutlined,
  QuestionCircleOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
  ScheduleOutlined,
  SettingOutlined,
  VideoCameraOutlined,
  WifiOutlined,
} from "@ant-design/icons-vue";

const tabItems = [
  {
    label: "待预约",
    desc: "选择考核时间",
    key: 1,
    icon: markRaw(CalendarOutlined),
  },
  {
    label: "待完成",
    desc: "按时进入面试",
    key: 2,
    icon: markRaw(ClockCircleOutlined),
  },
  {
    label: "已结束",
    desc: "查看评估状态与复盘报告",
    key: 4,
    icon: markRaw(CheckCircleOutlined),
  },
];

const sidebarNavItems = [
  { key: "home", label: "首页", icon: markRaw(HomeOutlined) },
  { key: "assessment", label: "我的考核", icon: markRaw(ProfileOutlined) },
  { key: "report", label: "我的报告", icon: markRaw(FileTextOutlined) },
  { key: "schedule", label: "日程安排", icon: markRaw(ScheduleOutlined) },
  { key: "device", label: "设备检测", icon: markRaw(VideoCameraOutlined) },
  { key: "help", label: "帮助中心", icon: markRaw(CustomerServiceOutlined) },
  { key: "setting", label: "设置", icon: markRaw(SettingOutlined) },
];

const deviceItems = [
  { label: "摄像头", icon: markRaw(VideoCameraOutlined) },
  { label: "麦克风", icon: markRaw(AudioOutlined) },
  { label: "扬声器", icon: markRaw(GlobalOutlined) },
  { label: "网络", icon: markRaw(WifiOutlined) },
];

const NOTICE_STORAGE_KEY = "student-dashboard-read-notices";
const tab = ref(1);
const sidebarActive = ref("home");
const noticeOpen = ref(false);
const taskDetailOpen = ref(false);
const deviceOpen = ref(false);
const helpOpen = ref(false);
const deviceChecking = ref(false);
const detailTask = ref<Recordable | null>(null);
const updatePass = ref();
const statisticLoaded = ref(false);
const statisticLoading = ref(false);
const readNoticeIds = ref<string[]>([]);
const statistic = ref<Recordable>({
  all: 0,
  toAppoint: 0,
  todo: 0,
  analysis: 0,
  done: 0,
});
const deviceState = ref<Record<string, "idle" | "success" | "warning">>({
  camera: "idle",
  microphone: "idle",
  speaker: "idle",
  network: "idle",
});
const activeTabInfo = computed(() =>
  tabItems.find((item) => item.key === tab.value)
);
const listCache = ref<Record<number, { records: Recordable[]; total: number }>>({});
const requestId = ref(0);

const changeTab = (e: number, options: { force?: boolean } = {}) => {
  if (tab.value === e && !options.force) return;
  tab.value = e;
  page.value = 1;
  const cached = listCache.value[e];
  if (cached && !options.force) {
    list.value = cached.records;
    total.value = cached.total;
    isResult.value = true;
    onList({ silent: true });
    return;
  }
  list.value = [];
  total.value = 0;
  onList({ force: options.force });
};

const appointRef = ref();
const loading = ref(false);
const isResult = ref(false);
const list = ref<Recordable[]>([]);
const total = ref(0);
const page = ref(1);
const serverTime = ref(dayjs());

const getResponseRecords = (res: Recordable) => res?.data?.records || [];
const getResponseTotal = (res: Recordable) => Number(res?.data?.total || 0);
const withDashboardStatus = (
  records: Recordable[],
  status: "analysis" | "done"
) => records.map((item) => ({ ...item, _dashboardStatus: status }));

const fetchDashboardList = async (
  currentTab: number,
  params: Record<string, any>
) => {
  if (currentTab === 4) {
    const [analysisRes, doneRes] = await Promise.all([
      getAssessmentAnalysis(params),
      getAssessmentDone(params),
    ]);
    const analysisRecords = withDashboardStatus(
      getResponseRecords(analysisRes),
      "analysis"
    );
    const doneRecords = withDashboardStatus(getResponseRecords(doneRes), "done");
    return {
      records: [...analysisRecords, ...doneRecords].slice(0, params.size),
      total: getResponseTotal(analysisRes) + getResponseTotal(doneRes),
    };
  }

  const requestMap: Record<number, (params: Record<string, any>) => Promise<any>> = {
    1: getAppointmentList,
    2: getAssessmentTodo,
  };
  const res = await requestMap[currentTab](params);
  return {
    records: getResponseRecords(res),
    total: getResponseTotal(res),
  };
};

const onList = (options: { force?: boolean; silent?: boolean } = {}) => {
  const currentTab = tab.value;
  const currentRequest = requestId.value + 1;
  requestId.value = currentRequest;
  const cached = listCache.value[currentTab];
  loading.value = !options.silent && (!cached || options.force);
  isResult.value = !!(options.silent && cached);
  const params = {
    page: page.value,
    size: 6,
  };

  fetchDashboardList(currentTab, params)
    .then(({ records, total: nextTotal }) => {
      if (requestId.value !== currentRequest) return;
      list.value = records;
      total.value = nextTotal;
      if (page.value === 1) {
        listCache.value[currentTab] = {
          records,
          total: nextTotal,
        };
      }
    })
    .catch(() => {
      if (requestId.value !== currentRequest) return;
      if (!cached) {
        list.value = [];
        total.value = 0;
      }
    })
    .finally(() => {
      if (requestId.value !== currentRequest) return;
      loading.value = false;
      isResult.value = true;
    });
};

const refreshDashboard = (options: { forceStatistic?: boolean } = {}) => {
  listCache.value = {};
  onList({ force: true });
  if (options.forceStatistic) {
    loadStatistic({ force: true });
  }
};

const refreshMachineTime = async () => {
  const res = await getMachineTime();
  if (res?.data) {
    serverTime.value = dayjs(res.data, "YYYY-MM-DD HH:mm:ss");
  }
};

const router = useRouter();
const authStore = useAuthStore();

const studentName = computed(() => authStore?.info?.name || "同学");
const studentInitial = computed(() => studentName.value.slice(0, 1).toUpperCase());
const studentNo = computed(
  () => authStore?.info?.studentNo || authStore?.info?.account || "S2024001024"
);
const studentStage = computed(
  () => authStore?.info?.grade || authStore?.info?.className || "本科 · 大三"
);
const featuredTask = computed(() => list.value?.[0] || null);
const scheduleTask = computed(() =>
  tab.value === 4 ? null : list.value?.[1] || featuredTask.value
);
const reportPreview = computed(() => (tab.value === 4 ? list.value.slice(0, 3) : []));
const progressTotalCount = computed(() => {
  if (statisticLoaded.value) return Number(statistic.value?.all || 0);
  return tab.value === 4 ? total.value : 0;
});
const progressDoneCount = computed(() => {
  const doneCount = statisticLoaded.value
    ? Number(statistic.value?.done || 0)
    : list.value.filter((item) => item?._dashboardStatus === "done").length;
  return Math.min(progressTotalCount.value, doneCount);
});
const progressPercent = computed(() => {
  if (!progressTotalCount.value) return 0;
  return Math.min(
    100,
    Math.round((progressDoneCount.value / progressTotalCount.value) * 100)
  );
});
const featuredDeadline = computed(() => {
  if (!featuredTask.value?.timePeriod) return "等待通知";
  const time = dayjs(featuredTask.value.timePeriod).format("YYYY-MM-DD HH:mm");
  if (tab.value === 1) return `预约时间 ${time}`;
  if (tab.value === 2) return `考核时间 ${time}`;
  if (isAnalysisTask(featuredTask.value)) return `提交时间 ${time}`;
  return `完成时间 ${time}`;
});
const nextSectionTitle = computed(() => {
  if (tab.value === 1) return "待预约考核";
  if (tab.value === 2) return "待完成考核";
  return "评估与复盘";
});
const todoItems = computed(() => {
  const items = [];
  const appointmentCount = tabCount(1);
  const todoCount = tabCount(2);
  if (appointmentCount > 0) {
    items.push({
      title: tab.value === 1 && featuredTask.value?.theme
        ? `预约 ${featuredTask.value.theme}`
        : "预约待处理考核",
      desc: `${appointmentCount} 场考核等待预约`,
      time: "待处理",
      icon: markRaw(CalendarOutlined),
    });
  }
  if (todoCount > 0) {
    items.push({
      title: "进入待完成面试",
      desc: `${todoCount} 场面试需要按时进入`,
      time: "待开始",
      icon: markRaw(PlayCircleOutlined),
    });
  }
  items.push({
    title: "完成设备检测",
    desc: "进入面试前建议确认设备状态",
    time: "建议",
    icon: markRaw(VideoCameraOutlined),
  });
  if (items.length === 1) {
    items.unshift({
      title: "暂无待办考核",
      desc: "当前没有需要处理的面试任务",
      time: "已同步",
      icon: markRaw(CheckCircleOutlined),
    });
  }
  return items;
});

const tabCount = (key: number) => {
  const countMap: Record<number, number> = {
    1: Number(statistic.value?.toAppoint || 0),
    2: Number(statistic.value?.todo || 0),
    4:
      Number(statistic.value?.analysis || 0) +
      Number(statistic.value?.done || 0),
  };
  if (statisticLoaded.value) return countMap[key] || 0;
  return tab.value === key ? total.value : 0;
};

const noticeItems = computed<Recordable[]>(() => {
  const items: Recordable[] = [];
  const appointmentCount = tabCount(1);
  const todoCount = tabCount(2);
  const analysisCount = Number(statistic.value?.analysis || 0);

  if (appointmentCount > 0) {
    items.push({
      id: "appointment",
      title: "待预约考核",
      desc: `${appointmentCount} 场考核等待预约时间`,
      icon: markRaw(CalendarOutlined),
      action: () => {
        noticeOpen.value = false;
        handleSideNav("schedule");
      },
    });
  }

  if (todoCount > 0) {
    items.push({
      id: "todo",
      title: "待完成面试",
      desc: `${todoCount} 场面试需要按时进入`,
      icon: markRaw(PlayCircleOutlined),
      action: () => {
        noticeOpen.value = false;
        handleSideNav("assessment");
      },
    });
  }

  if (analysisCount > 0) {
    items.push({
      id: "analysis",
      title: "报告生成中",
      desc: `${analysisCount} 份报告正在评估，完成后可查看复盘`,
      icon: markRaw(InboxOutlined),
      action: () => {
        noticeOpen.value = false;
        handleSideNav("report");
      },
    });
  }

  items.push({
    id: "device",
    title: "设备检测",
    desc: "建议在进入面试前完成浏览器设备检测",
    icon: markRaw(VideoCameraOutlined),
    action: () => {
      noticeOpen.value = false;
      openDeviceCheck();
    },
  });

  return items;
});

const isNoticeRead = (id: string) => readNoticeIds.value.includes(id);
const unreadNoticeCount = computed(
  () => noticeItems.value.filter((item) => !isNoticeRead(item.id)).length
);

const saveReadNoticeIds = () => {
  localStorage.setItem(NOTICE_STORAGE_KEY, JSON.stringify(readNoticeIds.value));
};

const markNoticeRead = (id: string) => {
  if (isNoticeRead(id)) return;
  readNoticeIds.value = [...readNoticeIds.value, id];
  saveReadNoticeIds();
};

const markAllNoticesRead = () => {
  readNoticeIds.value = Array.from(new Set(noticeItems.value.map((item) => item.id)));
  saveReadNoticeIds();
};

const handleNoticeAction = (item: Recordable) => {
  markNoticeRead(item.id);
  item.action?.();
};

const helpItems = [
  {
    title: "如何预约面试？",
    desc: "进入“待预约”列表，点击对应考核的立即预约，选择可用时间段后提交。",
  },
  {
    title: "什么时候可以开始考核？",
    desc: "已预约考核会在“待完成”中显示，到达预约时间后按钮会变为可开始。",
  },
  {
    title: "报告在哪里看？",
    desc: "考核结束后会进入“已结束”，分析完成前显示正在评估，完成后可查看复盘报告。",
  },
];

const deviceCheckItems = computed(() => [
  {
    key: "camera",
    label: "摄像头",
    icon: markRaw(VideoCameraOutlined),
    state: deviceState.value.camera,
    message:
      deviceState.value.camera === "success"
        ? "已检测到摄像头设备"
        : deviceState.value.camera === "warning"
          ? "未检测到摄像头或浏览器未授权"
          : "等待检测",
  },
  {
    key: "microphone",
    label: "麦克风",
    icon: markRaw(AudioOutlined),
    state: deviceState.value.microphone,
    message:
      deviceState.value.microphone === "success"
        ? "已检测到麦克风设备"
        : deviceState.value.microphone === "warning"
          ? "未检测到麦克风或浏览器未授权"
          : "等待检测",
  },
  {
    key: "speaker",
    label: "扬声器",
    icon: markRaw(GlobalOutlined),
    state: deviceState.value.speaker,
    message:
      deviceState.value.speaker === "success"
        ? "浏览器支持音频播放"
        : "等待检测",
  },
  {
    key: "network",
    label: "网络",
    icon: markRaw(WifiOutlined),
    state: deviceState.value.network,
    message:
      deviceState.value.network === "success"
        ? "网络连接正常"
        : deviceState.value.network === "warning"
          ? "当前浏览器处于离线状态"
          : "等待检测",
  },
]);

const loadStatistic = (options: { force?: boolean } = {}) => {
  if (statisticLoading.value) return;
  if (statisticLoaded.value && !options.force) return;
  statisticLoading.value = true;
  getAssessmentStatistic()
    .then((res) => {
      statistic.value = {
        all: res?.data?.all || 0,
        toAppoint: res?.data?.toAppoint || 0,
        todo: res?.data?.todo || 0,
        analysis: res?.data?.analysis || 0,
        done: res?.data?.done || 0,
      };
      statisticLoaded.value = true;
    })
    .catch(() => {
      // 列表接口仍可正常使用，统计失败时保留列表数量兜底。
    })
    .finally(() => {
      statisticLoading.value = false;
    });
};

const handleSideNav = (key: string) => {
  sidebarActive.value = key;
  if (key === "home") {
    changeTab(1);
    return;
  }
  if (key === "assessment") {
    changeTab(2);
    return;
  }
  if (key === "report") {
    changeTab(4);
    return;
  }
  if (key === "schedule") {
    changeTab(1);
    return;
  }
  if (key === "device") {
    openDeviceCheck();
    return;
  }
  if (key === "help") {
    helpOpen.value = true;
    return;
  }
  if (key === "setting") {
    updatePass.value?.openModal();
  }
};

const handleUserCommand = ({ key }: { key: string | number }) => {
  if (key === "password") {
    updatePass.value?.openModal();
    return;
  }
  Modal.confirm({
    title: "确认退出登录吗？",
    icon: createVNode(ExclamationCircleOutlined, { class: "!text-[#e75f49]" }),
    okText: "退出登录",
    cancelText: "取消",
    onOk: async () => {
      await authStore.logout();
      window.location.href = "/login";
    },
  });
};

const startFn = async (e: Recordable) => {
  try {
    await refreshMachineTime();
  } catch (error) {
    // 时间接口失败时使用本地已缓存时间，避免阻断考核入口。
  }
  if (serverTime.value?.isBefore(dayjs(e?.timePeriod))) return;
  if (e?.defense && e?.question) {
    if (e?.finishDefense) {
      router.push(`/question?id=${e?.id}`);
    } else {
      router.push(`/dashboard?id=${e?.id}`);
    }
  } else if (e?.defense) {
    if (!e?.finishDefense) {
      router.push(`/dashboard?id=${e?.id}`);
    } else {
      router.push({
        name: "ResultOverPage",
      });
    }
  } else {
    router.push(`/question?id=${e?.id}`);
  }
};

const toAppoint = (e: Record<string, any>) => {
  appointRef.value?.openModal(e);
};

const toDetail = (e: Record<string, any>) => {
  if (isAnalysisTask(e)) {
    message.info("系统正在生成评估报告，请稍后查看");
    return;
  }
  if (e?.state == 2) {
    router.push(`/studyDetail?id=${e?.id}`);
  }
  if (e?.state == 3 || e?.state == 5) {
    return;
  }
  if (e?.state == 4) {
    toAppoint(e);
  }
};

const openTaskDetail = (item: Recordable) => {
  detailTask.value = item;
  taskDetailOpen.value = true;
};

const handleDetailPrimary = () => {
  if (!detailTask.value) return;
  const currentTask = detailTask.value;
  taskDetailOpen.value = false;
  handlePrimaryAction(currentTask);
};

const canStart = (item: Recordable) => {
  return !serverTime.value?.isBefore(dayjs(item?.timePeriod));
};

const shouldShowCancel = (item: Recordable) => {
  return tab.value == 2 && !canStart(item) && item?.timePeriod;
};

const canReappoint = (item: Recordable) => {
  return (
    item.state == 4 ||
    (item?.appointRecordFlag &&
      item.state != 3 &&
      item.state != 5 &&
      typeof item?.defenseScore == "number" &&
      item?.defenseScore < item?.passScore)
  );
};

const isReappointLocked = (item: Recordable) => {
  return item.state == 3 || item.state == 5;
};

const isAnalysisTask = (item?: Recordable | null) => {
  return item?._dashboardStatus === "analysis";
};

const statusText = (item: Recordable) => {
  if (isAnalysisTask(item)) return "正在评估";
  if (tab.value == 1) return "待预约";
  if (tab.value == 2) return canStart(item) ? "可开始" : "已预约";
  if (item?.state == 2) return "已完成";
  if (isReappointLocked(item)) return "暂不可约";
  if (canReappoint(item)) return "可重约";
  return "已结束";
};

const primaryActionText = (item: Recordable) => {
  if (!item) return "暂无任务";
  if (tab.value === 1) return "立即预约";
  if (tab.value === 2) return canStart(item) ? "开始考核" : "未到时间";
  if (isAnalysisTask(item)) return "正在评估";
  if (item?.state === 2) return "查看报告";
  if (canReappoint(item)) return "重新预约";
  return "查看详情";
};

const primaryDisabled = (item: Recordable) => {
  return (tab.value === 2 && item && !canStart(item)) || isAnalysisTask(item);
};

const handlePrimaryAction = (item: Recordable) => {
  if (!item) return;
  if (tab.value === 1) {
    toAppoint(item);
    return;
  }
  if (tab.value === 2) {
    startFn(item);
    return;
  }
  if (isAnalysisTask(item)) return;
  if (item?.state === 2) {
    toDetail(item);
    return;
  }
  if (canReappoint(item)) {
    toAppoint(item);
    return;
  }
  message.info("暂无可操作事项");
};

const handleSecondaryAction = (item: Recordable) => {
  if (!item) return;
  if (tab.value === 4 && item?.state === 2) {
    toDetail(item);
    return;
  }
  openTaskDetail(item);
};

const durationLabel = (item?: Recordable | null) => {
  if (!item) return "暂无时长";
  const seconds = Number(item?.duration || item?.answerTime || 0);
  if (!seconds) return "60 分钟";
  if (seconds >= 60) return `${Math.ceil(seconds / 60)} 分钟`;
  return `${seconds} 秒`;
};

const modeLabel = (item?: Recordable | null) => {
  if (!item) return "AI 面试";
  if (item?.defense && item?.question) return "答辩 + 问答";
  if (item?.defense) return "AI 答辩";
  if (item?.question) return "AI 问答";
  return "AI 面试";
};

const scheduleDate = (item?: Recordable | null) => {
  if (!item?.timePeriod) {
    return {
      day: "--/--",
      week: "暂无",
      time: "--:--",
    };
  }
  const value = item?.timePeriod ? dayjs(item.timePeriod) : dayjs();
  return {
    day: value.format("MM/DD"),
    week: value.format("ddd"),
    time: value.format("HH:mm"),
  };
};

const detailTimeLabel = (item?: Recordable | null) => {
  if (!item?.timePeriod) return "等待通知";
  return dayjs(item.timePeriod).format("YYYY-MM-DD HH:mm");
};

const openDeviceCheck = () => {
  deviceOpen.value = true;
  if (Object.values(deviceState.value).every((item) => item === "idle")) {
    runDeviceCheck();
  }
};

const runDeviceCheck = async () => {
  deviceChecking.value = true;
  try {
    const devices = await navigator.mediaDevices?.enumerateDevices?.();
    const deviceList = devices || [];
    deviceState.value.camera = deviceList.some((item) => item.kind === "videoinput")
      ? "success"
      : "warning";
    deviceState.value.microphone = deviceList.some(
      (item) => item.kind === "audioinput"
    )
      ? "success"
      : "warning";
    deviceState.value.speaker =
      typeof Audio !== "undefined" ? "success" : "warning";
    deviceState.value.network = navigator.onLine ? "success" : "warning";
    if (Object.values(deviceState.value).every((item) => item === "success")) {
      message.success("设备检测通过");
    } else {
      message.warning("部分设备未检测通过，请检查浏览器授权或设备连接");
    }
  } catch (error) {
    deviceState.value = {
      camera: "warning",
      microphone: "warning",
      speaker: typeof Audio !== "undefined" ? "success" : "warning",
      network: navigator.onLine ? "success" : "warning",
    };
    message.warning("无法读取浏览器设备权限，请检查授权设置");
  } finally {
    deviceChecking.value = false;
  }
};

onMounted(() => {
  try {
    readNoticeIds.value = JSON.parse(
      localStorage.getItem(NOTICE_STORAGE_KEY) || "[]"
    );
  } catch (error) {
    readNoticeIds.value = [];
  }
  onList();
  loadStatistic();
  refreshMachineTime();
});

const preFn = () => {
  if (page.value <= 1) return;
  page.value -= 1;
  onList({ force: true });
};
const nextFn = () => {
  if (page.value * 6 >= total.value) return;
  page.value += 1;
  onList({ force: true });
};

const cancelSubscribe = (e: Recordable) => {
  Modal.confirm({
    title: "确认解除预约吗？",
    icon: createVNode(ExclamationCircleOutlined, { class: "!text-[#e75f49]" }),
    okText: "解除预约",
    onOk: async () => {
      await cancelAppointment(e?.id);
      message.success("已解除预约");
      refreshDashboard({ forceStatistic: true });
    },
  });
};
</script>

<style scoped>
.student-page {
  min-height: 100vh;
  padding: 28px;
  overflow-y: auto;
  background:
    radial-gradient(circle at 78% 16%, rgba(255, 226, 112, 0.2), transparent 24%),
    radial-gradient(circle at 18% 10%, rgba(255, 255, 255, 0.22), transparent 28%),
    linear-gradient(180deg, #bebdb9 0%, #a8a7a2 58%, #8f8e8a 100%);
  color: #17181a;
}

.student-shell {
  width: min(1720px, 100%);
  min-height: calc(100vh - 56px);
  margin: 0 auto;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 28px;
  background:
    radial-gradient(circle at 78% 14%, rgba(255, 229, 108, 0.34), transparent 26%),
    linear-gradient(125deg, rgba(245, 246, 241, 0.92), rgba(255, 250, 219, 0.82)),
    rgba(246, 246, 239, 0.92);
  box-shadow:
    0 34px 92px rgba(31, 32, 30, 0.26),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.student-topbar {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
}

.brand-lockup,
.user-chip,
.icon-button,
.sidebar-nav button,
.state-tabs button,
.section-title button,
.primary-action,
.secondary-action,
.plain-action,
.todo-card > button,
.student-pagination button,
.device-action {
  border: 0;
  font-family: inherit;
  cursor: pointer;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  background: transparent;
  color: #17181a;
  text-align: left;
}

.brand-mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(145deg, #3f403d, #232422);
  box-shadow: 0 12px 24px rgba(31, 32, 29, 0.18);
}

.brand-mark img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.brand-copy strong,
.brand-copy small {
  display: block;
}

.brand-copy strong {
  font-size: 22px;
  font-weight: 850;
  letter-spacing: 0;
}

.brand-copy small {
  margin-top: 3px;
  color: #7a7c75;
  font-size: 12px;
  font-weight: 700;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.icon-button {
  width: 46px;
  height: 46px;
  position: relative;
  display: grid;
  place-items: center;
  border: 1px solid rgba(23, 24, 26, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 247, 0.72);
  color: #232422;
  font-size: 20px;
}

.notice-dot {
  min-width: 18px;
  height: 18px;
  position: absolute;
  top: -1px;
  right: -1px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #e75f49;
  color: #fff;
  font-size: 11px;
  font-weight: 850;
}

.user-chip {
  height: 48px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 5px 12px 5px 5px;
  border: 1px solid rgba(23, 24, 26, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 247, 0.76);
  color: #17181a;
}

.user-chip span {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #2f302f;
  color: #fff;
  font-size: 18px;
  font-weight: 850;
}

.user-chip strong {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-workspace {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 340px;
  gap: 18px;
}

.student-sidebar,
.student-main,
.progress-panel {
  min-width: 0;
}

.student-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 24px;
  background:
    radial-gradient(circle at 30% 0%, rgba(255, 255, 255, 0.12), transparent 30%),
    linear-gradient(180deg, #555652 0%, #3b3c39 46%, #2d2e2b 100%);
  box-shadow:
    0 24px 60px rgba(37, 38, 35, 0.26),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
}

.profile-card {
  padding: 8px 6px 4px;
  text-align: center;
}

.profile-avatar {
  width: 88px;
  height: 88px;
  display: grid;
  place-items: center;
  margin: 18px auto 14px;
  border-radius: 999px;
  background:
    radial-gradient(circle at 35% 28%, rgba(255, 255, 255, 0.78), transparent 24%),
    linear-gradient(145deg, #f8f8f0, #d9d9d2);
  color: #2d2e2b;
  box-shadow:
    0 18px 38px rgba(20, 21, 20, 0.24),
    inset 0 0 0 1px rgba(255, 255, 255, 0.52);
  font-size: 42px;
  font-weight: 850;
}

.profile-card h2 {
  margin: 0;
  color: #f7f7ef;
  font-size: 24px;
  font-weight: 850;
}

.profile-card p {
  margin: 8px 0 10px;
  color: rgba(247, 247, 239, 0.58);
  font-size: 13px;
  font-weight: 700;
}

.profile-card > span,
.soft-pill,
.next-card .section-title > span,
.schedule-row > span,
.quick-card > span,
.mini-list small {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(244, 207, 85, 0.32);
  color: #7b6720;
  font-size: 12px;
  font-weight: 850;
}

.sidebar-nav {
  display: grid;
  gap: 7px;
}

.sidebar-nav button {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  color: rgba(247, 247, 239, 0.58);
  font-size: 15px;
  font-weight: 750;
  text-align: left;
}

.sidebar-nav button.active,
.sidebar-nav button:hover {
  background: rgba(255, 255, 255, 0.13);
  color: #fff;
  box-shadow:
    inset 3px 0 0 #f4cf55,
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.trust-card {
  min-height: 138px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 7px;
  margin-top: auto;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 18px;
  background: rgba(26, 27, 25, 0.22);
}

.trust-card :deep(.anticon) {
  color: #f4cf55;
  font-size: 22px;
}

.trust-card strong {
  color: #fff;
  font-size: 15px;
  font-weight: 850;
}

.trust-card span {
  color: rgba(247, 247, 239, 0.54);
  font-size: 12px;
  line-height: 1.5;
}

.student-main {
  display: grid;
  gap: 14px;
}

.state-tabs {
  width: fit-content;
  max-width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  overflow-x: auto;
  border: 1px solid rgba(23, 24, 26, 0.06);
  border-radius: 999px;
  background: rgba(255, 255, 247, 0.78);
}

.state-tabs button {
  height: 48px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-radius: 999px;
  background: transparent;
  color: #2f302f;
  font-size: 15px;
  font-weight: 800;
  white-space: nowrap;
}

.state-tabs button strong {
  min-width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(47, 48, 47, 0.1);
  font-size: 14px;
}

.state-tabs button.active {
  background: #2f302f;
  color: #fff;
  box-shadow: 0 16px 34px rgba(32, 33, 31, 0.2);
}

.state-tabs button.active strong {
  background: #fff;
  color: #2f302f;
}

.welcome-card,
.next-card,
.schedule-card,
.quick-card {
  border: 1px solid rgba(23, 24, 26, 0.06);
  border-radius: 24px;
  background: rgba(255, 255, 247, 0.72);
  box-shadow:
    0 16px 42px rgba(45, 46, 43, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.welcome-card {
  min-height: 300px;
  position: relative;
  overflow: hidden;
  padding: 28px 30px 108px;
}

.welcome-copy {
  max-width: 560px;
  position: relative;
  z-index: 2;
}

.welcome-copy h1 {
  margin: 16px 0 10px;
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.1;
  font-weight: 850;
  letter-spacing: 0;
}

.welcome-copy p {
  margin: 0;
  color: #686b63;
  font-size: 15px;
  line-height: 1.75;
}

.welcome-visual {
  position: absolute;
  right: -26px;
  top: 0;
  width: min(440px, 46%);
  height: 190px;
  object-fit: cover;
  object-position: right center;
  mix-blend-mode: multiply;
  opacity: 0.78;
}

.progress-dock {
  min-height: 70px;
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 18px;
  z-index: 3;
  display: grid;
  grid-template-columns: 112px minmax(160px, 1fr) 110px;
  align-items: center;
  gap: 18px;
  padding: 10px 16px 10px 10px;
  border: 1px solid rgba(23, 24, 26, 0.06);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(14px);
}

.progress-percent {
  display: grid;
  place-items: center;
  min-height: 54px;
  border-radius: 14px;
  background: #2f302f;
  color: #f4cf55;
}

.progress-percent small,
.progress-total small {
  color: inherit;
  font-size: 11px;
  font-weight: 750;
}

.progress-percent strong,
.progress-total strong {
  display: block;
  font-size: 24px;
  font-weight: 850;
  line-height: 1;
}

.progress-track,
.dark-progress {
  height: 16px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(47, 48, 47, 0.08);
}

.progress-track i,
.dark-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #f4cf55, #f7df82);
}

.progress-total {
  text-align: right;
}

.progress-total small {
  color: #777a72;
}

.progress-total strong {
  color: #2f302f;
}

.next-card,
.schedule-card {
  padding: 22px 24px;
}

.section-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-title h2 {
  margin: 0;
  color: #17181a;
  font-size: 24px;
  font-weight: 850;
}

.section-title p {
  margin: 6px 0 0;
  color: #777a72;
  font-size: 13px;
}

.section-title.compact {
  align-items: center;
}

.section-title button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  color: #2f302f;
  font-size: 13px;
  font-weight: 850;
}

.next-task {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 210px;
  align-items: center;
  gap: 18px;
  padding: 18px;
  border: 1px solid rgba(23, 24, 26, 0.06);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.62);
}

.task-icon {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: linear-gradient(145deg, #f07157, #e75f49);
  color: #fff;
  font-size: 26px;
  box-shadow: 0 14px 28px rgba(231, 95, 73, 0.24);
}

.task-copy span {
  color: #7b6720;
  font-size: 12px;
  font-weight: 850;
}

.task-copy h3 {
  margin: 8px 0 6px;
  color: #17181a;
  font-size: 22px;
  font-weight: 850;
  line-height: 1.3;
}

.task-copy p {
  margin: 0;
  color: #696c65;
  font-size: 13px;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-top: 14px;
  color: #6c6e68;
  font-size: 12px;
  font-weight: 750;
}

.task-actions {
  display: grid;
  gap: 10px;
}

.primary-action,
.secondary-action,
.todo-card > button,
.device-action {
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 850;
}

.primary-action {
  background: linear-gradient(135deg, #f07157, #e75f49);
  color: #fff;
  box-shadow: 0 16px 34px rgba(231, 95, 73, 0.26);
}

.primary-action:disabled {
  background: rgba(47, 48, 47, 0.12);
  color: #777a72;
  cursor: not-allowed;
  box-shadow: none;
}

.secondary-action {
  border: 1px solid rgba(23, 24, 26, 0.08);
  background: rgba(255, 255, 247, 0.78);
  color: #2f302f;
}

.plain-action {
  background: transparent;
  color: #e75f49;
  font-size: 13px;
  font-weight: 850;
}

.next-empty {
  display: grid;
  place-items: center;
  min-height: 148px;
  border: 1px dashed rgba(47, 48, 47, 0.16);
  border-radius: 20px;
  color: #777a72;
  text-align: center;
}

.next-empty :deep(.anticon) {
  color: #f4cf55;
  font-size: 32px;
}

.next-empty h3 {
  margin: 10px 0 4px;
  color: #17181a;
  font-size: 18px;
}

.next-empty p {
  margin: 0;
  font-size: 13px;
}

.schedule-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 14px 16px;
  border: 1px solid rgba(23, 24, 26, 0.06);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.56);
}

.schedule-row time strong,
.quick-card > strong {
  display: block;
  color: #17181a;
  font-size: 24px;
  font-weight: 850;
  line-height: 1;
}

.schedule-row time span,
.schedule-row p,
.quick-card p {
  color: #777a72;
  font-size: 12px;
  font-weight: 700;
}

.schedule-row h3 {
  margin: 0 0 6px;
  color: #17181a;
  font-size: 18px;
  font-weight: 850;
}

.schedule-row p {
  margin: 0;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.quick-card {
  min-height: 190px;
  padding: 18px;
}

.quick-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.quick-card header > :deep(.anticon) {
  color: #2f302f;
  font-size: 22px;
}

.quick-card header button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: #4f514c;
  cursor: pointer;
  font-size: 12px;
  font-weight: 850;
}

.quick-card h3 {
  margin: 0 0 14px;
  color: #17181a;
  font-size: 17px;
  font-weight: 850;
}

.quick-card p {
  margin: 10px 0 12px;
  line-height: 1.6;
}

.mini-list {
  display: grid;
  gap: 10px;
}

.mini-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(23, 24, 26, 0.06);
}

.mini-list div:last-child {
  border-bottom: 0;
}

.mini-list span {
  min-width: 0;
  overflow: hidden;
  color: #17181a;
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-list small {
  flex-shrink: 0;
  padding: 4px 8px;
  font-size: 11px;
}

.device-icons {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 14px 0;
}

.device-icons span {
  position: relative;
  display: grid;
  place-items: center;
  gap: 5px;
  color: #2f302f;
  font-size: 20px;
}

.device-icons i {
  width: 9px;
  height: 9px;
  position: absolute;
  right: 18px;
  top: 20px;
  border-radius: 999px;
  background: #69bd5b;
  box-shadow: 0 0 0 3px rgba(105, 189, 91, 0.14);
}

.device-icons small {
  color: #6c6e68;
  font-size: 11px;
  font-weight: 750;
}

.device-action {
  width: 100%;
  min-height: 42px;
  background: rgba(244, 207, 85, 0.42);
  color: #6f5d1e;
}

.student-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  color: #555852;
  font-size: 13px;
  font-weight: 850;
}

.student-pagination button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(255, 255, 247, 0.72);
  color: #2f302f;
}

.student-pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.progress-panel {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 28px;
  border-radius: 24px;
  background:
    radial-gradient(circle at 72% 0%, rgba(255, 255, 255, 0.12), transparent 28%),
    linear-gradient(145deg, #3f403d, #242523);
  color: #f8f8f0;
  box-shadow: 0 22px 54px rgba(32, 33, 31, 0.26);
}

.progress-card header,
.todo-card header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
}

.progress-card header span,
.flow-card h3,
.todo-card h3 {
  color: #fff;
  font-size: 16px;
  font-weight: 850;
}

.progress-card header strong {
  color: #f4cf55;
  font-size: 34px;
  font-weight: 850;
  line-height: 1;
}

.dark-progress {
  margin: 24px 0 12px;
  background: rgba(255, 255, 247, 0.16);
}

.progress-card p {
  margin: 0;
  color: rgba(255, 255, 247, 0.72);
  font-size: 13px;
  font-weight: 750;
}

.flow-card {
  padding-top: 22px;
  border-top: 1px solid rgba(255, 255, 247, 0.14);
}

.flow-list {
  display: grid;
  gap: 0;
  margin-top: 18px;
}

.flow-list div {
  position: relative;
  display: grid;
  grid-template-columns: 36px 1fr;
  column-gap: 12px;
  min-height: 58px;
}

.flow-list div::after {
  content: "";
  position: absolute;
  left: 17px;
  top: 34px;
  bottom: 4px;
  width: 1px;
  background: rgba(255, 255, 247, 0.18);
}

.flow-list div:last-child::after {
  display: none;
}

.flow-list span {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(255, 255, 247, 0.08);
  color: rgba(255, 255, 247, 0.48);
}

.flow-list div.active span {
  background: #f4cf55;
  color: #2f302f;
}

.flow-list strong,
.flow-list small {
  display: block;
}

.flow-list strong {
  color: rgba(255, 255, 247, 0.9);
  font-size: 14px;
  font-weight: 850;
}

.flow-list small {
  margin-top: 5px;
  color: rgba(255, 255, 247, 0.48);
  font-size: 12px;
  font-weight: 700;
}

.todo-card {
  padding-top: 22px;
  border-top: 1px solid rgba(255, 255, 247, 0.14);
}

.todo-card header span {
  color: rgba(255, 255, 247, 0.62);
  font-size: 13px;
  font-weight: 800;
}

.todo-list {
  display: grid;
  gap: 14px;
  margin: 18px 0 22px;
}

.todo-list article {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.todo-list article > span {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  background: rgba(244, 207, 85, 0.22);
  color: #f4cf55;
}

.todo-list strong,
.todo-list small {
  display: block;
}

.todo-list strong {
  overflow: hidden;
  color: #fff;
  font-size: 13px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-list small {
  margin-top: 4px;
  color: rgba(255, 255, 247, 0.48);
  font-size: 11px;
  font-weight: 700;
}

.todo-list time {
  color: #f07157;
  font-size: 12px;
  font-weight: 850;
}

.todo-card > button {
  width: 100%;
  border: 1px solid rgba(244, 207, 85, 0.42);
  background: transparent;
  color: #f4cf55;
}

:global(.student-user-menu) {
  min-width: 142px;
  padding: 8px;
  border-radius: 14px;
  box-shadow: 0 18px 42px rgba(35, 36, 34, 0.14);
}

.modal-list,
.device-check-list,
.help-modal {
  display: grid;
  gap: 12px;
}

.notice-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 2px 8px;
}

.notice-toolbar span {
  color: #62645f;
  font-size: 13px;
  font-weight: 800;
}

.notice-toolbar button {
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: rgba(47, 48, 47, 0.08);
  color: #2f302f;
  cursor: pointer;
  font-size: 12px;
  font-weight: 850;
}

.notice-toolbar button:disabled {
  opacity: 0.44;
  cursor: not-allowed;
}

.modal-list article,
.device-check-list article {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(23, 24, 26, 0.08);
  border-radius: 16px;
  background: rgba(255, 255, 247, 0.76);
}

.modal-list article.read {
  opacity: 0.62;
  background: rgba(245, 245, 238, 0.58);
}

.modal-list article > span,
.device-check-list article > span {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: rgba(244, 207, 85, 0.24);
  color: #6f5d1e;
  font-size: 20px;
}

.device-check-list article > span.success {
  background: rgba(105, 189, 91, 0.16);
  color: #4f8b43;
}

.device-check-list article > span.warning {
  background: rgba(231, 95, 73, 0.15);
  color: #e75f49;
}

.modal-list strong,
.device-check-list strong,
.help-modal strong {
  display: block;
  color: #17181a;
  font-size: 15px;
  font-weight: 850;
}

.modal-list small,
.device-check-list small,
.task-detail-modal p,
.device-modal p,
.help-modal p {
  margin: 4px 0 0;
  color: #6c6e68;
  font-size: 13px;
  line-height: 1.7;
}

.modal-list button {
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: #2f302f;
  color: #fff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 850;
}

.notice-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-list button.read-action {
  background: rgba(47, 48, 47, 0.08);
  color: #3c3d39;
}

.modal-list button:disabled {
  cursor: not-allowed;
  opacity: 0.56;
}

.task-detail-modal header {
  padding: 18px;
  border-radius: 18px;
  background: rgba(244, 207, 85, 0.16);
}

.task-detail-modal header span {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(244, 207, 85, 0.36);
  color: #7b6720;
  font-size: 12px;
  font-weight: 850;
}

.task-detail-modal h3 {
  margin: 12px 0 0;
  color: #17181a;
  font-size: 22px;
  font-weight: 850;
}

.detail-grid-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.detail-grid-list div {
  min-height: 76px;
  padding: 14px;
  border: 1px solid rgba(23, 24, 26, 0.08);
  border-radius: 16px;
  background: rgba(255, 255, 247, 0.76);
}

.detail-grid-list small {
  display: block;
  color: #777a72;
  font-size: 12px;
  font-weight: 750;
}

.detail-grid-list strong {
  display: block;
  margin-top: 8px;
  color: #17181a;
  font-size: 15px;
  font-weight: 850;
}

.device-modal > p {
  margin-bottom: 14px;
}

.help-modal article {
  padding: 16px;
  border: 1px solid rgba(23, 24, 26, 0.08);
  border-radius: 16px;
  background: rgba(255, 255, 247, 0.76);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.modal-actions .primary-action,
.modal-actions .secondary-action {
  min-width: 128px;
  padding: 0 18px;
}

@media (max-width: 1360px) {
  .student-workspace {
    grid-template-columns: 210px minmax(0, 1fr);
  }

  .progress-panel {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: 1fr 1fr 1.2fr;
  }
}

@media (max-width: 980px) {
  .student-page {
    padding: 16px;
  }

  .student-shell {
    padding: 14px;
    border-radius: 24px;
  }

  .student-topbar,
  .student-workspace,
  .next-task,
  .quick-grid,
  .progress-panel {
    grid-template-columns: 1fr;
  }

  .student-topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .student-sidebar {
    order: 2;
  }

  .student-main {
    order: 1;
  }

  .progress-panel {
    order: 3;
  }

  .sidebar-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .welcome-visual {
    opacity: 0.3;
  }
}

@media (max-width: 640px) {
  .student-page {
    padding: 10px;
  }

  .student-shell {
    min-height: auto;
  }

  .topbar-actions,
  .user-chip,
  .state-tabs,
  .schedule-row,
  .task-actions {
    width: 100%;
  }

  .state-tabs {
    border-radius: 22px;
  }

  .state-tabs button {
    flex: 0 0 auto;
  }

  .welcome-card {
    min-height: 330px;
    padding: 22px 18px;
  }

  .progress-dock {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .progress-total {
    text-align: left;
  }

  .next-task,
  .schedule-row {
    grid-template-columns: 1fr;
  }

  .sidebar-nav {
    grid-template-columns: 1fr;
  }

  .progress-panel {
    padding: 22px;
  }
}
</style>
