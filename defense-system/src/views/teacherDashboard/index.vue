<template>
  <div class="dashboard-shell">
    <aside class="dashboard-sidebar">
      <div class="dashboard-sidebar-head">
        <div class="dashboard-sidebar-icon">
          <ReadOutlined />
        </div>
        <div>
          <div class="dashboard-sidebar-title">教师端</div>
          <div class="dashboard-sidebar-subtitle">Teacher Console</div>
        </div>
      </div>

      <nav class="dashboard-nav" aria-label="教师端导航">
        <button
        v-for="item in tabs"
        :key="item.key"
          type="button"
          class="dashboard-nav-item"
          :class="activeTab == item.key ? 'is-active' : ''"
        @click="changeTab(item.key)"
      >
          <span class="dashboard-nav-icon">
            <component :is="item.icon" />
          </span>
          <span class="dashboard-nav-label">{{ item?.label }}</span>
        </button>
      </nav>

      <div class="dashboard-sidebar-card">
        <div class="dashboard-sidebar-card-title">Assessment</div>
        <p>集中查看考核记录，维护答辩规则与预约设置。</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-main-header">
        <div>
          <div class="dashboard-eyebrow">Teaching Console</div>
          <h1>{{ activeTabInfo?.label }}</h1>
        </div>
        <div class="dashboard-main-meta">
          <span>{{ tabs.length }} 个模块</span>
        </div>
      </header>

      <div class="dashboard-main-body">
        <StudyRecord v-if="activeTab == 0" />
        <AssessSetting v-else />
      </div>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, markRaw, ref } from "vue";
import AssessSetting from "./AssessSetting.vue";
import StudyRecord from "./StudyRecord.vue";
import { useRoute, useRouter } from "vue-router";
import {
  BarChartOutlined,
  ReadOutlined,
  SettingOutlined,
} from "@ant-design/icons-vue";

defineOptions({
  name: "TeacherDashboard",
});

const tabs = ref([
  {
    label: "考核记录",
    key: 0,
    component: StudyRecord,
    icon: markRaw(BarChartOutlined),
  },
  {
    label: "考核设置",
    key: 1,
    component: AssessSetting,
    icon: markRaw(SettingOutlined),
  },
]);

const route = useRoute();
const router = useRouter();
const activeTab = ref(Number(route.query?.tab || 0));
const activeTabInfo = computed(() =>
  tabs.value.find((item) => item.key === activeTab.value)
);
const changeTab = (e: number) => {
  activeTab.value = e;
  router.push(`/teacherDashboard?tab=${e}`);
};
</script>
<style scoped>
.dashboard-shell {
  width: 100%;
  height: 100%;
  display: flex;
  gap: 16px;
  overflow: hidden;
}

.dashboard-sidebar {
  width: 184px;
  min-width: 184px;
  display: flex;
  flex-direction: column;
  padding: 18px 14px;
  border-radius: 24px;
  background: linear-gradient(180deg, #3d3e3d, #2d2e2d);
  color: var(--color-text-inverse);
  box-shadow:
    0 24px 70px rgba(18, 19, 20, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.dashboard-sidebar-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 4px 18px;
}

.dashboard-sidebar-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 999px;
  background: #ffffff;
  color: var(--color-accent);
}

.dashboard-sidebar-title {
  font-size: 14px;
  font-weight: 800;
  line-height: 1.2;
}

.dashboard-sidebar-subtitle {
  margin-top: 4px;
  color: rgba(255, 255, 255, 0.48);
  font-size: 11px;
  font-weight: 600;
}

.dashboard-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.dashboard-nav-item {
  width: 100%;
  min-height: 46px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  text-align: left;
  transition:
    background-color 180ms ease,
    color 180ms ease,
    transform 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.dashboard-nav-item:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.dashboard-nav-item.is-active {
  background: rgba(255, 255, 255, 0.14);
  color: #ffffff;
}

.dashboard-nav-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
}

.dashboard-nav-item.is-active .dashboard-nav-icon {
  background: #ffffff;
  color: var(--color-accent);
}

.dashboard-nav-label {
  min-width: 0;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.25;
}

.dashboard-sidebar-card {
  padding: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
}

.dashboard-sidebar-card-title {
  font-size: 13px;
  font-weight: 800;
}

.dashboard-sidebar-card p {
  margin: 8px 0 0;
  color: rgba(255, 255, 255, 0.58);
  font-size: 12px;
  line-height: 1.6;
}

.dashboard-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
  border: 1px solid var(--color-border-light);
  border-radius: 28px;
  background: var(--color-surface);
  box-shadow:
    0 28px 80px rgba(20, 21, 22, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
  overflow: hidden;
}

.dashboard-main-header {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.dashboard-eyebrow {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 800;
}

.dashboard-main-header h1 {
  margin: 10px 0 0;
  color: var(--color-text-primary);
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
}

.dashboard-main-meta {
  height: 34px;
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  border: 1px solid rgba(24, 25, 27, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.58);
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.dashboard-main-body {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

@media (max-width: 900px) {
  .dashboard-shell {
    flex-direction: column;
    overflow: auto;
  }

  .dashboard-sidebar {
    width: 100%;
    min-width: 0;
  }

  .dashboard-nav {
    flex-direction: row;
    overflow-x: auto;
  }

  .dashboard-nav-item {
    min-width: 138px;
  }

  .dashboard-sidebar-card {
    display: none;
  }

  .dashboard-main {
    min-height: 720px;
    padding: 18px;
  }
}
</style>
