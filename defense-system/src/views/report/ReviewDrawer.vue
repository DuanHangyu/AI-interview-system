<template>
  <Drawer :open="open" title="复核评分" :width="520" destroy-on-close @close="emit('update:open', false)">
    <div class="review-tip">
      调整各维度分数后，最终得分为各维度之和；AI 原始评分会保留在报告中对照展示。
    </div>
    <div class="review-list">
      <div v-for="dim in rows" :key="dim.name" class="review-row">
        <div class="review-name">
          <div class="font-medium">{{ dim.name }}</div>
          <div class="text-xs muted">AI 评分 {{ dim.aiScore }}{{ dim.maxScore != null ? ` / ${dim.maxScore}` : "" }}</div>
        </div>
        <InputNumber
          v-model:value="dim.score"
          :min="0"
          :max="dim.maxScore ?? totalScore"
          :precision="0"
          class="w-[120px]"
          :addon-after="dim.maxScore != null ? `/ ${dim.maxScore}` : '分'"
        />
      </div>
    </div>
    <div class="review-total">
      <span>最终得分</span>
      <span class="num">
        <b :class="{ 'is-over': total > totalScore }">{{ total }}</b>
        <span class="muted"> / {{ totalScore }}</span>
        <span v-if="total !== aiTotal" class="text-xs muted ml-2">（AI 评分 {{ aiTotal }}）</span>
      </span>
    </div>
    <div v-if="total > totalScore" class="text-xs mt-1" style="color: var(--c-danger)">
      各维度之和不能超过总分 {{ totalScore }}
    </div>
    <div class="mt-6 font-medium">教师评语</div>
    <Textarea
      v-model:value="comment"
      class="mt-2"
      :rows="5"
      :maxlength="1000"
      show-count
      placeholder="可选：填写对学生表现的评价和建议，将展示在面试报告中"
    />
    <template #footer>
      <div class="flex justify-between">
        <Button @click="resetToAi">恢复 AI 评分</Button>
        <div class="flex gap-2">
          <Button @click="emit('update:open', false)">取消</Button>
          <Button type="primary" :loading="saving" :disabled="total > totalScore" @click="save">保存复核</Button>
        </div>
      </div>
    </template>
  </Drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Button, Drawer, InputNumber, Textarea, message } from "ant-design-vue";
import { reviewRecord } from "@/api/studyRecord";
import type { ReportDetail } from "./types";

const props = defineProps<{ open: boolean; detail: ReportDetail }>();
const emit = defineEmits<{ (e: "update:open", v: boolean): void; (e: "saved"): void }>();

interface Row {
  name: string;
  aiScore: number;
  maxScore?: number | null;
  score?: number;
}

const rows = ref<Row[]>([]);
const comment = ref("");
const saving = ref(false);

const totalScore = computed(() => props.detail.totalScore ?? 100);
const total = computed(() => rows.value.reduce((sum, r) => sum + (r.score ?? 0), 0));
const aiTotal = computed(() => rows.value.reduce((sum, r) => sum + (r.aiScore ?? 0), 0));

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    rows.value = (props.detail.dimensions || []).map((d) => ({
      name: d.name,
      aiScore: d.aiScore,
      maxScore: d.maxScore,
      score: d.score,
    }));
    comment.value = props.detail.review?.comment || "";
  },
  { immediate: true }
);

function resetToAi() {
  rows.value.forEach((r) => (r.score = r.aiScore));
}

async function save() {
  saving.value = true;
  try {
    await reviewRecord({
      recordId: props.detail.id,
      dimensions: rows.value.map((r) => ({ name: r.name, score: r.score ?? 0 })),
      comment: comment.value,
    });
    message.success("复核结果已保存");
    emit("update:open", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.review-tip {
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--c-primary-soft);
  color: var(--c-text-2);
  font-size: 13px;
  line-height: 20px;
}

.review-list {
  margin-top: 16px;
}

.review-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--c-border-2);
}

.review-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 16px;
  font-weight: 500;
}

.review-total b {
  font-size: 22px;
  color: var(--c-primary);
}

.review-total b.is-over {
  color: var(--c-danger);
}
</style>
