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
        <section
          v-show="showArr?.[0] == 1"
          class="question-voice-panel w-[800px] h-full bg-[rgba(0,0,0,0.15)] rounded-[32px] mr-4 relative overflow-hidden flex-shrink-0 fade-in"
        >
          <InterviewVoicePlayer
            ref="voicePlayerRef"
            :playback-enabled="voicePlaybackEnabled"
            @ready="onVoicePlayerReady"
            @playing="updatePlaying"
            @broadcastingEnd="broadcastingEnd"
            v-if="isReady && detail?.id"
          />
        </section>
        <section
          v-show="showArr?.[1] == 2"
          class="question-video-panel flex-grow max-h-[528px] h-full bg-[rgba(123,123,123,0.5)] rounded-[32px] flex flex-col p-4 fade-in videoBox"
        >
          <div
            class="flex-grow w-full rounded-[32px] overflow-hidden relative flex justify-center"
          >
            <video ref="videoRef" autoplay muted playsinline></video>
            <div class="wave" v-if="!isPlaying">
              <div class="wave-1"></div>
              <WaveComponent
                :loading="volumeLevel > 10 && audioStatus ? true : false"
              />
            </div>
          </div>
          <div
            class="defense-action mt-5 flex-shrink-0 flex space-x-10 items-center"
          >
            <div class="flex-grow">
              <Progress
                class="!mb-0"
                :showInfo="false"
                strokeColor="#fff"
                trailColor="rgba(255,255,255,0.35)"
                :percent="(answerTime / detail?.answerTime) * 100"
                :size="10"
              />
              <div class="flex justify-between items-center mt-1">
                <span class="text-base text-white font-normal">剩余时间</span>
                <span class="text-white">
                  <span class="text-2xl font-medium">{{ answerTime }}</span>
                  <span class="text-base font-normal pl-2">秒</span>
                </span>
              </div>
            </div>
            <div
              class="question-main-action w-[140px] h-[50px] bg-[rgba(255,255,255,0.15)] hover:bg-[rgba(255,255,255,0.3)] hover:scale-105 cursor-pointer flex items-center justify-center text-lg font-medium text-white flex-shrink-0 rounded-[693px]"
              @click="endQuestionEarly"
            >
              提前结束{{
                !isPlaying &&
                detail.answerTime - answerTime < 4 &&
                detail.answerTime - answerTime > 0
                  ? "(" + (4 - (detail.answerTime - answerTime)) + "秒)"
                  : ""
              }}
            </div>
          </div>
        </section>
        <section
          v-show="showArr?.[2] == 3"
          class="question-bottom-panel absolute bottom-[-10px] left-0 w-full bottom-card flex flex-col fade-in"
        >
          <div class="flex space-x-4 mb-4 items-end">
            <img
              src="@/assets/defense/qtt.png"
              class="W-[100px] h-[45px]"
              alt=""
              v-if="isPlaying"
            />
            <span class="text-2xl font-medium text-[rgba(255,255,255,0.8)]">
              问题 {{ questionList?.length == 0 ? 1 : questionList?.length }} /
              {{ detail?.questionCount }}
            </span>
          </div>
          <div
            class="bottom-card-in flex-grow py-3 px-2 text-[#FFFFFF] font-medium text-[22px] overflow-hidden"
          >
            <div
              class="w-full h-full overflow-y-auto custom-scrollbar px-1"
              ref="questionContainer"
            >
              <div
                class="leading-7"
                v-html="
                  calcRender(
                    questionList?.[questionList?.length - 1]?.title || ''
                  )
                "
              ></div>
              <template v-if="questionList?.[questionList?.length - 1]?.follow">
                <div class="text-[32px] font-semibold mt-3">追问</div>
                <div
                  class="mt-2 leading-7"
                  v-html="
                    calcRender(
                      questionList?.[questionList?.length - 1]?.follow?.title ||
                        ''
                    )
                  "
                ></div>
              </template>
            </div>
          </div>
        </section>
      </div>
    </div>
    <!-- 加载遮罩层 -->
    <LoadingOverlay
      v-if="showLoading"
      :text="loadingText"
      :bg="isFirstShow ? 'rgba(95, 90, 87, 1)' : 'rgba(95, 90, 87, 0.8)'"
    />
    <div
      v-if="blockingError"
      class="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(95,90,87,0.86)]"
    >
      <div
        class="w-[460px] max-w-[90vw] rounded-2xl bg-white/95 px-8 py-7 text-center shadow-2xl"
      >
        <div class="text-xl font-semibold text-[#1f2937] mb-3">面试连接异常</div>
        <div class="text-base leading-7 text-[#4b5563] whitespace-pre-line">
          {{ blockingError }}
        </div>
        <button
          class="mt-6 h-11 px-8 rounded-full bg-[#e75f49] text-white text-base font-medium hover:bg-[#f07157]"
          @click="retryBlockedStep"
        >
          重试
        </button>
      </div>
    </div>
  </main>
</template>
<script setup lang="ts">
import DefenseHeader from "@/components/Layout/componets/DefenseHeader.vue";
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from "vue";
import { useSpeechRecognition } from "@/composables/useSpeechRecognition.js";
import { useRouter, useRoute } from "vue-router";
import InterviewVoicePlayer from "@/components/InterviewVoicePlayer.vue";
import LoadingOverlay from "@/components/LoadingOverlay/newIndex.vue";
import { message, Progress } from "ant-design-vue";
import { getAssessmentDetail } from "@/api/assessment";
import { getToken } from "@/utils/auth";
import WaveComponent from "@/components/Wave/index.vue";
import MarkdownIt from "markdown-it";
import hljs from "markdown-it-highlightjs";
const md = new MarkdownIt().use(hljs);
const calcRender = computed(() => (e: string) => {
  return md.render(e);
});
const {
  initAudio,
  startAudio,
  stopAudio,
  closeAudio,
  volumeLevel,
  audioReady,
  audioError,
} = useSpeechRecognition({
  onConnectionLost: handleAnswerAudioConnectionLost,
});
const questionContainer = ref();

const showArr = ref<number[]>([]);
const router = useRouter();
const route = useRoute();
const detail = ref<Recordable>({});
const duration = ref(0);
const answerTime = ref(0);
let answerTimer: number | undefined = undefined;
let durationTimer: number | undefined = undefined;
const videoRef = ref<any>();
let videoStream: any = null;
const voicePlayerRef = ref(null);
const questionList = ref<Recordable[]>([]);
const isReady = ref(false);
const isVoiceReady = ref(false);
const isGenQuestion = ref(false);
const voicePlaybackEnabled = ref(false);
const audioStatus = ref(false);
const blockingError = ref("");
const blockedStep = ref<"setup" | "generate" | "answer">("setup");
const loadingText = ref("正在加载中，请稍后...");
const showLoading = computed(
  () =>
    !blockingError.value &&
    (!isReady.value || !isVoiceReady.value || !isConnected.value || isGenQuestion.value)
);
let voiceFallbackTimer: number | undefined = undefined;
let generateRetryTimer: number | undefined = undefined;

// 心跳间隔（毫秒）
const HEARTBEAT_INTERVAL = 5000; // 30秒
let heartbeatTimer: number | undefined = undefined;
const reconnectAttempts = ref(0);
const maxReconnectAttempts = 5;
const reconnectInterval = 3000; // 3秒重试一次
let reconnectTimer: number | undefined = undefined;
const socket = ref<WebSocket | null>(null);
const isConnected = ref(false);
const connectionStatus = ref("Disconnected");
const pageOff = ref(false);

const isPlaying = ref(false);

function resetLoadingText() {
  loadingText.value = "正在加载中，请稍后...";
}

function clearGenerateRetryTimer() {
  if (generateRetryTimer) {
    clearTimeout(generateRetryTimer);
    generateRetryTimer = undefined;
  }
}

function setBlockingError(messageText: string, step: "setup" | "generate" | "answer" = "generate") {
  clearVoiceFallbackTimer();
  clearGenerateRetryTimer();
  voicePlaybackEnabled.value = false;
  isGenQuestion.value = false;
  blockingError.value = messageText;
  blockedStep.value = step;
}

function clearBlockingError() {
  blockingError.value = "";
  resetLoadingText();
}

function handleAnswerAudioConnectionLost() {
  if (pageOff.value || !audioStatus.value) {
    return false;
  }
  clearInterval(answerTimer);
  answerTimer = undefined;
  audioStatus.value = false;
  isGenQuestion.value = false;
  answerTime.value = detail.value?.answerTime || answerTime.value;
  setBlockingError(
    "录音连接中断，本轮回答未保存。请检查网络和麦克风后点击重试，重新回答当前题目。",
    "answer"
  );
  message.error("录音连接中断，请重新回答当前题目");
  return false;
}

function clearTextHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = undefined;
  }
}

function isTextSocketOpen() {
  return socket.value?.readyState === WebSocket.OPEN;
}

function sendAssessmentAction(action: string) {
  if (!isTextSocketOpen()) {
    setBlockingError("面试连接已断开，请重试连接后继续。", "setup");
    connect();
    return false;
  }
  socket.value?.send(
    JSON.stringify({
      message: route.query?.id,
      action,
    })
  );
  return true;
}

function scheduleGenerateRetry() {
  if (generateRetryTimer) {
    return;
  }
  generateRetryTimer = setTimeout(() => {
    generateRetryTimer = undefined;
    if (isConnected.value && isGenQuestion.value && !blockingError.value) {
      generateQuestions();
    }
  }, 2000);
}

function parseQuestionContent(content: unknown) {
  if (typeof content !== "string" || !content.includes("title")) {
    return null;
  }
  try {
    const parsed = JSON.parse(content);
    return parsed?.title ? parsed : null;
  } catch (error) {
    console.warn("题目消息解析失败", error, content);
    return null;
  }
}

async function restartCurrentAnswer() {
  clearBlockingError();
  isGenQuestion.value = false;
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  const started = await startAudio();
  if (!started) {
    setBlockingError(audioError.value || "录音服务连接失败，请检查网络后重试。", "setup");
    return;
  }
  audioStatus.value = true;
  startAnswerQuestions();
}

async function retryBlockedStep() {
  const step = blockedStep.value;
  clearBlockingError();

  if (step === "setup") {
    const audioOk = audioReady.value || (await initAudio());
    if (!audioOk) {
      setBlockingError(audioError.value || "无法打开麦克风，请允许浏览器麦克风权限后重试。", "setup");
      return;
    }
    const cameraOk = videoStream || (await startCamera());
    if (!cameraOk) {
      return;
    }
    if (!isConnected.value) {
      connect();
    }
    return;
  }

  if (step === "answer") {
    await restartCurrentAnswer();
    return;
  }

  markQuestionPending();
  if (questionList.value.length === 0) {
    startAssessment();
  } else {
    generateQuestions();
  }
}

function clearVoiceFallbackTimer() {
  if (voiceFallbackTimer) {
    clearTimeout(voiceFallbackTimer);
    voiceFallbackTimer = undefined;
  }
}

function markQuestionPending(text?: string) {
  voicePlaybackEnabled.value = false;
  if (text) {
    loadingText.value = text;
  }
  isGenQuestion.value = true;
}

function scheduleVoiceFallbackStart() {
  clearVoiceFallbackTimer();
  voiceFallbackTimer = setTimeout(() => {
    voiceFallbackTimer = undefined;
    if (!audioStatus.value && !isPlaying.value && !isGenQuestion.value) {
      console.warn("题目播报未开始，自动进入答题状态");
      broadcastingEnd();
    }
  }, 10000);
}

const updatePlaying = (e: boolean) => {
  isPlaying.value = e;
  if (e) {
    clearVoiceFallbackTimer();
  }
  // if (isPlaying.value == false) {
  //   if (audioStatus.value) {
  //     stopAudio();
  //     audioStatus.value = false;
  //   }
  //   startAudio();
  //   audioStatus.value = true;
  //   startAnswerQuestions();
  // }
};

const broadcastingEnd = async () => {
  clearVoiceFallbackTimer();
  voicePlaybackEnabled.value = false;
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  const started = await startAudio();
  if (!started) {
    setBlockingError(audioError.value || "录音服务连接失败，请检查网络后重试。", "setup");
    return;
  }
  audioStatus.value = true;
  startAnswerQuestions();
};

const connect = () => {
  clearTextHeartbeat();
  if (socket.value) {
    socket.value?.close();
  }
  // 清除之前的重连定时器
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = undefined;
  }
  if (reconnectAttempts.value >= maxReconnectAttempts && !isConnected.value) {
    connectionStatus.value = `Failed after ${maxReconnectAttempts} attempts`;
    setBlockingError(
      "面试连接已断开，请检查网络后点击重试继续。",
      audioStatus.value ? "answer" : "setup"
    );
    return;
  }
  connectionStatus.value = "Connecting...";

  // 创建 WebSocket 连接
  const currentSocket = new WebSocket(
    process.env.VUE_APP_BASE_TEXT_WS,
    getToken() || ""
  );
  socket.value = currentSocket;

  currentSocket.onopen = () => {
    if (socket.value !== currentSocket) {
      return;
    }
    connectionStatus.value = "Connected";
    isConnected.value = true;
    reconnectAttempts.value = 0; // 重置重连计数器
    clearTextHeartbeat();
    console.log("WebSocket connected");
    heartbeatTimer = setInterval(() => {
      if (socket.value === currentSocket && currentSocket.readyState === WebSocket.OPEN) {
        currentSocket.send(JSON.stringify({ action: "ping" }));
      }
    }, HEARTBEAT_INTERVAL);
  };

  currentSocket.onmessage = (event) => {
    let msg;
    try {
      msg = JSON.parse(event.data);
    } catch (error) {
      console.warn("WebSocket 消息解析失败", error, event.data);
      return;
    }
    console.log(msg);
    const contentText = String(msg?.content || "");
    if (msg?.normalMessage) {
      if (contentText == "考核结束") {
        router.push({
          name: "ResultOverPage",
          query: route.query,
        });
      } else if (contentText.includes("题目生成失败")) {
        setBlockingError(contentText, "generate");
        message.error(contentText);
      } else if (contentText.includes("题目正在生成中")) {
        markQuestionPending(contentText);
        scheduleGenerateRetry();
      } else if (contentText.includes("正在生成")) {
        markQuestionPending(contentText);
      } else if (contentText.includes("题目生成完成")) {
        clearGenerateRetryTimer();
        generateQuestions();
      } else {
        const content = parseQuestionContent(contentText);
        if (content) {
          if (content?.title == "结束问答") {
            overAssessment();
          } else {
            clearBlockingError();
            clearGenerateRetryTimer();
            // if (audioStatus.value) {
            //   stopAudio();
            //   audioStatus.value = false;
            // }
            // startAudio();
            // audioStatus.value = true;
            if (content?.follow) {
              questionList.value[questionList.value?.length - 1].follow =
                content;
              try {
                if (questionContainer.value) {
                  nextTick(() => {
                    questionContainer.value.scrollTop =
                      questionContainer.value.scrollHeight;
                  });
                }
              } catch (error) {}
            } else {
              questionList.value?.push(content);
            }
            nextTick(() => {
              isGenQuestion.value = false;
              voicePlaybackEnabled.value = true;
              scheduleVoiceFallbackStart();
            });
            // startAnswerQuestions();
          }
        }
      }
    } else {
      isGenQuestion.value = false;
      if (contentText == "考核已完成") {
        // router.push({
        //   name: "ResultOverPage",
        //   query: route.query,
        // });
      } else if (contentText.includes("重新回答") || contentText.includes("录音数据")) {
        message.error(contentText);
        blockedStep.value = "answer";
        restartCurrentAnswer();
      } else {
        message.error(contentText);
      }
    }
  };

  currentSocket.onerror = (error: any) => {
    if (socket.value !== currentSocket) {
      return;
    }
    console.error("WebSocket error:", error);
    connectionStatus.value = "Error: " + error.message;
    attemptReconnect();
    isConnected.value = false;
  };

  currentSocket.onclose = () => {
    if (socket.value !== currentSocket) {
      return;
    }
    clearTextHeartbeat();
    connectionStatus.value = "Disconnected";
    isConnected.value = false;
    if (!pageOff.value) {
      attemptReconnect();
    }
    console.log("WebSocket disconnected");
  };
};
// 尝试重新连接
const attemptReconnect = () => {
  if (isConnected.value || reconnectTimer) {
    return;
  }
  if (reconnectAttempts.value >= maxReconnectAttempts) {
    setBlockingError(
      "面试连接已断开，请检查网络后点击重试继续。",
      audioStatus.value ? "answer" : "setup"
    );
    return;
  }
  reconnectAttempts.value += 1;
  connectionStatus.value = `Attempting to reconnect (${reconnectAttempts.value}/${maxReconnectAttempts})...`;

  reconnectTimer = setTimeout(() => {
    connect();
  }, reconnectInterval);
};

async function startCamera() {
  try {
    videoStream = await navigator.mediaDevices.getUserMedia({ video: true });
    if (videoRef.value) {
      videoRef.value.srcObject = videoStream;
      videoRef.value.onloadedmetadata = () => {
        videoRef.value.play();
      };
    }
    return true;
  } catch (err) {
    console.error("摄像头启动失败", err);
    setBlockingError("无法打开摄像头，请允许浏览器摄像头权限后重试。", "setup");
    return false;
  }
}
function stopCamera() {
  if (videoStream) {
    videoStream
      .getTracks()
      .forEach((track: { stop: () => any }) => track.stop());
    videoStream = null;
  }
}

function startAssessment() {
  return sendAssessmentAction("start-assessment");
}

function handleQuestionTimeout() {
  answerTime.value = detail.value?.answerTime || 60;
  clearInterval(answerTimer);
  answerTime.value--;
  answerTimer = setInterval(() => {
    answerTime.value--;
    if (answerTime.value <= 0) {
      clearInterval(answerTimer);
      endQuestionEarly();
    }
  }, 1000);
}

function startAnswerQuestions() {
  if (!sendAssessmentAction("start-answer")) {
    return;
  }
  handleQuestionTimeout();
}

function endQuestionEarly() {
  clearVoiceFallbackTimer();
  if (detail.value?.answerTime - answerTime.value < 4) {
    return;
  }
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  try {
    (voicePlayerRef.value as any)?.stopVoice?.();
  } catch (error) {}
  voicePlaybackEnabled.value = false;
  clearInterval(answerTimer);
  answerTime.value = detail.value?.answerTime || 60;
  markQuestionPending();
  if (!sendAssessmentAction("end-answer")) {
    isGenQuestion.value = false;
  }
  // if (
  //   questionList.value?.[questionList?.value?.length - 1]?.index ==
  //   detail.value?.questionCount
  // ) {
  //   overAssessment();
  // } else {
  //   generateQuestions();
  // }
}

function generateQuestions() {
  clearVoiceFallbackTimer();
  // if (
  //   questionList.value?.[questionList?.value?.length - 1]?.index >=
  //   detail.value?.questionCount
  // ) {
  //   return;
  // }
  markQuestionPending();
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  if (!sendAssessmentAction("generate_question")) {
    isGenQuestion.value = false;
  }
}

function overAssessment() {
  clearVoiceFallbackTimer();
  isGenQuestion.value = false;
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  sendAssessmentAction("end-assessment");
  router.push({
    name: "ResultOverPage",
    query: route.query,
  });
}

onMounted(() => {
  getAssessmentDetail(route.query?.id as unknown as number).then(
    async (res) => {
      isReady.value = true;
      detail.value = res?.data || {};
      duration.value = res?.data?.duration || 0;
      answerTime.value = res?.data?.answerTime || 0;
      const audioOk = await initAudio();
      if (!audioOk) {
        setBlockingError(audioError.value || "无法打开麦克风，请允许浏览器麦克风权限后重试。", "setup");
        return;
      }
      const cameraOk = await startCamera();
      if (!cameraOk) {
        return;
      }
      connect();
    }
  );
});

onUnmounted(() => {
  pageOff.value = true;
  clearVoiceFallbackTimer();
  clearGenerateRetryTimer();
  stopCamera();
  closeAudio();
  clearInterval(answerTimer);
  clearInterval(durationTimer);
  if (socket.value) {
    socket.value.close();
  }
  clearTextHeartbeat();
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
  }
});

const onVoicePlayerReady = async () => {
  isVoiceReady.value = true;
};

const isFirst = ref(false);
watch(
  () => [isReady.value, isVoiceReady.value, isConnected.value],
  async ([ready, voiceReady, status]: any) => {
    if (ready && voiceReady && status && !isFirst.value) {
      isFirst.value = true;
      markQuestionPending();
      if (!startAssessment()) {
        isFirst.value = false;
      }
    }
  }
);

const isFirstShow = ref(true);
watch(
  () => [
    isReady.value,
    isVoiceReady.value,
    isConnected.value,
    isGenQuestion.value,
  ],
  () => {
    if (
      !(
        !isReady.value ||
        !isVoiceReady.value ||
        !isConnected.value ||
        isGenQuestion.value
      )
    ) {
      if (isFirstShow.value) {
        isFirstShow.value = false;
        showArr.value = [1, 2, 3];
      }
    }
  },
  {
    deep: true,
    immediate: true,
  }
);
</script>
<style scoped>
.defense {
  background:
    radial-gradient(circle at 82% 18%, rgba(244, 207, 85, 0.18), transparent 30%),
    linear-gradient(180deg, #706f6b 0%, #5f5a57 100%);
}
.bottom-card {
  height: 265px;
  background: linear-gradient(
    180deg,
    rgba(167, 167, 167, 0.4) 0%,
    rgba(26, 26, 26, 0.8) 100%
  );
  box-shadow: 0px 20px 80px 0px rgba(0, 0, 0, 0.5),
    inset 2px 2px 0px 0px rgba(255, 255, 255, 0.6),
    inset -2px -2px 0px 0px rgba(0, 0, 0, 0.2);
  border-radius: 50px;
  padding: 24px;
  backdrop-filter: blur(40px);
}
.bottom-card-in {
  width: 100%;
  background: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0.3) 0%,
    rgba(83, 83, 83, 0.2) 100%
  );
  box-shadow: inset 0px 0px 4px 0px rgba(0, 0, 0, 0.4);
  border-radius: 32px 32px 32px 32px;
  border: 2px solid rgba(255, 255, 255, 0.3);
}
.defense-action {
  width: 100%;
  height: 95px;
  background: linear-gradient(
    180deg,
    rgba(211, 211, 211, 0.3) 0%,
    rgba(30, 30, 30, 0.2) 100%
  );
  box-shadow: 0px 20px 50px 0px rgba(0, 0, 0, 0.7);
  border-radius: 459px 459px 459px 459px;
  padding: 16px;
}

.wave {
  width: 100%;
  height: 128px;
  /* background: linear-gradient(
    0deg,
    rgba(0, 0, 0, 0.6) 0%,
    rgba(0, 0, 0, 0.4) 25%,
    rgba(0, 0, 0, 0.2) 50%,
    transparent 75%,
    transparent 100%
  ); */
  background: linear-gradient(
    0deg,
    rgba(0, 0, 0, 0.8) 0%,
    rgba(0, 0, 0, 0.6) 25%,
    rgba(0, 0, 0, 0.4) 50%,
    rgba(0, 0, 0, 0.2) 75%,
    transparent 100%
  );
  border-bottom-right-radius: 32px;
  border-bottom-left-radius: 32px;
  /* backdrop-filter: blur(10px); */
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
  background: linear-gradient(
    0deg,
    rgba(0, 0, 0, 0.8) 0%,
    rgba(0, 0, 0, 0.6) 25%,
    rgba(0, 0, 0, 0.4) 50%,
    rgba(0, 0, 0, 0.2) 75%,
    transparent 100%
  );
  /* backdrop-filter: blur(0.15rem); */
  z-index: 10;
}

/* Webkit浏览器滚动条 */
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

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #a0a0a0;
}

/* Firefox浏览器支持 */
@supports not selector(::-webkit-scrollbar) {
  .custom-scrollbar {
    scrollbar-width: thin;
    scrollbar-color: #c0c0c0 rgba(255, 255, 255, 0.3);
  }
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
  0% {
    opacity: 0.1;
  }
  25% {
    opacity: 0.25;
  }
  50% {
    opacity: 0.5;
  }
  75% {
    opacity: 0.75;
  }
  100% {
    opacity: 1;
  }
  /* to {
    opacity: 1;
  } */
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

  .question-voice-panel,
  .question-video-panel {
    width: 100% !important;
    min-height: 380px;
    max-height: none;
    margin-right: 0 !important;
  }

  .question-video-panel {
    height: auto;
  }

  .question-bottom-panel {
    position: relative !important;
    bottom: auto !important;
    left: auto !important;
    min-height: 240px;
    margin-top: 0;
  }

  .bottom-card {
    height: auto;
    min-height: 240px;
  }
}

@media (max-width: 640px) {
  .defense {
    padding: 12px;
  }

  .question-voice-panel,
  .question-video-panel {
    min-height: 330px;
    border-radius: 24px;
  }

  .question-video-panel {
    padding: 12px;
  }

  .defense-action {
    height: auto;
    min-height: 112px;
    flex-direction: column;
    gap: 14px;
    padding: 14px;
    border-radius: 28px;
  }

  .question-main-action {
    width: 100% !important;
    max-width: 260px;
  }

  .bottom-card {
    padding: 14px;
    border-radius: 28px;
  }

  .bottom-card-in {
    border-radius: 22px;
    font-size: 18px;
  }
}
</style>
