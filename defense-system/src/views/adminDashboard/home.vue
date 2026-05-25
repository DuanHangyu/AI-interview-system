<template>
  <div class="size-full space-x-4 flex overflow-hidden">
    <section class="space-y-4 w-40 flex-shrink-0">
      <div
        v-for="item in tabs"
        :key="item.key"
        class="w-full h-[52px] truncate tabs"
        :class="activeTab == item.key ? 'activeTab' : ''"
        @click="changeTab(item.key)"
      >
        {{ item?.label }}
      </div>
    </section>
    <section class="flex-grow overflow-hidden">
      <StudentManage v-if="activeTab == 0" />
      <TeacherManage v-else-if="activeTab == 1" />
      <StudyRecord v-else-if="activeTab == 2" />
      <AssessSetting v-else-if="activeTab == 3" />
    </section>
  </div>
</template>
<script setup lang="ts">
import { ref } from "vue";
import AssessSetting from "../teacherDashboard/AssessSetting.vue";
import StudentManage from "../teacherDashboard/StudentManage.vue";
import StudyRecord from "../teacherDashboard/StudyRecord.vue";
import TeacherManage from "./index.vue";
import { useRoute, useRouter } from "vue-router";

defineOptions({
  name: "AdminDashboard",
});

const tabs = ref([
  {
    label: "学生信息管理",
    key: 0,
    component: StudentManage,
  },
  {
    label: "教师管理",
    key: 1,
    component: TeacherManage,
  },
  {
    label: "考核记录",
    key: 2,
    component: StudyRecord,
  },
  {
    label: "考核设置",
    key: 3,
    component: AssessSetting,
  },
]);

const route = useRoute();
const router = useRouter();
const initialTab = Number(route.query?.tab || 0);
const activeTab = ref(
  tabs.value.some((item) => item.key === initialTab) ? initialTab : 0
);
const changeTab = (e: number) => {
  activeTab.value = e;
  router.push(`/adminDashboard?tab=${e}`);
};
</script>
<style scoped>
.tabs {
  height: 52px;
  background: #ffffff;
  border-radius: 6px 6px 6px 6px;
  padding: 15px 16px;
  font-weight: 600;
  font-size: 16px;
  color: #1677ff;
  cursor: pointer;
  display: flex;
  align-items: center;
}

.activeTab {
  background: #1677ff;
  color: rgba(255, 255, 255, 0.88);
}
</style>
