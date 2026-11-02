<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">面试报告</h1>
        <p class="page-desc">已结束的面试及其评估结果</p>
      </div>
      <Button @click="load"><ReloadOutlined /> 刷新</Button>
    </div>

    <div class="card">
      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        row-key="id"
        :scroll="{ x: 640 }"
        :pagination="{
          current: page,
          pageSize: size,
          total,
          showTotal: (t: number) => `共 ${t} 条`,
          hideOnSinglePage: true,
        }"
        :locale="{ emptyText: '暂无已结束的面试' }"
        @change="onChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'theme'">
            <div class="font-medium">{{ record.theme }}</div>
          </template>
          <template v-else-if="column.key === 'time'">
            <span class="num">{{ formatTime(record.assessmentEndTime || record.assessStartTime) }}</span>
          </template>
          <template v-else-if="column.key === 'score'">
            <template v-if="record.state !== 2">
              <span class="muted">-</span>
            </template>
            <template v-else-if="!record.showResult">
              <span class="muted">未公布</span>
            </template>
            <template v-else-if="record.defenseScore != null">
              <span class="num font-semibold">{{ record.defenseScore }}</span>
              <span class="muted num"> / {{ record.score }}</span>
              <Tag
                v-if="record.passScore != null"
                class="ml-2"
                :bordered="false"
                :color="record.defenseScore >= record.passScore ? 'success' : 'error'"
                >{{ record.defenseScore >= record.passScore ? "通过" : "未通过" }}</Tag
              >
            </template>
            <span v-else class="muted">-</span>
          </template>
          <template v-else-if="column.key === 'state'">
            <Badge :status="stateOf(record).badge" :text="stateOf(record).text" />
            <div v-if="lockedText(record)" class="text-xs muted mt-1">{{ lockedText(record) }}</div>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button
                v-if="record.state === 2"
                type="link"
                size="small"
                class="!px-0"
                @click="router.push(`/student/reports/${record.id}`)"
                >查看报告</Button
              >
              <Button
                v-if="canReappoint(record)"
                type="link"
                size="small"
                class="!px-0"
                :disabled="!hasSlots(record)"
                @click="openAppoint(record)"
                >{{ hasSlots(record) ? "重新预约" : "暂无可约时间" }}</Button
              >
            </Space>
          </template>
        </template>
      </Table>
    </div>

    <AppointmentModal v-model:open="appointOpen" :item="appointItem" title="重新预约" @success="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Badge, Button, Space, Table, Tag } from "ant-design-vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import dayjs from "dayjs";
import { getAssessmentDone } from "@/api/studentAssessment";
import AppointmentModal from "./components/AppointmentModal.vue";

const router = useRouter();
const list = ref<Record<string, any>[]>([]);
const total = ref(0);
const page = ref(1);
const size = 10;
const loading = ref(false);
const appointOpen = ref(false);
const appointItem = ref<Record<string, any> | null>(null);

const columns = [
  { title: "面试名称", key: "theme" },
  { title: "完成时间", key: "time", width: 180 },
  { title: "得分", key: "score", width: 220 },
  { title: "状态", key: "state", width: 220 },
  { title: "操作", key: "action", width: 180 },
];

type BadgeStatus = "success" | "processing" | "default" | "error" | "warning";

function stateOf(r: Record<string, any>): { text: string; badge: BadgeStatus } {
  switch (r.state) {
    case 2:
      return { text: "已完成", badge: "success" };
    case 3:
      return { text: r.stateTag || "预约未参加", badge: "error" };
    case 4:
      return { text: "可重新预约", badge: "processing" };
    case 5:
      return { text: r.stateTag || "过期未预约", badge: "default" };
    default:
      return { text: r.stateTag || "已结束", badge: "default" };
  }
}

// 缺考且开启了缺考处罚：在解禁时间前不能重新预约
function lockedText(r: Record<string, any>) {
  if (r.state === 3 && r.assessmentFailPunish && r.rescheduleAppointTime) {
    return `缺考，${formatTime(r.rescheduleAppointTime)} 后可重新预约`;
  }
  return "";
}

// 与原逻辑一致：教师解除处罚后（状态 4）可重约；预约制面试未通过时也可重约
function canReappoint(r: Record<string, any>) {
  if (r.state === 3 || r.state === 5) return false;
  if (r.state === 4) return true;
  return Boolean(
    r.appointRecordFlag && typeof r.defenseScore === "number" && r.passScore != null && r.defenseScore < r.passScore
  );
}

function hasSlots(r: Record<string, any>) {
  return (r.canAppointmentTimes || []).some((t: Record<string, any>) => !t.full);
}

function formatTime(t?: string) {
  return t ? dayjs(t).format("YYYY-MM-DD HH:mm") : "-";
}

async function load() {
  loading.value = true;
  try {
    const res: any = await getAssessmentDone({ page: page.value, size });
    list.value = res?.data?.records || [];
    total.value = res?.data?.total || 0;
  } finally {
    loading.value = false;
  }
}

function onChange(p: { current?: number }) {
  page.value = p.current || 1;
  load();
}

function openAppoint(r: Record<string, any>) {
  appointItem.value = r;
  appointOpen.value = true;
}

onMounted(load);
</script>
