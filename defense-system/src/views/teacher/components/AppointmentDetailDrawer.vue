<template>
  <Drawer v-model:open="visible" title="预约情况" :width="760" destroy-on-close>
    <Spin :spinning="loading">
      <div class="muted text-[13px] mb-4">{{ assessment.theme }}</div>
      <div class="stats">
        <div class="stat">
          <div class="stat-label">已预约</div>
          <div class="stat-value num">{{ info.alreadyAppointCount ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">未预约</div>
          <div class="stat-value num">{{ info.notAppointCount ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">预约时段</div>
          <div class="stat-value num">{{ slots.length }}</div>
        </div>
      </div>

      <Tabs v-model:activeKey="view" class="mt-4">
        <TabPane key="slots" tab="按时段查看" />
        <TabPane key="none" :tab="`未预约学生（${info.notAppointStudents?.length || 0}）`" />
      </Tabs>

      <template v-if="view === 'slots'">
        <Empty v-if="!slots.length" description="尚未设置预约时段" />
        <template v-else>
          <Select v-model:value="current" :options="slotOptions" class="w-full" />
          <div class="mt-4">
            <Segmented v-model:value="group" :options="groupOptions" />
          </div>
          <Table
            class="mt-3"
            size="small"
            row-key="id"
            :columns="columns"
            :data-source="groupRows"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'punishState'">
                <Tag v-if="record.punishState === 0" color="red" :bordered="false">限制预约中</Tag>
                <Tag v-else-if="record.punishState === 1" :bordered="false">已解除限制</Tag>
                <span v-else class="muted">—</span>
              </template>
              <template v-else-if="column.key === 'action'">
                <Popconfirm
                  v-if="group === 'appointment'"
                  title="取消后该学生需要重新预约，确定取消吗？"
                  ok-text="取消预约"
                  cancel-text="返回"
                  @confirm="cancel(record)"
                >
                  <Button type="link" size="small" :disabled="slotStarted">取消预约</Button>
                </Popconfirm>
                <Popconfirm
                  v-else-if="group === 'expired' && record.punishState === 0"
                  title="解除后该学生可以立即重新预约，确定解除吗？"
                  ok-text="解除限制"
                  cancel-text="返回"
                  @confirm="lift(record)"
                >
                  <Button type="link" size="small">解除限制</Button>
                </Popconfirm>
              </template>
            </template>
            <template #emptyText><Empty :image="simpleImage" description="暂无学生" /></template>
          </Table>
          <div v-if="group === 'appointment' && slotStarted" class="muted text-xs mt-2">
            该时段已开始，不能再取消预约。
          </div>
        </template>
      </template>

      <Table
        v-else
        size="small"
        row-key="id"
        :columns="baseColumns"
        :data-source="info.notAppointStudents || []"
        :pagination="{ pageSize: 20, hideOnSinglePage: true }"
      >
        <template #emptyText><Empty :image="simpleImage" description="所有学生均已预约" /></template>
      </Table>
    </Spin>
  </Drawer>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import dayjs from "dayjs";
import {
  Button,
  Drawer,
  Empty,
  Popconfirm,
  Segmented,
  Select,
  Spin,
  TabPane,
  Table,
  Tabs,
  Tag,
  message,
} from "ant-design-vue";
import { cancelStudentAppointment, getSettingAppointment, liftPunish } from "@/api/assessment";

type Group = "appointment" | "assessed" | "expired";

const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
const visible = ref(false);
const loading = ref(false);
const assessment = ref<Record<string, any>>({});
const info = ref<Record<string, any>>({});
const view = ref<"slots" | "none">("slots");
const current = ref(0);
const group = ref<Group>("appointment");

const slots = computed<Record<string, any>[]>(() => info.value.timePeriods || []);
const slotOptions = computed(() =>
  slots.value.map((s, i) => ({
    value: i,
    label: `${dayjs(s.timePeriod).format("YYYY-MM-DD HH:mm")}${s.location ? `  ·  ${s.location}` : ""}`,
  }))
);
const currentSlot = computed(() => slots.value[current.value] || {});
// 与后端一致：开始 10 分钟后未参加即记为缺考，之后不再允许取消预约
const slotStarted = computed(
  () =>
    !!currentSlot.value.timePeriod && dayjs().isAfter(dayjs(currentSlot.value.timePeriod).add(10, "minute"))
);
const groupOptions = computed(() => [
  { value: "appointment", label: `待面试 ${currentSlot.value.appointmentStudents?.length || 0}` },
  { value: "assessed", label: `已完成 ${currentSlot.value.alreadyAssessStudents?.length || 0}` },
  { value: "expired", label: `缺考 ${currentSlot.value.expiredStudents?.length || 0}` },
]);
const groupRows = computed(() => {
  const map: Record<Group, string> = {
    appointment: "appointmentStudents",
    assessed: "alreadyAssessStudents",
    expired: "expiredStudents",
  };
  return currentSlot.value[map[group.value]] || [];
});

const baseColumns = [
  { title: "姓名", dataIndex: "studentName", key: "studentName" },
  { title: "班级", dataIndex: "schoolClass", key: "schoolClass" },
];
const columns = computed(() => {
  if (group.value === "appointment") return [...baseColumns, { title: "操作", key: "action", width: 100 }];
  if (group.value === "expired")
    return [
      ...baseColumns,
      { title: "预约限制", key: "punishState", width: 120 },
      { title: "操作", key: "action", width: 100 },
    ];
  return baseColumns;
});

async function load() {
  loading.value = true;
  try {
    const res = await getSettingAppointment(assessment.value.id);
    info.value = res?.data || {};
    if (current.value >= slots.value.length) current.value = 0;
  } finally {
    loading.value = false;
  }
}

function open(record: Record<string, any>) {
  assessment.value = record;
  info.value = {};
  view.value = "slots";
  current.value = 0;
  group.value = "appointment";
  visible.value = true;
  load();
}

async function cancel(record: Record<string, any>) {
  await cancelStudentAppointment(record.id);
  message.success("已取消预约");
  load();
}

async function lift(record: Record<string, any>) {
  await liftPunish({ id: record.id });
  message.success("已解除限制");
  load();
}

defineExpose({ open });
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.stat {
  padding: 12px 16px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
}

.stat-label {
  font-size: 13px;
  color: var(--c-text-3);
}

.stat-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 600;
}
</style>
