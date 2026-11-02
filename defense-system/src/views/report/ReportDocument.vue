<template>
  <article class="report">
    <!-- 报告抬头 -->
    <header class="rp-head">
      <div class="rp-brand">
        <img :src="logoRed" :alt="BRAND.school + BRAND.college" class="rp-logo" />
        <span class="rp-product">{{ BRAND.product }}</span>
      </div>
      <div class="rp-meta">
        <span>报告编号 {{ reportNo }}</span>
        <span v-if="detail.review?.reviewTime">复核时间 {{ detail.review.reviewTime }}</span>
      </div>
    </header>
    <h1 class="rp-title">面试评估报告</h1>
    <div class="rp-subtitle">{{ detail.theme }}</div>

    <!-- 候选人信息 -->
    <section class="rp-info">
      <div v-for="item in infoItems" :key="item.label" class="rp-info-item">
        <span class="rp-info-label">{{ item.label }}</span>
        <span class="rp-info-value">{{ item.value || "—" }}</span>
      </div>
    </section>

    <!-- 综合结论 -->
    <section class="rp-section rp-overview">
      <div class="rp-score">
        <div class="rp-score-label">综合得分</div>
        <div class="rp-score-value num" :class="passed ? 'is-pass' : 'is-fail'">
          {{ detail.score ?? "—" }}<small>/ {{ detail.totalScore ?? 100 }}</small>
        </div>
        <span class="rp-verdict" :class="passed ? 'is-pass' : 'is-fail'">{{ passed ? "通过" : "未通过" }}</span>
        <div class="rp-score-foot">
          及格线 {{ detail.passScore ?? "—" }}
          <template v-if="reviewed"> · AI 初评 {{ detail.aiScore }}</template>
        </div>
      </div>
      <div class="rp-summary">
        <div class="rp-h3">综合评价</div>
        <p>{{ detail.summary || "暂无综合评价" }}</p>
        <div v-if="reviewed" class="rp-reviewed-note">
          分数已由{{ detail.review?.reviewerName ? `教师 ${detail.review.reviewerName} ` : "教师" }}复核调整
        </div>
      </div>
    </section>

    <!-- 分项评分 -->
    <section v-if="dimensions.length" class="rp-section">
      <h2 class="rp-h2">分项评分</h2>
      <div class="rp-dims">
        <div v-for="dim in dimensions" :key="dim.name" class="rp-dim">
          <div class="rp-dim-head">
            <span class="rp-dim-name">{{ dim.name }}</span>
            <span class="rp-dim-score num">
              <template v-if="dim.score !== dim.aiScore">
                <s class="muted">{{ dim.aiScore }}</s>
                <span class="rp-arrow">→</span>
              </template>
              <b>{{ dim.score }}</b>
              <span class="muted"> / {{ dim.maxScore ?? "—" }}</span>
            </span>
          </div>
          <div class="rp-bar">
            <i :style="{ width: barWidth(dim) }" :class="barClass(dim)"></i>
          </div>
          <p v-if="dim.description" class="rp-dim-desc">{{ dim.description }}</p>
        </div>
      </div>
    </section>

    <!-- 亮点 / 不足 / 建议 -->
    <section v-if="hasAnalysis" class="rp-section">
      <h2 class="rp-h2">表现分析</h2>
      <div class="rp-analysis">
        <div v-if="strengths.length" class="rp-block">
          <div class="rp-h3 is-good">表现亮点</div>
          <ul>
            <li v-for="(t, i) in strengths" :key="i">{{ t }}</li>
          </ul>
        </div>
        <div v-if="weaknesses.length" class="rp-block">
          <div class="rp-h3 is-bad">待提升项</div>
          <ul>
            <li v-for="(t, i) in weaknesses" :key="i">{{ t }}</li>
          </ul>
        </div>
        <div v-if="suggestions.length" class="rp-block">
          <div class="rp-h3">改进建议</div>
          <ol>
            <li v-for="(t, i) in suggestions" :key="i">{{ t }}</li>
          </ol>
        </div>
      </div>
    </section>

    <!-- 教师复核意见 -->
    <section v-if="detail.review?.comment" class="rp-section">
      <h2 class="rp-h2">教师评语</h2>
      <blockquote class="rp-comment">
        {{ detail.review.comment }}
        <footer>—— {{ detail.review.reviewerName || "教师" }}，{{ detail.review.reviewTime }}</footer>
      </blockquote>
    </section>

    <!-- 答辩记录 -->
    <section v-if="detail.defense" class="rp-section">
      <h2 class="rp-h2">项目答辩记录</h2>
      <div class="rp-qa">
        <div class="rp-qa-row">
          <span class="rp-qa-tag is-a">陈述</span>
          <p>{{ detail.defense.defenseAnswer || "（无内容）" }}</p>
        </div>
        <audio
          v-if="detail.defense.defenseAnswerFile"
          :src="detail.defense.defenseAnswerFile"
          controls
          preload="none"
          class="rp-audio no-print"
        ></audio>
      </div>
    </section>

    <!-- 问答记录 -->
    <section v-if="qa.length" class="rp-section">
      <h2 class="rp-h2">问答记录</h2>
      <div v-for="(item, i) in qa" :key="item.id" class="rp-qa">
        <div class="rp-qa-index">第 {{ i + 1 }} 题</div>
        <div class="rp-qa-row">
          <span class="rp-qa-tag">问</span>
          <p class="rp-q">{{ plain(item.question) }}</p>
        </div>
        <div class="rp-qa-row">
          <span class="rp-qa-tag is-a">答</span>
          <p>{{ item.answer || "（未作答）" }}</p>
        </div>
        <audio v-if="item.answerFile" :src="item.answerFile" controls preload="none" class="rp-audio no-print"></audio>
        <template v-if="item.followQuestion">
          <div class="rp-qa-row is-follow">
            <span class="rp-qa-tag">追问</span>
            <p class="rp-q">{{ plain(item.followQuestion) }}</p>
          </div>
          <div class="rp-qa-row is-follow">
            <span class="rp-qa-tag is-a">答</span>
            <p>{{ item.followAnswer || "（未作答）" }}</p>
          </div>
          <audio
            v-if="item.followAnswerFile"
            :src="item.followAnswerFile"
            controls
            preload="none"
            class="rp-audio no-print"
          ></audio>
        </template>
      </div>
    </section>

    <footer class="rp-foot">
      本报告由 AI 根据面试录音的转写内容和考核标准生成{{ reviewed ? "，分数已经教师复核" : "" }}，仅供教学评估参考。
    </footer>
  </article>
</template>

<script setup lang="ts">
import { computed } from "vue";
import dayjs from "dayjs";
import type { ReportDetail, ReportDimension } from "./types";
import { BRAND } from "@/config/brand";
import logoRed from "@/assets/brand/logo-marx-red.png";

const props = defineProps<{ detail: ReportDetail }>();

const reportNo = computed(() => `IR-${String(props.detail.id).padStart(6, "0")}`);
const reviewed = computed(() => !!props.detail.review);
const passed = computed(
  () => props.detail.score != null && props.detail.score >= (props.detail.passScore ?? 0)
);
const dimensions = computed(() => props.detail.dimensions || []);
const strengths = computed(() => clean(props.detail.analysis?.strengths));
const weaknesses = computed(() => clean(props.detail.analysis?.weaknesses));
const suggestions = computed(() => clean(props.detail.analysis?.suggestions));
const hasAnalysis = computed(() => strengths.value.length + weaknesses.value.length + suggestions.value.length > 0);
const qa = computed(() => props.detail.result || []);

const infoItems = computed(() => {
  const d = props.detail;
  const parts = [d.defenseEnabled ? "项目答辩" : "", d.questionEnabled ? "随机问答" : ""].filter(Boolean);
  return [
    { label: "姓名", value: d.studentName },
    { label: "学号", value: d.studentAccount },
    { label: "班级", value: d.schoolClass },
    { label: "面试时间", value: d.interviewTime ? dayjs(d.interviewTime).format("YYYY-MM-DD HH:mm") : "" },
    { label: "面试环节", value: parts.join(" + ") },
    { label: "问答题数", value: d.questionEnabled ? `${qa.value.length} 题` : "" },
  ];
});

function clean(list?: string[]) {
  return (list || []).map((t) => (t || "").trim()).filter(Boolean);
}

function plain(text?: string | null) {
  return (text || "").replace(/[*#`>_]/g, "");
}

function barWidth(dim: ReportDimension) {
  const max = dim.maxScore || props.detail.totalScore || 100;
  return `${Math.max(0, Math.min(100, (dim.score / max) * 100))}%`;
}

function barClass(dim: ReportDimension) {
  if (!dim.maxScore) return "";
  const ratio = dim.score / dim.maxScore;
  return ratio >= 0.8 ? "is-high" : ratio < 0.6 ? "is-low" : "";
}
</script>

<style scoped>
.report {
  --rp-text: #1f2329;
  --rp-text-2: #4e5969;
  --rp-line: #e5e6eb;
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
  padding: 48px 56px;
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  color: var(--rp-text);
  font-size: 14px;
  line-height: 1.75;
}

.rp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--c-primary);
  font-size: 12px;
  color: var(--rp-text-2);
}

.rp-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--rp-text);
}

.rp-logo {
  display: block;
  height: 30px;
  width: auto;
}

.rp-product {
  padding-left: 12px;
  border-left: 1px solid var(--rp-line);
  font-size: 13px;
  font-weight: 500;
  color: var(--rp-text-2);
  letter-spacing: 1px;
}

.rp-meta {
  display: flex;
  gap: 16px;
}

.rp-title {
  margin: 32px 0 4px;
  color: var(--c-primary-deep);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 2px;
  text-align: center;
}

.rp-subtitle {
  text-align: center;
  color: var(--rp-text-2);
}

.rp-info {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 28px;
  border: 1px solid var(--rp-line);
  border-radius: 6px;
  overflow: hidden;
}

.rp-info-item {
  display: flex;
  border-bottom: 1px solid var(--rp-line);
}

.rp-info-item:nth-last-child(-n + 3) {
  border-bottom: 0;
}

.rp-info-label {
  flex-shrink: 0;
  width: 76px;
  padding: 8px 12px;
  background: #f7f8fa;
  color: var(--rp-text-2);
  font-size: 13px;
}

.rp-info-value {
  padding: 8px 12px;
  font-size: 13px;
  word-break: break-all;
}

.rp-section {
  margin-top: 32px;
  break-inside: avoid-page;
}

.rp-h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  font-size: 17px;
  font-weight: 600;
}

.rp-h2::before {
  content: "";
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: var(--c-primary);
}

.rp-h3 {
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 600;
}

.rp-h3.is-good {
  color: #00a32a;
}

.rp-h3.is-bad {
  color: #e8590c;
}

.rp-overview {
  display: flex;
  gap: 32px;
  padding: 24px;
  border: 1px solid var(--rp-line);
  border-radius: 6px;
  background: #fafbfc;
}

.rp-score {
  flex-shrink: 0;
  width: 168px;
  text-align: center;
  border-right: 1px solid var(--rp-line);
  padding-right: 24px;
}

.rp-score-label {
  font-size: 13px;
  color: var(--rp-text-2);
}

.rp-score-value {
  margin: 4px 0 8px;
  font-size: 48px;
  font-weight: 700;
  line-height: 1.1;
}

.rp-score-value small {
  margin-left: 4px;
  font-size: 16px;
  font-weight: 400;
  color: var(--rp-text-2);
}

.rp-score-value.is-pass {
  color: var(--c-primary);
}

.rp-score-value.is-fail {
  color: #f53f3f;
}

.rp-verdict {
  display: inline-block;
  padding: 0 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 24px;
  font-weight: 500;
}

.rp-verdict.is-pass {
  background: #e8ffea;
  color: #00a32a;
}

.rp-verdict.is-fail {
  background: #ffece8;
  color: #f53f3f;
}

.rp-score-foot {
  margin-top: 10px;
  font-size: 12px;
  color: var(--rp-text-2);
}

.rp-summary p {
  margin: 0;
  color: var(--rp-text);
}

.rp-reviewed-note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--c-primary);
}

.rp-dims {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.rp-dim {
  break-inside: avoid;
}

.rp-dim-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.rp-dim-name {
  font-weight: 600;
}

.rp-dim-score b {
  font-size: 16px;
}

.rp-arrow {
  margin: 0 4px;
  color: var(--rp-text-2);
}

.rp-bar {
  height: 8px;
  margin: 6px 0;
  overflow: hidden;
  border-radius: 4px;
  background: #f0f1f2;
}

.rp-bar i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--c-primary);
}

.rp-bar i.is-high {
  background: #00b42a;
}

.rp-bar i.is-low {
  background: #ff7d00;
}

.rp-dim-desc {
  margin: 0;
  color: var(--rp-text-2);
  font-size: 13px;
}

.rp-analysis {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rp-block ul,
.rp-block ol {
  margin: 0;
  padding-left: 20px;
  color: var(--rp-text);
}

.rp-block li + li {
  margin-top: 4px;
}

.rp-comment {
  margin: 0;
  padding: 16px 20px;
  border-left: 3px solid var(--c-primary);
  background: #fdf6f4;
  white-space: pre-line;
}

.rp-comment footer {
  margin-top: 8px;
  font-size: 12px;
  color: var(--rp-text-2);
  text-align: right;
}

.rp-qa {
  padding: 16px 0;
  border-bottom: 1px dashed var(--rp-line);
  break-inside: avoid;
}

.rp-qa:first-of-type {
  padding-top: 0;
}

.rp-qa-index {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--rp-text-2);
}

.rp-qa-row {
  display: flex;
  gap: 12px;
}

.rp-qa-row + .rp-qa-row {
  margin-top: 6px;
}

.rp-qa-row.is-follow {
  margin-left: 24px;
}

.rp-qa-row p {
  margin: 0;
  flex: 1;
  white-space: pre-line;
}

.rp-q {
  font-weight: 500;
}

.rp-qa-tag {
  flex-shrink: 0;
  min-width: 32px;
  height: 22px;
  padding: 0 6px;
  margin-top: 2px;
  border-radius: 4px;
  background: #f2f3f5;
  color: var(--rp-text-2);
  font-size: 12px;
  line-height: 22px;
  text-align: center;
}

.rp-qa-tag.is-a {
  background: var(--c-primary-soft);
  color: var(--c-primary);
}

.rp-audio {
  display: block;
  width: 100%;
  max-width: 420px;
  height: 32px;
  margin: 8px 0 0 44px;
}

.rp-foot {
  margin-top: 40px;
  padding-top: 12px;
  border-top: 1px solid var(--rp-line);
  font-size: 12px;
  color: var(--rp-text-2);
  text-align: center;
}

@media (max-width: 720px) {
  .report {
    padding: 24px 16px;
  }
  .rp-info {
    grid-template-columns: 1fr;
  }
  .rp-overview {
    flex-direction: column;
  }
  .rp-score {
    width: auto;
    border-right: 0;
    padding-right: 0;
  }
}

@media print {
  .report {
    max-width: none;
    padding: 0;
    border: 0;
    border-radius: 0;
    font-size: 12px;
  }
  .rp-title {
    margin-top: 20px;
  }
  .rp-section {
    margin-top: 24px;
  }
}
</style>
