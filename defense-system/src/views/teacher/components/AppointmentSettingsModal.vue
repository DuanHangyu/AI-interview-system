<template>
  <Modal
    v-model:open="visible"
    title="预约设置"
    :width="760"
    :confirm-loading="saving"
    ok-text="保存"
    destroy-on-close
    @ok="save"
  >
    <div class="muted text-[13px] mb-3">{{ assessment.theme }}</div>
    <div class="section-title">
      <span>预约时段</span>
      <Button size="small" @click="addSlot">
        <template #icon><PlusOutlined /></template>
        添加时段
      </Button>
    </div>
    <Table size="small" row-key="key" :columns="columns" :data-source="slots" :pagination="false">
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'timePeriod'">
          <DatePicker
            v-model:value="record.timePeriod"
            show-time
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:00"
            placeholder="开始时间"
            class="w-full"
            :status="submitted && !record.timePeriod ? 'error' : ''"
          />
        </template>
        <template v-else-if="column.key === 'participantLimit'">
          <InputNumber
            v-model:value="record.participantLimit"
            :min="1"
            :precision="0"
            placeholder="人数"
            class="w-full"
            :status="submitted && !record.participantLimit ? 'error' : ''"
          />
        </template>
        <template v-else-if="column.key === 'location'">
          <Input v-model:value="record.location" placeholder="如：实验楼 A101（可选）" />
        </template>
        <template v-else-if="column.key === 'action'">
          <Button type="link" size="small" danger :disabled="slots.length <= 1" @click="slots.splice(index, 1)">
            删除
          </Button>
        </template>
      </template>
    </Table>
    <div class="muted text-xs mt-2">删除已有时段会同时取消该时段内学生的预约。修改时间后，已预约学生会自动调整到新时间。</div>

    <div class="section-title mt-6"><span>缺考限制</span></div>
    <div class="flex items-center gap-3">
      <Switch v-model:checked="failPunish" />
      <span>预约后未参加面试的学生，在指定时间前不能重新预约</span>
    </div>
    <div v-if="failPunish" class="flex items-center gap-3 mt-3">
      <span class="muted">限制截止至</span>
      <DatePicker
        v-model:value="rescheduleAppointTime"
        show-time
        format="YYYY-MM-DD HH:mm"
        value-format="YYYY-MM-DD HH:mm:00"
        placeholder="选择时间"
        :status="submitted && !rescheduleAppointTime ? 'error' : ''"
      />
    </div>
  </Modal>
</template>

<script setup lang="ts">
import { ref } from "vue";
import dayjs from "dayjs";
import { Button, DatePicker, Input, InputNumber, Modal, Switch, Table, message } from "ant-design-vue";
import { PlusOutlined } from "@ant-design/icons-vue";
import { settingAppointment } from "@/api/assessment";

interface Slot {
  key: number;
  id?: number;
  timePeriod?: string;
  participantLimit?: number;
  location?: string;
}

const emit = defineEmits<{ (e: "success"): void }>();

const visible = ref(false);
const saving = ref(false);
const submitted = ref(false);
const assessment = ref<Record<string, any>>({});
const slots = ref<Slot[]>([]);
const failPunish = ref(false);
const rescheduleAppointTime = ref<string>();
let keySeq = 0;

const columns = [
  { title: "开始时间", key: "timePeriod", width: 220 },
  { title: "人数上限", key: "participantLimit", width: 120 },
  { title: "地点", key: "location" },
  { title: "", key: "action", width: 64 },
];

function format(value?: string) {
  return value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : undefined;
}

function addSlot() {
  slots.value.push({ key: ++keySeq });
}

function open(record: Record<string, any>) {
  assessment.value = record;
  submitted.value = false;
  const setting = record.appointmentSetting || {};
  slots.value = (setting.timePeriods || []).map((item: Record<string, any>) => ({
    key: ++keySeq,
    id: item.id,
    timePeriod: format(item.timePeriod),
    participantLimit: item.participantLimit,
    location: item.location,
  }));
  if (!slots.value.length) addSlot();
  failPunish.value = !!setting.assessmentFailPunish;
  rescheduleAppointTime.value = format(setting.rescheduleAppointTime);
  visible.value = true;
}

async function save() {
  submitted.value = true;
  if (slots.value.some((s) => !s.timePeriod || !s.participantLimit)) {
    message.error("请填写每个时段的开始时间和人数上限");
    return;
  }
  if (failPunish.value && !rescheduleAppointTime.value) {
    message.error("请选择限制截止时间");
    return;
  }
  saving.value = true;
  try {
    const params: Record<string, any> = {
      assessmentId: assessment.value.id,
      timePeriods: slots.value.map((s) => ({
        ...(s.id ? { id: s.id } : {}),
        timePeriod: s.timePeriod,
        participantLimit: s.participantLimit,
        location: s.location?.trim() || undefined,
      })),
      assessmentFailPunish: failPunish.value,
    };
    if (failPunish.value) params.rescheduleAppointTime = rescheduleAppointTime.value;
    await settingAppointment(params);
    message.success("预约设置已保存");
    visible.value = false;
    emit("success");
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
