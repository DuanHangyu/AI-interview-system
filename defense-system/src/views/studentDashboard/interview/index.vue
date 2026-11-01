<template>
  <main
    class="size-full px-4 xl:px-9 pt-6 pb-8 defense flex flex-col overflow-auto relative"
  >
    <div class="defense-header-bar w-full flex-shrink-0">
      <DefenseHeader class="w-full" />
    </div>
    <div class="flex-grow w-full flex items-center justify-center">
      <div
        class="defense-stage flex mt-4 max-h-[800px] max-w-[1450px] h-full w-full relative"
      >
        <!-- 左侧：答辩材料 / 题目进度 -->
        <section
          class="side-panel w-[800px] h-full bg-[rgba(0,0,0,0.15)] rounded-[32px] mr-4 p-4 relative fade-in flex-shrink-0"
        >
          <template v-if="stage === 'defense'">
            <div
              class="h-full w-full flex flex-col items-center justify-center pb-[250px]"
              v-if="!filePreviewUrl"
            >
              <img
                src="@/assets/defense/empty.png"
                class="w-[200px] h-[147px] scale-150 translate-y-[20px]"
                alt=""
              />
              <Upload
                name="file"
                accept=".pdf"
                :showUploadList="false"
                :headers="uploadHeaders"
                :action="uploadAction"
                @change="handleFileChange"
                :beforeUpload="beforeFileUpload"
                class="z-10"
              >
                <div
                  class="w-[140px] h-[50px] bg-[rgba(255,255,255,0.15)] hover:bg-[rgba(255,255,255,0.3)] rounded-[693px] text-lg font-medium text-white flex items-center justify-center hover:scale-105 cursor-pointer z-10"
                >
                  上传文件
                </div>
              </Upload>
              <div class="mt-3 text-sm text-[rgba(255,255,255,0.7)]">
                可选：上传答辩 PDF 材料
              </div>
            </div>
            <div
              class="bg-white rounded-[32px] w-full h-full overflow-hidden"
              v-else
            >
              <iframe :src="filePreviewUrl" class="size-full" frameborder="0"></iframe>
            </div>
            <Upload
              name="file"
              accept=".pdf"
              :showUploadList="false"
              :headers="uploadHeaders"
              :action="uploadAction"
              @change="handleFileChange"
              :beforeUpload="beforeFileUpload"
              v-if="filePreviewUrl"
            >
              <div
                class="reUpload hover:scale-105 absolute bottom-[190px] right-[40px] flex items-center justify-center"
              >
                重新上传
              </div>
            </Upload>
          </template>
          <div
            v-else
            class="h-full w-full flex flex-col pb-[250px] text-white overflow-hidden"
          >
            <div class="text-2xl font-semibold px-4 pt-4">面试进度</div>
            <div class="mt-2 px-4 text-base text-[rgba(255,255,255,0.7)]">
              {{ progressHint }}
            </div>
            <div class="mt-6 px-4 grid grid-cols-5 gap-3 overflow-y-auto custom-scrollbar">
              <div
                v-for="n in state?.total || 0"
                :key="n"
                class="slot"
                :class="{
                  'slot--done': n <= (state?.answeredCount || 0),
                  'slot--current': state?.current?.index === n,
                }"
              >
                {{ n }}
              </div>
            </div>
            <div class="mt-auto px-4 flex items-center space-x-3 text-lg">
              <span class="status-dot" :class="`status-dot--${questionPhase}`"></span>
              <span>{{ phaseText }}</span>
            </div>
          </div>
        </section>

        <!-- 右侧：摄像头 + 操作栏 -->
        <section
          class="video-panel flex-grow max-h-[600px] bg-[rgba(123,123,123,0.5)] rounded-[32px] flex flex-col p-4 fade-in videoBox"
        >
          <div class="flex-grow w-full rounded-[32px] overflow-hidden relative flex justify-center">
            <video
              v-show="cameraReady"
              ref="videoRef"
              autoplay
              muted
              playsinline
            ></video>
            <div
              v-if="!cameraReady"
              class="size-full flex items-center justify-center text-[rgba(255,255,255,0.6)] text-base"
            >
              摄像头未开启（不影响面试）
            </div>
            <div class="wave" v-if="recorder.recording.value">
              <div class="wave-1"></div>
              <WaveComponent
                class="relative z-20"
                :loading="recorder.volumeLevel.value > 10"
              />
            </div>
          </div>
          <div class="defense-action mt-5 flex-shrink-0 flex space-x-10 items-center">
            <div class="flex-grow">
              <Progress
                class="!mb-0"
                :showInfo="false"
                strokeColor="#fff"
                trailColor="rgba(255,255,255,0.35)"
                :percent="countdownTotal ? (countdown / countdownTotal) * 100 : 0"
                :size="10"
              />
              <div class="flex justify-between items-center mt-1">
                <span class="text-base text-white font-normal">剩余时间</span>
                <span class="text-white">
                  <span class="text-2xl font-medium">{{ countdown }}</span>
                  <span class="text-base font-normal pl-2">秒</span>
                </span>
              </div>
            </div>
            <div
              class="main-action w-[140px] h-[50px] bg-[rgba(255,255,255,0.15)] hover:bg-[rgba(255,255,255,0.3)] hover:scale-105 cursor-pointer flex items-center justify-center text-lg font-medium text-white flex-shrink-0 rounded-[693px]"
              :class="{ 'opacity-50 !cursor-not-allowed': actionDisabled }"
              @click="onMainAction"
            >
              <LoadingOutlined class="mr-1" v-if="busy" />
              {{ actionText }}
            </div>
          </div>
        </section>

        <!-- 底部：主题 / 题目 -->
        <section
          class="bottom-panel absolute bottom-[-10px] left-0 w-full bottom-card flex flex-col fade-in"
        >
          <div class="flex space-x-4 mb-4 items-end" v-if="stage === 'question' && state?.current">
            <span class="text-2xl font-medium text-[rgba(255,255,255,0.8)]">
              {{ state.current.followUp ? "追问" : "问题" }}
              {{ state.current.index }} / {{ state.total }}
            </span>
          </div>
          <div class="bottom-card-in flex-grow py-3 px-2 text-[#FFFFFF] font-medium text-[22px] overflow-hidden">
            <div class="w-full h-full overflow-y-auto custom-scrollbar px-3">
              <div
                v-if="stage === 'question' && state?.current"
                class="leading-8"
                v-html="renderMarkdown(state.current.content)"
              ></div>
              <div v-else class="font-semibold text-2xl leading-9">
                {{ state?.theme }}
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <!-- 开始前说明 -->
    <div
      v-if="stage === 'intro'"
      class="absolute inset-0 z-40 flex items-center justify-center bg-[rgba(95,90,87,0.9)]"
    >
      <div class="w-[520px] max-w-[90vw] rounded-2xl bg-white/95 px-8 py-7 shadow-2xl">
        <div class="text-xl font-semibold text-[#1f2937] mb-3">{{ state?.theme }}</div>
        <ul class="text-base leading-8 text-[#4b5563] list-disc pl-5">
          <li v-if="state?.defenseEnabled">
            先进行项目答辩，最长 {{ state?.duration }} 秒
          </li>
          <li v-if="state?.questionEnabled">
            系统将从题库中为你随机抽题，每题作答最长 {{ state?.answerTime }} 秒
          </li>
          <li v-if="state?.questionEnabled && state?.followUpEnabled">
            部分题目回答后会有追问
          </li>
          <li>中途刷新或断网不会丢失已提交的回答，重新进入即可继续</li>
        </ul>
        <div class="mt-6 text-center">
          <button
            class="h-11 px-10 rounded-full bg-[#e75f49] text-white text-base font-medium hover:bg-[#f07157] disabled:opacity-60"
            :disabled="busy"
            @click="beginInterview"
          >
            <LoadingOutlined class="mr-1" v-if="busy" />
            开始面试
          </button>
        </div>
      </div>
    </div>

    <!-- 抽题动画 -->
    <div
      v-if="stage === 'drawing'"
      class="absolute inset-0 z-40 flex flex-col items-center justify-center bg-[rgba(95,90,87,0.92)] text-white"
    >
      <div class="draw-cards">
        <div class="draw-card" v-for="n in 3" :key="n" :style="{ animationDelay: `${n * 0.15}s` }">?</div>
      </div>
      <div class="mt-8 text-2xl font-semibold">正在为你随机抽题…</div>
      <div class="mt-2 text-lg text-[rgba(255,255,255,0.75)]">
        共抽取 {{ state?.total }} 道题
      </div>
    </div>

    <LoadingOverlay v-if="stage === 'loading'" :text="loadingText" />

    <!-- 阻塞性错误 / 上传失败 -->
    <div
      v-if="blocking"
      class="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(95,90,87,0.86)]"
    >
      <div class="w-[460px] max-w-[90vw] rounded-2xl bg-white/95 px-8 py-7 text-center shadow-2xl">
        <div class="text-xl font-semibold text-[#1f2937] mb-3">{{ blocking.title }}</div>
        <div class="text-base leading-7 text-[#4b5563] whitespace-pre-line">
          {{ blocking.message }}
        </div>
        <div class="mt-6 flex justify-center space-x-3">
          <button
            v-for="action in blocking.actions"
            :key="action.label"
            class="h-11 px-8 rounded-full text-base font-medium disabled:opacity-60"
            :class="action.primary ? 'bg-[#e75f49] text-white hover:bg-[#f07157]' : 'bg-[#f3f4f6] text-[#374151] hover:bg-[#e5e7eb]'"
            :disabled="busy"
            @click="action.run"
          >
            <LoadingOutlined class="mr-1" v-if="busy && action.primary" />
            {{ action.label }}
          </button>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import DefenseHeader from "@/components/Layout/componets/DefenseHeader.vue";
import WaveComponent from "@/components/Wave/index.vue";
import LoadingOverlay from "@/components/LoadingOverlay/newIndex.vue";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Modal, Progress, Upload, UploadChangeParam, message } from "ant-design-vue";
import { FileType } from "ant-design-vue/es/upload/interface.js";
import { LoadingOutlined } from "@ant-design/icons-vue";
import MarkdownIt from "markdown-it";
import hljs from "markdown-it-highlightjs";
import { getToken } from "@/utils/auth";
import { uploadAssessmentFile } from "@/api/studentAssessment";
import {
  InterviewState,
  getInterviewState,
  getQuestionAudio,
  startInterview,
  submitInterview,
  uploadAnswerAudio,
  uploadDefenseAudio,
} from "@/api/interview";
import { useAudioRecorder } from "@/composables/useAudioRecorder";

type Stage = "loading" | "intro" | "defense" | "drawing" | "question" | "submitting";
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
const videoRef = ref<HTMLVideoElement | null>(null);
const cameraReady = ref(false);
const uploadFile = ref<Recordable>({});

let countdownTimer: number | undefined;
let clockTimer: number | undefined;
let videoStream: MediaStream | null = null;
let currentAudio: HTMLAudioElement | null = null;
let currentAudioUrl = "";
let askedSlotKey = "";
let drawingShown = false;
let pageOff = false;
let finishing = false;

const uploadAction = process.env.VUE_APP_BASE_API + "/file/upload";
const uploadHeaders = ref({ Authorization: `Bearer ${getToken()}` });
const filePreviewUrl = computed(() => {
  const file = uploadFile.value;
  if (!file?.fileUrl) return file?.presignedUrl || "";
  const params = new URLSearchParams({ fileUrl: file.fileUrl });
  const token = getToken();
  if (token) params.set("Authorization", `Bearer ${token}`);
  return `${process.env.VUE_APP_BASE_API}/file/preview?${params.toString()}`;
});

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

function showBlocking(title: string, messageText: string, actions: BlockingAction[]) {
  blocking.value = { title, message: messageText, actions };
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
  router.replace({ name: "ResultOverPage", query: { id: assessmentId.value } });
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

async function fetchQuestionAudio(index: number, followUp: boolean): Promise<Blob> {
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error("audio timeout")), AUDIO_FETCH_TIMEOUT)
  );
  const res: any = await Promise.race([
    getQuestionAudio(assessmentId.value, index, followUp),
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
  const slotKey = `${current.index}-${current.followUp}`;
  if (askedSlotKey === slotKey && questionPhase.value !== "idle") return;
  askedSlotKey = slotKey;
  questionPhase.value = "playing";
  countdownTotal.value = state.value?.answerTime || 0;
  countdown.value = countdownTotal.value;
  try {
    const blob = await fetchQuestionAudio(current.index, current.followUp);
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
  await sendAnswer(current.index, current.followUp, audio);
}

async function sendAnswer(index: number, followUp: boolean, audio: Blob) {
  busy.value = true;
  try {
    const res = await uploadWithRetry(() =>
      uploadAnswerAudio(assessmentId.value, index, followUp, audio)
    );
    blocking.value = null;
    questionPhase.value = "idle";
    applyState(res.data);
  } catch (error) {
    const retry = { label: "重试上传", primary: true, run: () => sendAnswer(index, followUp, audio) };
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

async function startCamera() {
  try {
    if (!navigator.mediaDevices?.getUserMedia) return;
    videoStream = await Promise.race([
      navigator.mediaDevices.getUserMedia({ video: true }),
      new Promise<never>((_, reject) => setTimeout(() => reject(new Error("timeout")), 10000)),
    ]);
    if (pageOff) {
      stopCamera();
      return;
    }
    if (videoRef.value) {
      videoRef.value.srcObject = videoStream;
    }
    cameraReady.value = true;
  } catch (error) {
    // 摄像头只是预览，不可用时不阻断面试
    cameraReady.value = false;
  }
}

function stopCamera() {
  videoStream?.getTracks().forEach((track) => track.stop());
  videoStream = null;
  cameraReady.value = false;
}

async function prepareDevices() {
  loadingText.value = "正在检测麦克风...";
  const micOk = await recorder.init();
  if (!cameraReady.value) startCamera();
  return micOk;
}

// ---------------- 答辩材料上传（可选） ----------------
const beforeFileUpload = (file: FileType) => {
  const isPdf = file?.type === "application/pdf" || file?.name?.toLowerCase().endsWith(".pdf");
  if (!isPdf) {
    message.error("只允许上传 PDF 文件");
    return false;
  }
  return true;
};

const handleFileChange = (info: UploadChangeParam) => {
  if (info.file.status !== "done") {
    if (info.file.status === "error") message.error("文件上传失败");
    return;
  }
  if (info.file?.response?.code != 200) {
    message.error(info.file?.response?.message || "文件上传失败");
    return;
  }
  const data = info.file.response.data || {};
  uploadFile.value = {
    fileSize: info.file?.size,
    fileUrl: data.url,
    fileName: data.fileName,
    presignedUrl: data.presignedUrl,
  };
  uploadAssessmentFile({
    assessmentId: assessmentId.value,
    file: { fileSize: info.file?.size, fileUrl: data.url, fileName: data.fileName },
  }).catch(() => undefined);
};

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
      { label: "返回", run: () => router.replace("/studentDashboard") },
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
  stopCamera();
  recorder.dispose();
  window.removeEventListener("beforeunload", handleBeforeUnload);
});
</script>

<style scoped>
.defense {
  background:
    radial-gradient(circle at 82% 18%, rgba(244, 207, 85, 0.18), transparent 30%),
    linear-gradient(180deg, #706f6b 0%, #5f5a57 100%);
}
.bottom-card {
  height: 265px;
  background: linear-gradient(180deg, rgba(167, 167, 167, 0.4) 0%, rgba(26, 26, 26, 0.8) 100%);
  box-shadow: 0px 20px 80px 0px rgba(0, 0, 0, 0.5),
    inset 2px 2px 0px 0px rgba(255, 255, 255, 0.6),
    inset -2px -2px 0px 0px rgba(0, 0, 0, 0.2);
  border-radius: 50px;
  padding: 24px;
  backdrop-filter: blur(40px);
}
.bottom-card-in {
  width: 100%;
  height: 100%;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.3) 0%, rgba(83, 83, 83, 0.2) 100%);
  box-shadow: inset 0px 0px 4px 0px rgba(0, 0, 0, 0.4);
  border-radius: 32px;
  border: 2px solid rgba(255, 255, 255, 0.3);
}
.defense-action {
  width: 100%;
  height: 95px;
  background: linear-gradient(180deg, rgba(211, 211, 211, 0.3) 0%, rgba(30, 30, 30, 0.2) 100%);
  box-shadow: 0px 20px 50px 0px rgba(0, 0, 0, 0.7);
  border-radius: 459px;
  padding: 16px;
}
.reUpload {
  width: 140px;
  height: 50px;
  background: linear-gradient(270deg, rgba(255, 255, 255, 0.4) 0%, rgba(255, 255, 255, 0.8) 99%);
  box-shadow: 0px 10px 20px 0px rgba(18, 21, 73, 0.08);
  border-radius: 693px;
  border: 1px solid #ffffff;
  cursor: pointer;
  font-weight: 500;
  font-size: 18px;
  color: #4866ff;
}
.slot {
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid rgba(255, 255, 255, 0.15);
  transition: all 0.3s ease;
}
.slot--done {
  background: rgba(82, 196, 26, 0.35);
  border-color: rgba(149, 222, 100, 0.7);
  color: #fff;
}
.slot--current {
  background: rgba(255, 255, 255, 0.3);
  border-color: #fff;
  color: #fff;
  transform: scale(1.05);
}
.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
}
.status-dot--playing {
  background: #4096ff;
  animation: pulse 1.2s infinite;
}
.status-dot--recording {
  background: #ff4d4f;
  animation: pulse 1.2s infinite;
}
.status-dot--uploading {
  background: #faad14;
  animation: pulse 1.2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
.draw-cards {
  display: flex;
  gap: 18px;
}
.draw-card {
  width: 90px;
  height: 126px;
  border-radius: 16px;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.55));
  color: #e75f49;
  font-size: 44px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35);
  animation: shuffle 0.9s ease-in-out infinite alternate;
}
@keyframes shuffle {
  0% { transform: translateY(0) rotate(-6deg); }
  100% { transform: translateY(-18px) rotate(6deg); }
}
.wave {
  width: 100%;
  height: 128px;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.8) 0%, rgba(0, 0, 0, 0.4) 50%, transparent 100%);
  border-bottom-right-radius: 32px;
  border-bottom-left-radius: 32px;
  position: absolute;
  bottom: 0;
  left: 0;
  z-index: 10;
}
.wave-1 {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 80%;
  width: 100%;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.8) 0%, rgba(0, 0, 0, 0.4) 50%, transparent 100%);
  z-index: 10;
}
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #c0c0c0;
  border-radius: 3px;
}
video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 5px;
}
.fade-in {
  opacity: 0;
  animation: fadeIn 0.8s ease-in forwards;
}
@keyframes fadeIn {
  from { opacity: 0.1; }
  to { opacity: 1; }
}
.videoBox {
  height: calc(100% - 270px);
}
.defense-header-bar {
  max-width: 1450px;
  margin: 0 auto;
}
@media (max-width: 1100px) {
  .defense {
    height: auto;
    min-height: 100dvh;
    padding: 18px 16px 24px;
  }
  .defense-stage {
    height: auto;
    max-height: none;
    flex-direction: column;
    gap: 16px;
  }
  .side-panel,
  .video-panel {
    width: 100% !important;
    min-height: 420px;
    max-height: none;
    margin-right: 0 !important;
  }
  .side-panel > div {
    padding-bottom: 16px !important;
  }
  .bottom-panel {
    position: relative !important;
    bottom: auto !important;
    min-height: 220px;
  }
  .bottom-card {
    height: auto;
    min-height: 220px;
  }
}
@media (max-width: 640px) {
  .defense {
    padding: 12px;
  }
  .side-panel,
  .video-panel {
    min-height: 340px;
    border-radius: 24px;
  }
  .defense-action {
    height: auto;
    min-height: 112px;
    flex-direction: column;
    gap: 14px;
    padding: 14px;
    border-radius: 28px;
  }
  .main-action,
  .reUpload {
    width: 100% !important;
    max-width: 260px;
  }
  .bottom-card {
    padding: 14px;
    border-radius: 28px;
  }
}
</style>
