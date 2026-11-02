<template>
  <div class="room">
    <header class="room-header">
      <div class="room-brand">
        <img :src="logoBlue" :alt="BRAND.school + BRAND.college" class="room-mark" />
        <span class="room-theme" :title="state?.theme">{{ state?.theme || "AI 面试" }}</span>
      </div>
      <div class="room-steps" v-if="state && stage !== 'done'">
        <template v-for="(step, i) in steps" :key="step.key">
          <span v-if="i > 0" class="room-step-line" :class="{ 'is-done': i <= stepIndex }"></span>
          <span class="room-step" :class="{ 'is-active': i === stepIndex, 'is-done': i < stepIndex }">
            <span class="room-step-dot">
              <CheckOutlined v-if="i < stepIndex" />
              <template v-else>{{ i + 1 }}</template>
            </span>
            {{ step.label }}
          </span>
        </template>
      </div>
      <div class="room-timer" :class="{ 'is-warn': timerWarn }">
        <template v-if="timerVisible">
          <span class="room-timer-label">剩余时间</span>
          <span class="num">{{ timerText }}</span>
        </template>
      </div>
    </header>

    <!-- 加载 / 提交中 -->
    <div v-if="stage === 'loading' || stage === 'submitting'" class="room-center">
      <Spin size="large" />
      <div class="mt-4 muted">{{ loadingText }}</div>
    </div>

    <!-- 完成 -->
    <div v-else-if="stage === 'done'" class="room-center">
      <div class="done-card">
        <CheckCircleFilled class="done-icon" />
        <h2>面试已提交</h2>
        <p class="muted">系统正在转写录音并生成面试报告，通常需要 1～3 分钟。</p>
        <div class="mt-6 flex justify-center gap-3">
          <Button @click="router.replace('/student/interviews')">返回我的面试</Button>
          <Button type="primary" @click="router.replace(`/student/reports/${assessmentId}`)">查看面试报告</Button>
        </div>
      </div>
    </div>

    <!-- 面试须知 + 设备检测 -->
    <div v-else-if="stage === 'intro'" class="room-body" @pointerdown="recorder.resume()">
      <section class="panel panel-main">
        <div class="panel-label">面试须知</div>
        <h1 class="intro-title">{{ state?.theme }}</h1>
        <ol class="intro-list">
          <li v-if="state?.defenseEnabled">
            <b>项目答辩</b>：介绍你的项目，最长 {{ formatSeconds(state?.duration || 0) }}。
          </li>
          <li v-if="state?.questionEnabled">
            <b>随机问答</b>：系统从题库随机抽题，每题作答最长 {{ formatSeconds(state?.answerTime || 0) }}{{
              state?.followUpEnabled ? "，部分题目回答后会有追问" : ""
            }}。
          </li>
          <li>每道题先语音播报，播报结束后自动开始录音；作答满 {{ MIN_RECORD_SECONDS }} 秒后可提前结束。</li>
          <li>回答提交后不可修改。中途刷新或断网不会丢失已提交的回答，重新进入即可继续。</li>
          <li>请在安静的环境中作答，建议佩戴耳机，避免题目播报被麦克风录入。</li>
        </ol>
        <div class="intro-actions">
          <Button type="primary" size="large" :loading="busy" @click="confirmBegin">开始面试</Button>
          <Button size="large" @click="router.replace('/student/interviews')">稍后再来</Button>
        </div>
      </section>
      <aside class="panel-side">
        <div class="panel">
          <div class="panel-label">麦克风检测</div>
          <div class="mic mt-0">
            <div class="mic-head">
              <span><AudioOutlined /> 麦克风</span>
              <span :class="micHeard ? 'text-ok' : 'text-warn'">
                {{ micHeard ? "检测正常" : "请对着麦克风说一句话" }}
              </span>
            </div>
            <div class="meter"><i :style="{ width: meterWidth }"></i></div>
            <Select
              v-if="recorder.devices.value.length > 1"
              class="w-full mt-3"
              :value="recorder.deviceId.value"
              :options="deviceOptions"
              :disabled="busy"
              @change="onDeviceSelect"
            />
            <div v-else class="mt-2 text-xs muted">{{ recorder.deviceLabel.value || "默认麦克风" }}</div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 抽题 -->
    <div v-else-if="stage === 'drawing'" class="room-center">
      <div class="draw">
        <span v-for="n in 3" :key="n" :style="{ animationDelay: `${n * 0.12}s` }">?</span>
      </div>
      <div class="mt-6 text-lg font-medium">正在从题库随机抽题</div>
      <div class="mt-1 muted">本次共 {{ state?.total }} 道题</div>
    </div>

    <!-- 答辩 / 问答 -->
    <div v-else class="room-body">
      <section class="panel panel-main">
        <template v-if="stage === 'defense'">
          <div class="panel-label">项目答辩</div>
          <h1 class="question-text">
            请介绍你的项目：背景与目标、整体设计、你负责的部分，以及遇到的难点和解决方式。
          </h1>
          <ul class="defense-hints">
            <li>建议时长 {{ formatSeconds(state?.duration || 0) }} 以内，结构清晰比面面俱到更重要</li>
            <li>点击「开始答辩」后开始录音，答满 {{ MIN_RECORD_SECONDS }} 秒后可提前结束</li>
          </ul>
        </template>
        <template v-else-if="state?.current">
          <div class="panel-label">
            问题 {{ state.current.index }}
            <span class="muted">/ {{ state.total }}</span>
            <template v-if="state.current.followUp">
              · 追问<template v-if="state.current.followTotal > 1">
                {{ state.current.followIndex }}<span class="muted"> / {{ state.current.followTotal }}</span></template
              >
            </template>
          </div>
          <div class="question-text md" v-html="renderMarkdown(state.current.content)"></div>
        </template>

        <div class="status-bar">
          <span class="status-dot" :class="`is-${phaseKey}`"></span>
          <span class="status-text">{{ statusText }}</span>
          <div v-if="isRecording" class="level">
            <i v-for="n in 12" :key="n" :class="{ on: levelBars >= n }"></i>
          </div>
          <span v-if="isRecording" class="muted num ml-auto">已录 {{ formatSeconds(recordedSeconds) }}</span>
        </div>
      </section>

      <aside class="panel-side">
        <div class="panel">
          <div class="panel-label">麦克风</div>
          <div class="meter"><i :style="{ width: meterWidth }"></i></div>
          <div class="mt-2 text-xs muted truncate">{{ recorder.deviceLabel.value || "默认麦克风" }}</div>
        </div>
        <div class="panel">
          <div class="panel-label">作答提示</div>
          <ul class="tips">
            <li>先说结论，再展开理由和例子</li>
            <li>结合你亲自做过的项目和数据</li>
            <li>不会的问题可以说明思路，不要沉默</li>
          </ul>
        </div>
        <div class="panel" v-if="stage === 'question'">
          <div class="panel-label">答题进度</div>
          <div class="slots">
            <span
              v-for="n in state?.total || 0"
              :key="n"
              class="slot"
              :class="{ 'is-done': n <= (state?.answeredCount || 0), 'is-current': state?.current?.index === n }"
              >{{ n }}</span
            >
          </div>
          <div class="mt-3 text-xs muted">已完成 {{ state?.answeredCount || 0 }} / {{ state?.total || 0 }} 题</div>
        </div>
      </aside>
    </div>

    <footer v-if="stage === 'defense' || stage === 'question'" class="room-footer">
      <div class="footer-track"><i :style="{ width: countdownPercent + '%' }"></i></div>
      <Button
        type="primary"
        size="large"
        class="footer-action"
        :disabled="actionDisabled"
        :loading="busy"
        @click="onMainAction"
        >{{ actionText }}</Button
      >
    </footer>

    <!-- 阻塞性提示 -->
    <Modal
      :open="!!blocking"
      :title="blocking?.title"
      :closable="false"
      :mask-closable="false"
      :keyboard="false"
      :footer="null"
      centered
      :width="460"
    >
      <div @pointerdown="recorder.resume()">
        <div class="blocking-msg">{{ blocking?.message }}</div>
        <div v-if="blocking?.micCheck" class="mic mt-4">
          <div class="mic-head">
            <span><AudioOutlined /> 麦克风</span>
            <span :class="micHeard ? 'text-ok' : 'text-warn'">
              {{ micHeard ? "检测正常" : "请对着麦克风说一句话" }}
            </span>
          </div>
          <div class="meter"><i :style="{ width: meterWidth }"></i></div>
          <Select
            v-if="recorder.devices.value.length > 1"
            class="w-full mt-3"
            :value="recorder.deviceId.value"
            :options="deviceOptions"
            :disabled="busy"
            @change="onDeviceSelect"
          />
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <Button
            v-for="action in [...(blocking?.actions || [])].reverse()"
            :key="action.label"
            :type="action.primary ? 'primary' : 'default'"
            :loading="busy && action.primary"
            @click="action.run"
            >{{ action.label }}</Button
          >
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Modal, Select, Spin, message } from "ant-design-vue";
import {
  AudioOutlined,
  CheckCircleFilled,
  CheckOutlined,
} from "@ant-design/icons-vue";
import MarkdownIt from "markdown-it";
import hljs from "markdown-it-highlightjs";
import {
  InterviewState,
  getInterviewState,
  getQuestionAudio,
  startInterview,
  submitInterview,
  uploadAnswerAudio,
  uploadDefenseAudio,
} from "@/api/interview";
import { SILENCE_PEAK, useAudioRecorder } from "@/composables/useAudioRecorder";
import { BRAND } from "@/config/brand";
import logoBlue from "@/assets/brand/logo-marx-blue.png";

type Stage = "loading" | "intro" | "defense" | "drawing" | "question" | "submitting" | "done";
type QuestionPhase = "idle" | "playing" | "recording" | "uploading";
interface BlockingAction {
  label: string;
  primary?: boolean;
  run: () => void;
}
interface Blocking {
  title: string;
  message: string;
  actions: BlockingAction[];
  micCheck?: boolean;
}

const MIN_RECORD_SECONDS = 3;
const UPLOAD_ATTEMPTS = 3;
const AUDIO_FETCH_TIMEOUT = 8000;

const md = new MarkdownIt().use(hljs);
const renderMarkdown = (text: string) => md.render(text || "");

const route = useRoute();
const router = useRouter();
const assessmentId = computed(() => String(route.query?.id || ""));
const recorder = useAudioRecorder();

const state = ref<InterviewState | null>(null);
const stage = ref<Stage>("loading");
const questionPhase = ref<QuestionPhase>("idle");
const defenseRecording = ref(false);
const busy = ref(false);
const blocking = ref<Blocking | null>(null);
const loadingText = ref("正在加载中，请稍后...");
const countdown = ref(0);
const countdownTotal = ref(0);
const recordStartedAt = ref(0);
const now = ref(Date.now());

let countdownTimer: number | undefined;
let clockTimer: number | undefined;
let currentAudio: HTMLAudioElement | null = null;
let currentAudioUrl = "";
let askedSlotKey = "";
let drawingShown = false;
let pageOff = false;
let finishing = false;

const micHeard = computed(() => recorder.peakLevel.value >= SILENCE_PEAK);

const recordedSeconds = computed(() =>
  recordStartedAt.value ? Math.floor((now.value - recordStartedAt.value) / 1000) : 0
);
const canStopEarly = computed(() => recordedSeconds.value >= MIN_RECORD_SECONDS);
const waitSeconds = computed(() => Math.max(0, MIN_RECORD_SECONDS - recordedSeconds.value));

const actionText = computed(() => {
  if (stage.value === "defense") {
    if (busy.value) return "提交中";
    if (!defenseRecording.value) return "开始答辩";
    return canStopEarly.value ? "结束答辩" : `结束答辩(${waitSeconds.value}秒)`;
  }
  if (stage.value === "question") {
    if (questionPhase.value === "playing") return "跳过播报";
    if (questionPhase.value === "uploading") return "提交中";
    if (questionPhase.value === "recording") {
      return canStopEarly.value ? "提前结束" : `提前结束(${waitSeconds.value}秒)`;
    }
  }
  return "请稍候";
});
const actionDisabled = computed(() => {
  if (busy.value || blocking.value) return true;
  if (stage.value === "defense") return defenseRecording.value && !canStopEarly.value;
  if (stage.value === "question") {
    if (questionPhase.value === "playing") return false;
    if (questionPhase.value === "recording") return !canStopEarly.value;
  }
  return true;
});
const phaseText = computed(() => {
  switch (questionPhase.value) {
    case "playing":
      return "面试官正在提问";
    case "recording":
      return "正在录音，请作答";
    case "uploading":
      return "正在提交回答";
    default:
      return "准备中";
  }
});
const progressHint = computed(() => {
  const s = state.value;
  if (!s) return "";
  return `已完成 ${s.answeredCount} / ${s.total} 题`;
});

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function isNetworkError(error: unknown) {
  return Boolean((error as any)?.isAxiosError);
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function showBlocking(title: string, messageText: string, actions: BlockingAction[], micCheck = false) {
  blocking.value = { title, message: messageText, actions, micCheck };
  if (micCheck) recorder.resetPeak();
}

async function onDeviceChange(event: Event) {
  const id = (event.target as HTMLSelectElement).value;
  busy.value = true;
  try {
    if (!(await recorder.selectDevice(id))) {
      message.error(recorder.error.value || "切换麦克风失败");
    }
    recorder.resetPeak();
  } finally {
    busy.value = false;
  }
}

// 录音几乎是静音时让学生检查设备后重录，而不是把空录音交上去
function showSilentRecording(redo: () => void, submitAnyway: () => void) {
  showBlocking(
    "没有检测到你的声音",
    "这段录音几乎是静音的，可能是麦克风被静音或选错了设备。\n请对着麦克风说话，确认下方音量条有跳动后重新录音。",
    [
      { label: "重新录音", primary: true, run: () => { blocking.value = null; redo(); } },
      { label: "仍然提交", run: () => { blocking.value = null; submitAnyway(); } },
    ],
    true
  );
}

function clearCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = undefined;
  }
}

function startCountdown(seconds: number, onTimeout: () => void) {
  clearCountdown();
  countdownTotal.value = seconds;
  countdown.value = seconds;
  recordStartedAt.value = Date.now();
  countdownTimer = window.setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1);
    if (countdown.value <= 0) {
      clearCountdown();
      onTimeout();
    }
  }, 1000);
}

// 带退避重试的上传；网络错误重试，业务错误直接抛出
async function uploadWithRetry<T>(send: () => Promise<T>): Promise<T> {
  let lastError: unknown;
  for (let attempt = 1; attempt <= UPLOAD_ATTEMPTS; attempt++) {
    try {
      return await send();
    } catch (error) {
      lastError = error;
      if (!isNetworkError(error) || attempt === UPLOAD_ATTEMPTS) break;
      await sleep(1000 * 2 ** (attempt - 1));
    }
  }
  throw lastError;
}

async function loadStateWithRetry() {
  let lastError: unknown;
  for (let attempt = 1; attempt <= UPLOAD_ATTEMPTS; attempt++) {
    try {
      const res = await getInterviewState(assessmentId.value);
      return res.data as InterviewState;
    } catch (error) {
      lastError = error;
      if (!isNetworkError(error) || attempt === UPLOAD_ATTEMPTS) break;
      await sleep(1000 * attempt);
    }
  }
  throw lastError;
}

function goResult() {
  stopQuestionAudio();
  clearCountdown();
  recorder.dispose();
  stage.value = "done";
}

// 根据服务端状态推进页面
function applyState(next: InterviewState) {
  state.value = next;
  if (pageOff) return;
  switch (next.status) {
    case "NOT_STARTED":
      stage.value = "intro";
      break;
    case "DEFENSE":
      stage.value = "defense";
      countdownTotal.value = next.duration;
      countdown.value = next.duration;
      break;
    case "QUESTIONS":
      if (!drawingShown && next.answeredCount === 0 && !next.current?.followUp) {
        drawingShown = true;
        stage.value = "drawing";
        setTimeout(() => {
          if (pageOff) return;
          stage.value = "question";
          askCurrent();
        }, 2200);
      } else {
        drawingShown = true;
        stage.value = "question";
        askCurrent();
      }
      break;
    case "READY_TO_SUBMIT":
      submit();
      break;
    case "SUBMITTED":
      goResult();
      break;
  }
}

function confirmBegin() {
  if (busy.value) return;
  if (micHeard.value) {
    beginInterview();
    return;
  }
  Modal.confirm({
    title: "还没有检测到你的声音",
    content: "请确认麦克风没有被静音、选择了正确的设备，然后对着麦克风说一句话。确定要直接开始吗？",
    okText: "仍然开始",
    cancelText: "再检测一下",
    onOk: beginInterview,
  });
}

async function beginInterview() {
  if (busy.value) return;
  busy.value = true;
  try {
    const res = await startInterview(assessmentId.value);
    applyState(res.data);
  } catch (error) {
    if (isNetworkError(error)) {
      showBlocking("网络异常", "开始面试失败，请检查网络后重试。", [
        { label: "重试", primary: true, run: () => { blocking.value = null; beginInterview(); } },
      ]);
    }
  } finally {
    busy.value = false;
  }
}

async function submit() {
  if (finishing) return;
  finishing = true;
  stage.value = "submitting";
  loadingText.value = "正在提交面试，请稍候...";
  busy.value = true;
  try {
    await uploadWithRetry(() => submitInterview(assessmentId.value));
    goResult();
  } catch (error) {
    finishing = false;
    showBlocking("提交失败", getErrorMessage(error, "面试提交失败，请检查网络后重试。"), [
      { label: "重新提交", primary: true, run: () => { blocking.value = null; submit(); } },
    ]);
  } finally {
    busy.value = false;
  }
}

// ---------------- 答辩 ----------------
async function startDefense() {
  const started = await recorder.start();
  if (!started) {
    showMicError();
    return;
  }
  defenseRecording.value = true;
  startCountdown(state.value?.duration || 60, finishDefense);
}

function confirmFinishDefense() {
  if (!canStopEarly.value) return;
  Modal.confirm({
    title: "确认结束答辩吗？",
    okText: "确认结束",
    cancelText: "继续答辩",
    onOk: finishDefense,
  });
}

async function finishDefense() {
  if (!defenseRecording.value) return;
  clearCountdown();
  defenseRecording.value = false;
  recordStartedAt.value = 0;
  const audio = await recorder.stop();
  if (recorder.peakLevel.value < SILENCE_PEAK) {
    showSilentRecording(startDefense, () => sendDefense(audio));
    return;
  }
  await sendDefense(audio);
}

async function sendDefense(audio: Blob) {
  busy.value = true;
  try {
    const res = await uploadWithRetry(() => uploadDefenseAudio(assessmentId.value, audio));
    blocking.value = null;
    applyState(res.data);
  } catch (error) {
    const retry = { label: "重试上传", primary: true, run: () => sendDefense(audio) };
    const redo = { label: "重新答辩", run: () => { blocking.value = null; countdown.value = state.value?.duration || 0; } };
    showBlocking(
      "答辩提交失败",
      isNetworkError(error)
        ? "网络异常，答辩录音尚未提交。录音已保留，请检查网络后点击重试上传。"
        : getErrorMessage(error, "答辩提交失败"),
      isNetworkError(error) ? [retry, redo] : [reloadAction(), redo]
    );
  } finally {
    busy.value = false;
  }
}

// 业务错误（例如在另一个标签页已作答）时，以服务端进度为准重新加载
function reloadAction(): BlockingAction {
  return {
    label: "刷新进度",
    primary: true,
    run: async () => {
      blocking.value = null;
      stopQuestionAudio();
      clearCountdown();
      questionPhase.value = "idle";
      askedSlotKey = "";
      try {
        applyState(await loadStateWithRetry());
      } catch (error) {
        showBlocking("网络异常", "加载面试进度失败，请检查网络后重试。", [reloadAction()]);
      }
    },
  };
}

// ---------------- 问答 ----------------
function stopQuestionAudio() {
  if (currentAudio) {
    currentAudio.onended = null;
    currentAudio.onerror = null;
    currentAudio.pause();
    currentAudio = null;
  }
  if (currentAudioUrl) {
    URL.revokeObjectURL(currentAudioUrl);
    currentAudioUrl = "";
  }
  window.speechSynthesis?.cancel();
}

function playBlob(blob: Blob): Promise<void> {
  return new Promise((resolve, reject) => {
    currentAudioUrl = URL.createObjectURL(blob);
    const audio = new Audio(currentAudioUrl);
    currentAudio = audio;
    audio.onended = () => resolve();
    audio.onerror = () => reject(new Error("audio play failed"));
    audio.play().catch(reject);
  });
}

function speakText(text: string): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!window.speechSynthesis || typeof SpeechSynthesisUtterance === "undefined") {
      reject(new Error("speechSynthesis unavailable"));
      return;
    }
    const utterance = new SpeechSynthesisUtterance(text.replace(/[#*`>_-]/g, ""));
    utterance.lang = "zh-CN";
    utterance.onend = () => resolve();
    utterance.onerror = () => reject(new Error("speechSynthesis failed"));
    window.speechSynthesis.speak(utterance);
  });
}

async function fetchQuestionAudio(index: number, followIndex: number): Promise<Blob> {
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error("audio timeout")), AUDIO_FETCH_TIMEOUT)
  );
  const res: any = await Promise.race([
    getQuestionAudio(assessmentId.value, index, followIndex),
    timeout,
  ]);
  const blob: Blob = res?.data;
  if (!blob || !blob.size || !String(blob.type || "audio").startsWith("audio")) {
    throw new Error("invalid audio");
  }
  return blob;
}

// 播报当前题目（失败时逐级降级，绝不阻塞面试），随后开始作答
async function askCurrent() {
  const current = state.value?.current;
  if (!current) return;
  const slotKey = `${current.index}-${current.followIndex || 0}`;
  if (askedSlotKey === slotKey && questionPhase.value !== "idle") return;
  askedSlotKey = slotKey;
  questionPhase.value = "playing";
  countdownTotal.value = state.value?.answerTime || 0;
  countdown.value = countdownTotal.value;
  try {
    const blob = await fetchQuestionAudio(current.index, current.followIndex || 0);
    if (askedSlotKey !== slotKey || questionPhase.value !== "playing") return;
    await playBlob(blob);
  } catch (audioError) {
    if (askedSlotKey !== slotKey || questionPhase.value !== "playing") return;
    try {
      await speakText(current.content);
    } catch (speechError) {
      await sleep(1500);
    }
  }
  if (askedSlotKey === slotKey && questionPhase.value === "playing") {
    startAnswer();
  }
}

async function startAnswer() {
  stopQuestionAudio();
  const started = await recorder.start();
  if (!started) {
    questionPhase.value = "idle";
    showMicError();
    return;
  }
  questionPhase.value = "recording";
  startCountdown(state.value?.answerTime || 60, finishAnswer);
}

async function finishAnswer() {
  if (questionPhase.value !== "recording") return;
  const current = state.value?.current;
  if (!current) return;
  clearCountdown();
  recordStartedAt.value = 0;
  questionPhase.value = "uploading";
  const audio = await recorder.stop();
  if (recorder.peakLevel.value < SILENCE_PEAK) {
    showSilentRecording(
      () => { questionPhase.value = "idle"; startAnswer(); },
      () => sendAnswer(current.index, current.followIndex || 0, audio)
    );
    return;
  }
  await sendAnswer(current.index, current.followIndex || 0, audio);
}

async function sendAnswer(index: number, followIndex: number, audio: Blob) {
  busy.value = true;
  try {
    const res = await uploadWithRetry(() =>
      uploadAnswerAudio(assessmentId.value, index, followIndex, audio)
    );
    blocking.value = null;
    questionPhase.value = "idle";
    applyState(res.data);
  } catch (error) {
    const retry = { label: "重试上传", primary: true, run: () => sendAnswer(index, followIndex, audio) };
    const redo = {
      label: "重新回答",
      run: () => {
        blocking.value = null;
        questionPhase.value = "idle";
        startAnswer();
      },
    };
    showBlocking(
      "回答提交失败",
      isNetworkError(error)
        ? "网络异常，本题回答尚未提交。录音已保留，请检查网络后点击重试上传。"
        : getErrorMessage(error, "回答提交失败"),
      isNetworkError(error) ? [retry, redo] : [reloadAction(), redo]
    );
  } finally {
    busy.value = false;
  }
}

function onMainAction() {
  if (actionDisabled.value) return;
  if (stage.value === "defense") {
    if (!defenseRecording.value) startDefense();
    else confirmFinishDefense();
    return;
  }
  if (stage.value === "question") {
    if (questionPhase.value === "playing") {
      questionPhase.value = "idle";
      startAnswer();
    } else if (questionPhase.value === "recording") {
      finishAnswer();
    }
  }
}

// ---------------- 设备 ----------------
function showMicError() {
  showBlocking(
    "麦克风不可用",
    recorder.error.value || "无法打开麦克风，请允许浏览器麦克风权限后重试。",
    [
      {
        label: "重试",
        primary: true,
        run: async () => {
          blocking.value = null;
          await prepareDevices();
          if (state.value) applyState(state.value);
        },
      },
    ]
  );
}

async function prepareDevices() {
  loadingText.value = "正在检测麦克风...";
  return recorder.init();
}

// ---------------- 生命周期 ----------------
async function bootstrap() {
  stage.value = "loading";
  loadingText.value = "正在加载面试信息...";
  let initial: InterviewState;
  try {
    initial = await loadStateWithRetry();
  } catch (error) {
    showBlocking("加载失败", getErrorMessage(error, "面试信息加载失败，请检查网络后重试。"), [
      { label: "重试", primary: true, run: () => { blocking.value = null; bootstrap(); } },
      { label: "返回", run: () => router.replace("/student/interviews") },
    ]);
    return;
  }
  state.value = initial;
  if (initial.status === "SUBMITTED") {
    goResult();
    return;
  }
  if (initial.status === "READY_TO_SUBMIT") {
    submit();
    return;
  }
  const micOk = await prepareDevices();
  if (!micOk) {
    showMicError();
    return;
  }
  applyState(initial);
}

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (defenseRecording.value || questionPhase.value === "recording" || questionPhase.value === "uploading") {
    event.preventDefault();
    event.returnValue = "";
  }
};

onMounted(() => {
  clockTimer = window.setInterval(() => (now.value = Date.now()), 500);
  window.addEventListener("beforeunload", handleBeforeUnload);
  bootstrap();
});

onUnmounted(() => {
  pageOff = true;
  clearCountdown();
  clearInterval(clockTimer);
  stopQuestionAudio();
  recorder.dispose();
  window.removeEventListener("beforeunload", handleBeforeUnload);
});

// ---------------- 页面展示 ----------------
const steps = computed(() => {
  const list = [{ key: "intro", label: "面试须知" }];
  if (state.value?.defenseEnabled) list.push({ key: "defense", label: "项目答辩" });
  if (state.value?.questionEnabled) list.push({ key: "question", label: "随机问答" });
  list.push({ key: "submit", label: "提交" });
  return list;
});
const stepIndex = computed(() => {
  const key =
    stage.value === "intro" || stage.value === "loading"
      ? "intro"
      : stage.value === "defense"
      ? "defense"
      : stage.value === "drawing" || stage.value === "question"
      ? "question"
      : "submit";
  return Math.max(0, steps.value.findIndex((s) => s.key === key));
});
const isRecording = computed(() => defenseRecording.value || questionPhase.value === "recording");
const timerVisible = computed(
  () => (stage.value === "defense" || stage.value === "question") && countdownTotal.value > 0
);
const timerText = computed(() => formatSeconds(countdown.value));
const timerWarn = computed(() => isRecording.value && countdown.value <= 10);
const countdownPercent = computed(() =>
  countdownTotal.value ? (countdown.value / countdownTotal.value) * 100 : 0
);
const meterWidth = computed(() => `${Math.min(100, recorder.volumeLevel.value * 2)}%`);
const levelBars = computed(() => Math.round((Math.min(100, recorder.volumeLevel.value * 2.5) / 100) * 12));
const deviceOptions = computed(() =>
  recorder.devices.value.map((d) => ({ label: d.label, value: d.deviceId }))
);
const phaseKey = computed(() => {
  if (stage.value !== "defense") return questionPhase.value;
  if (defenseRecording.value) return "recording";
  return busy.value ? "uploading" : "idle";
});
const statusText = computed(() => {
  if (stage.value !== "defense") return phaseText.value;
  if (defenseRecording.value) return "正在录音，请开始答辩";
  return busy.value ? "正在提交答辩录音" : "准备好后点击「开始答辩」";
});

function formatSeconds(sec: number) {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function onDeviceSelect(value: unknown) {
  onDeviceChange({ target: { value } } as unknown as Event);
}

</script>

<style scoped>
.room {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--c-bg);
}

.room-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  height: 64px;
  padding: 0 24px;
  background: var(--c-surface);
  border-top: 3px solid var(--c-primary);
  border-bottom: 1px solid var(--c-border);
}

.room-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.room-mark {
  flex-shrink: 0;
  height: 34px;
  width: auto;
  padding-right: 14px;
  border-right: 1px solid var(--c-border);
}

.room-theme {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
}

.room-steps {
  display: flex;
  align-items: center;
  gap: 8px;
}

.room-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text-3);
  white-space: nowrap;
}

.room-step-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid var(--c-border);
  font-size: 12px;
}

.room-step.is-active {
  color: var(--c-text);
  font-weight: 600;
}

.room-step.is-active .room-step-dot {
  border-color: var(--c-primary);
  background: var(--c-primary);
  color: #fff;
}

.room-step.is-done {
  color: var(--c-text-2);
}

.room-step.is-done .room-step-dot {
  border-color: var(--c-primary);
  color: var(--c-primary);
}

.room-step-line {
  width: 28px;
  height: 1px;
  background: var(--c-border);
}

.room-step-line.is-done {
  background: var(--c-primary);
}

.room-timer {
  justify-self: end;
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 22px;
  font-weight: 600;
}

.room-timer-label {
  font-size: 13px;
  font-weight: 400;
  color: var(--c-text-3);
}

.room-timer.is-warn {
  color: var(--c-danger);
}

.room-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.room-body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 24px 104px;
}

.panel {
  padding: 20px 24px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 8px;
}

.panel-main {
  display: flex;
  flex-direction: column;
  min-height: 460px;
}

.panel-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-side .panel {
  padding: 16px;
}

.panel-label {
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-2);
}

.intro-title {
  margin: 0 0 16px;
  font-size: 22px;
  line-height: 32px;
  font-weight: 600;
}

.intro-list {
  margin: 0;
  padding-left: 20px;
  color: var(--c-text-2);
  line-height: 30px;
}

.intro-list b {
  color: var(--c-text);
  font-weight: 600;
}

.intro-actions {
  display: flex;
  gap: 12px;
  margin-top: auto;
  padding-top: 32px;
}

.question-text {
  margin: 0;
  font-size: 22px;
  line-height: 36px;
  font-weight: 500;
  color: var(--c-text);
}

.question-text.md :deep(p) {
  margin: 0 0 8px;
}

.question-text.md :deep(code) {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--c-bg);
  font-size: 0.9em;
}

.defense-hints,
.tips {
  margin: 20px 0 0;
  padding-left: 18px;
  color: var(--c-text-2);
  line-height: 28px;
}

.tips {
  margin: 0;
  font-size: 13px;
  line-height: 24px;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid var(--c-border-2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-text-3);
}

.status-dot.is-playing {
  background: var(--c-primary);
}

.status-dot.is-recording {
  background: var(--c-danger);
  animation: pulse 1.2s infinite;
}

.status-dot.is-uploading {
  background: var(--c-warning);
}

.status-text {
  font-weight: 500;
}

.level {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 16px;
  margin-left: 8px;
}

.level i {
  width: 3px;
  height: 100%;
  border-radius: 2px;
  background: var(--c-border);
  transition: background 0.1s;
}

.level i:nth-child(odd) {
  height: 60%;
}

.level i.on {
  background: var(--c-primary);
}

.mic {
  margin-top: 16px;
}

.mic-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--c-text-2);
}

.text-ok {
  color: var(--c-success);
}

.text-warn {
  color: var(--c-warning);
}

.meter {
  height: 6px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--c-border-2);
}

.meter i {
  display: block;
  height: 100%;
  background: var(--c-success);
  transition: width 0.1s;
}

.slots {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}

.slot {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  font-size: 13px;
  color: var(--c-text-3);
}

.slot.is-done {
  border-color: transparent;
  background: var(--c-primary-soft);
  color: var(--c-primary);
}

.slot.is-current {
  border-color: var(--c-primary);
  color: var(--c-primary);
  font-weight: 600;
}

.room-footer {
  position: fixed;
  inset: auto 0 0 0;
  display: flex;
  align-items: center;
  gap: 24px;
  height: 80px;
  padding: 0 max(24px, calc((100vw - 1280px) / 2 + 24px));
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.footer-track {
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--c-border-2);
}

.footer-track i {
  display: block;
  height: 100%;
  background: var(--c-primary);
  transition: width 1s linear;
}

.footer-action {
  min-width: 160px;
}

.done-card {
  width: 440px;
  max-width: 100%;
  padding: 40px 32px;
  text-align: center;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 8px;
}

.done-card h2 {
  margin: 16px 0 8px;
  font-size: 20px;
  font-weight: 600;
}

.done-icon {
  font-size: 48px;
  color: var(--c-success);
}

.draw {
  display: flex;
  gap: 16px;
}

.draw span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 96px;
  border-radius: 8px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  color: var(--c-primary);
  font-size: 28px;
  font-weight: 600;
  animation: flip 1.1s ease-in-out infinite alternate;
}

.blocking-msg {
  white-space: pre-line;
  line-height: 24px;
  color: var(--c-text-2);
}

@keyframes pulse {
  50% {
    opacity: 0.35;
  }
}

@keyframes flip {
  to {
    transform: translateY(-10px);
    box-shadow: 0 8px 20px rgba(195, 25, 42, 0.15);
  }
}

@media (max-width: 960px) {
  .room-body {
    grid-template-columns: 1fr;
  }
  .room-steps {
    display: none;
  }
  .room-header {
    grid-template-columns: 1fr auto;
  }
}

@media (max-width: 640px) {
  .room-mark {
    height: 26px;
  }
}
</style>
