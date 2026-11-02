<template>
  <article class="report">
    <div class="rp-brand">
      <img :src="logoBlue" :alt="BRAND.school + BRAND.college" />
      <span>报告编号：{{ reportNo }}</span>
    </div>

    <h1 class="rp-title">
      面试成绩分析报告
      <span class="rp-score num">[ {{ detail.score ?? "—" }}/{{ detail.totalScore ?? 100 }} 分]</span>
    </h1>

    <!-- 基本信息 -->
    <section class="rp-section">
      <h2 class="rp-h2">[基本信息]</h2>
      <p class="rp-info">
        <span v-for="item in infoItems" :key="item.label">
          <b>{{ item.label }}：</b>{{ item.value || "—" }}
        </span>
      </p>
    </section>

    <!-- 整体评价 -->
    <section class="rp-section">
      <h2 class="rp-h2">[整体评价]</h2>
      <p class="rp-blue">{{ detail.summary || "暂无整体评价" }}</p>
      <p class="rp-note">
        结论：<b :class="passed ? 'is-pass' : 'is-fail'">{{ passed ? "通过" : "未通过" }}</b>（及格线
        {{ detail.passScore ?? "—" }} 分）<template v-if="reviewed">
          。AI 初评 {{ detail.aiScore }} 分，已由{{ detail.review?.reviewerName ? `${detail.review.reviewerName}老师` : "教师" }}复核调整。</template
        >
      </p>
      <template v-if="strengths.length">
        <div class="rp-label">[主要优点]</div>
        <p v-for="(t, i) in strengths" :key="'s' + i" class="rp-blue">{{ i + 1 }}. {{ t }}</p>
      </template>
      <template v-if="weaknesses.length">
        <div class="rp-label">[主要不足]</div>
        <p v-for="(t, i) in weaknesses" :key="'w' + i" class="rp-blue">{{ i + 1 }}. {{ t }}</p>
      </template>
    </section>

    <!-- 分项评分 -->
    <section v-if="dimensions.length" class="rp-section">
      <h2 class="rp-h2">[分项评分]</h2>
      <div v-for="dim in dimensions" :key="dim.name" class="rp-item">
        <div class="rp-label">
          [{{ dim.name }}]
          <span class="num">{{ dim.score }}/{{ dim.maxScore ?? "—" }} 分</span>
          <span v-if="dim.score !== dim.aiScore" class="rp-ai num">（AI 初评 {{ dim.aiScore }} 分）</span>
        </div>
        <p v-if="dim.description" class="rp-blue">{{ dim.description }}</p>
      </div>
    </section>

    <!-- 项目答辩 -->
    <section v-if="detail.defense" class="rp-section">
      <h2 class="rp-h2">[项目答辩]</h2>
      <div class="rp-label">[陈述原话]</div>
      <p class="rp-quote">{{ detail.defense.defenseAnswer || "（无内容）" }}</p>
      <audio
        v-if="detail.defense.defenseAnswerFile"
        :src="detail.defense.defenseAnswerFile"
        controls
        preload="none"
        class="rp-audio no-print"
      ></audio>
      <template v-if="hasComment(detail.defense)">
        <div class="rp-label">[陈述评价]</div>
        <p v-if="detail.defense.strength" class="rp-blue"><b>优点</b>{{ detail.defense.strength }}</p>
        <p v-if="detail.defense.weakness" class="rp-blue"><b>缺点</b>{{ detail.defense.weakness }}</p>
        <p v-if="detail.defense.suggestion" class="rp-blue"><b>如何改进</b>{{ detail.defense.suggestion }}</p>
      </template>
    </section>

    <!-- 面试问答 -->
    <section v-if="qa.length" class="rp-section">
      <h2 class="rp-h2">[面试问答]</h2>
      <div v-for="(item, i) in qa" :key="item.id" class="rp-item">
        <div class="rp-label">[问题内容 · 第 {{ i + 1 }} 题]</div>
        <p>{{ plain(item.question) }}</p>
        <div class="rp-label">[回答内容]</div>
        <p class="rp-answer">{{ item.answer || "（未作答）" }}</p>
        <audio v-if="item.answerFile" :src="item.answerFile" controls preload="none" class="rp-audio no-print"></audio>
        <template v-if="item.followQuestion">
          <div class="rp-label">[追问内容]</div>
          <p>{{ plain(item.followQuestion) }}</p>
          <div class="rp-label">[追问回答]</div>
          <p class="rp-answer">{{ item.followAnswer || "（未作答）" }}</p>
          <audio
            v-if="item.followAnswerFile"
            :src="item.followAnswerFile"
            controls
            preload="none"
            class="rp-audio no-print"
          ></audio>
        </template>
        <template v-if="item.strength">
          <div class="rp-label">[优势]</div>
          <p>{{ item.strength }}</p>
        </template>
        <template v-if="item.weakness">
          <div class="rp-label">[劣势]</div>
          <p>{{ item.weakness }}</p>
        </template>
        <template v-if="item.suggestion">
          <div class="rp-label">[建议]</div>
          <p>{{ item.suggestion }}</p>
        </template>
      </div>
    </section>

    <!-- 教师评语 -->
    <section v-if="detail.review?.comment" class="rp-section">
      <h2 class="rp-h2">[教师评语]</h2>
      <p class="rp-blue">{{ detail.review.comment }}</p>
      <p class="rp-note">—— {{ detail.review.reviewerName || "教师" }}，{{ detail.review.reviewTime }}</p>
    </section>

    <!-- 改进建议 -->
    <section v-if="suggestions.length" class="rp-section">
      <h2 class="rp-h2">[改进建议]</h2>
      <p v-for="(t, i) in suggestions" :key="i" class="rp-blue">{{ i + 1 }}. {{ t }}</p>
    </section>

    <footer class="rp-foot">
      <p><b>报告由{{ BRAND.school }}{{ BRAND.college }}{{ BRAND.product }}生成</b></p>
      <p><b>时间：</b>{{ reportTime }}</p>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { computed } from "vue";
import dayjs from "dayjs";
import type { ReportDetail } from "./types";
import { BRAND } from "@/config/brand";
import logoBlue from "@/assets/brand/logo-marx-blue.png";

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
const qa = computed(() => props.detail.result || []);
const reportTime = computed(() => {
  const t = props.detail.review?.reviewTime || props.detail.interviewTime;
  return t ? dayjs(t).format("YYYY-MM-DD HH:mm:ss") : "—";
});

const infoItems = computed(() => {
  const d = props.detail;
  const parts = [d.defenseEnabled ? "项目答辩" : "", d.questionEnabled ? "随机问答" : ""].filter(Boolean);
  return [
    { label: "姓名", value: d.studentName },
    { label: "学号", value: d.studentAccount },
    { label: "班级", value: d.schoolClass },
    { label: "面试名称", value: d.theme },
    { label: "面试时间", value: d.interviewTime ? dayjs(d.interviewTime).format("YYYY-MM-DD HH:mm") : "" },
    { label: "面试环节", value: parts.join(" + ") },
  ];
});

function clean(list?: string[]) {
  return (list || []).map((t) => (t || "").trim()).filter(Boolean);
}

function plain(text?: string | null) {
  return (text || "").replace(/[*#`>_]/g, "");
}

function hasComment(c: { strength?: string | null; weakness?: string | null; suggestion?: string | null }) {
  return !!(c.strength || c.weakness || c.suggestion);
}
</script>

<style scoped>
/* 版式按学院提供的《面试成绩分析报告》样张：宋体正文，灰底方括号标题，评价蓝字，学生原话下划线 */
.report {
  --rp-blue: #3341c9;
  --rp-red: #e0252d;
  --rp-gray-bg: #d9d9d9;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  padding: 36px 52px 48px;
  background: #fff;
  border-left: 1px solid #8c8c8c;
  border-right: 1px solid #8c8c8c;
  color: #333;
  font-family: "Times New Roman", "SimSun", "宋体", "Songti SC", "STSong", serif;
  font-size: 15px;
  line-height: 1.8;
}

.rp-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid #d9d9d9;
  font-size: 12px;
  color: #888;
}

.rp-brand img {
  display: block;
  height: 30px;
  width: auto;
}

.rp-title {
  margin: 28px 0 8px;
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  font-size: 30px;
  font-weight: 400;
  line-height: 1.4;
  color: #000;
}

.rp-score {
  display: inline-block;
  margin-left: 8px;
  padding: 0 6px;
  background: #f5b8c3;
  color: var(--rp-red);
  font-weight: 700;
}

.rp-section {
  margin-top: 30px;
}

/* display: table 让灰底只包住文字，同时独占一行 */
.rp-h2 {
  display: table;
  margin: 0 0 12px;
  padding: 0 4px;
  background: var(--rp-gray-bg);
  font-family: "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  font-size: 24px;
  font-weight: 400;
  line-height: 1.5;
  color: #000;
  break-after: avoid-page;
}

.rp-label {
  display: table;
  margin: 10px 0 4px;
  padding: 0 3px;
  background: var(--rp-gray-bg);
  font-family: "Times New Roman", "SimHei", "黑体", "Microsoft YaHei", sans-serif;
  font-size: 14px;
  font-weight: 700;
  break-after: avoid-page;
  line-height: 1.6;
  color: #000;
}

.rp-label + p,
.rp-label + .rp-quote,
.rp-label + .rp-answer {
  margin-top: 0;
}

.report p {
  margin: 0 0 4px;
  text-align: justify;
  white-space: pre-line;
}

.rp-blue {
  color: var(--rp-blue);
}

.rp-blue b,
.rp-info b,
.rp-note b {
  margin-right: 4px;
  font-family: "Times New Roman", "SimHei", "黑体", "Microsoft YaHei", sans-serif;
}

.rp-quote {
  color: #000;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.rp-answer {
  color: var(--rp-red);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.rp-info {
  display: flex;
  flex-wrap: wrap;
  column-gap: 28px;
}

.rp-info b {
  color: #000;
}

.rp-note {
  color: #666;
  font-size: 14px;
}

.rp-note .is-pass {
  color: #1a8f3c;
}

.rp-note .is-fail {
  color: var(--rp-red);
}

.rp-ai {
  font-weight: 400;
  color: #666;
}

.rp-item {
  margin-bottom: 18px;
}

.rp-item + .rp-item {
  padding-top: 6px;
}

.rp-audio {
  display: block;
  width: 100%;
  max-width: 420px;
  height: 36px;
  margin: 6px 0 4px;
}

.rp-foot {
  margin-top: 40px;
  color: #000;
  font-family: "Times New Roman", "SimHei", "黑体", "Microsoft YaHei", sans-serif;
}

@media (max-width: 640px) {
  .report {
    padding: 24px 16px 32px;
  }
  .rp-title {
    font-size: 24px;
  }
  .rp-h2 {
    font-size: 20px;
  }
}

@media print {
  .report {
    max-width: none;
    padding: 0 8mm;
    font-size: 13px;
  }
  .rp-title {
    margin-top: 16px;
  }
}
</style>
