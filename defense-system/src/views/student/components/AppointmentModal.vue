<template>
  <Modal
    :open="open"
    :title="title"
    ok-text="确认预约"
    :ok-button-props="{ disabled: !selected }"
    :confirm-loading="saving"
    destroy-on-close
    @ok="submit"
    @cancel="emit('update:open', false)"
  >
    <div class="muted text-[13px] mb-3">{{ item?.theme }}</div>
    <div v-if="!slots.length" class="py-6">
      <Empty description="暂无可预约时间段，请联系教师开放新的预约时间" />
    </div>
    <div v-else class="slots">
      <button
        v-for="slot in slots"
        :key="slot.timePeriod"
        type="button"
        class="slot"
        :class="{ 'is-active': selected === slot.timePeriod, 'is-full': slot.full }"
        :disabled="slot.full"
        @click="selected = slot.timePeriod"
      >
        <div class="slot-time num">{{ formatTime(slot.timePeriod) }}</div>
        <div class="slot-meta">
          <span>{{ slot.location || "地点待定" }}</span>
          <span>{{ slot.full ? "已约满" : `剩余 ${slot.remainCount ?? "-"} 个名额` }}</span>
        </div>
      </button>
    </div>
  </Modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Empty, Modal, message } from "ant-design-vue";
import dayjs from "dayjs";
import { appointment } from "@/api/studentAssessment";

const props = defineProps<{ open: boolean; item: Record<string, any> | null; title?: string }>();
const emit = defineEmits<{ (e: "update:open", v: boolean): void; (e: "success"): void }>();

const selected = ref<string>();
const saving = ref(false);
const slots = computed<Record<string, any>[]>(() => props.item?.canAppointmentTimes || []);
const title = computed(() => props.title || "预约面试时间");

watch(
  () => props.open,
  (v) => {
    if (v) selected.value = undefined;
  }
);

function formatTime(t: string) {
  return t ? dayjs(t).format("YYYY-MM-DD HH:mm") : "-";
}

async function submit() {
  if (!props.item || !selected.value) return;
  saving.value = true;
  try {
    await appointment({ assessmentId: props.item.id, timePeriod: selected.value });
    message.success("预约成功");
    emit("update:open", false);
    emit("success");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.slots {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.slot {
  padding: 12px 14px;
  text-align: left;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.slot:hover:not(:disabled) {
  border-color: var(--c-primary);
}

.slot.is-active {
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
}

.slot.is-full {
  cursor: not-allowed;
  opacity: 0.55;
}

.slot-time {
  font-weight: 600;
  color: var(--c-text);
}

.slot-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-text-3);
}
</style>
