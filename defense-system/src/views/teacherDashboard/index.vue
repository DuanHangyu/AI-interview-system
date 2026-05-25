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
      <StudyRecord v-if="activeTab == 0" />
      <AssessSetting v-else />
    </section>
  </div>
</template>
<script setup lang="ts">
import { ref } from "vue";
import AssessSetting from "./AssessSetting.vue";
import StudyRecord from "./StudyRecord.vue";
import { useRoute, useRouter } from "vue-router";

defineOptions({
  name: "TeacherDashboard",
});

const tabs = ref([
  {
    label: "考核记录",
    key: 0,
    component: StudyRecord,
  },
  {
    label: "考核设置",
    key: 1,
    component: AssessSetting,
  },
]);

const route = useRoute();
const router = useRouter();
const activeTab = ref(Number(route.query?.tab || 0));
const changeTab = (e: number) => {
  activeTab.value = e;
  router.push(`/teacherDashboard?tab=${e}`);
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
