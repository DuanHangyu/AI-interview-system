<template>
  <div class="size-full grid grid-cols-2 gap-x-3 text-[rgba(0,0,0,0.88)]">
    <div
      class="bg-[#ffffff] h-full overflow-hidden flex flex-col p-5 rounded-[30px]"
    >
      <div class="flex flex-col overflow-y-auto question" v-if="isReady">
        <div>
          <div class="flex items-center space-x-3">
            <div class="w-[10px] h-[30px] bg-[#409eff]"></div>
            <span class="text-base font-bold">
              问题
              {{ questionList?.length == 0 ? 1 : questionList?.length }} /
              {{ detail?.questionCount }}
            </span>
          </div>
          <p
            class="question-text"
            v-html="
              calcRender(questionList?.[questionList?.length - 1]?.title || '')
            "
          ></p>
        </div>
        <div
          class="mt-3"
          v-if="questionList?.[questionList?.length - 1]?.follow"
        >
          <span class="text-base font-bold">追问</span>
          <p
            class="question-text"
            v-html="
              calcRender(
                questionList?.[questionList?.length - 1]?.follow?.title || ''
              )
            "
          ></p>
        </div>
      </div>
      <div class="reserved-interview-panel" v-if="isReady">
        <InterviewVoicePlayer
          ref="voicePlayerRef"
          @ready="onVoicePlayerReady"
          @playing="updatePlaying"
        />
      </div>
    </div>
    <div class="bg-[#ffffffb3] flex flex-col p-5 rounded-[30px]">
      <div class="flex items-center justify-between mb-[10px] flex-shrink-0">
        <span class="text-base font-bold w-[160px] flex-shrink-0">
          {{ `剩余时间：${answerTime} 秒` }}
        </span>
        <Progress
          class="flex-grow mr-10"
          :showInfo="false"
          strokeColor="#01C883"
          trailColor="#DFDFDF"
          :percent="(answerTime / detail?.answerTime) * 100"
          :size="10"
        />
        <Button
          type="primary"
          class="flex-shrink-0"
          danger
          @click="endQuestionEarly"
        >
          提前结束
        </Button>
      </div>

      <div class="video-container flex-grow">
        <video ref="videoRef" autoplay muted playsinline></video>
      </div>
      <div
        class="transcription-area h-[130px] flex-shrink-0 border-solid border-[1px] border-[#ddd] rounded-bl-[30px] rounded-br-[30px] bg-[rgba(75,132,255,0.1)] overflow-y-auto p-3 text-[#111] flex items-center justify-center"
      >
        <div
          class="flex items-center space-x-2 justify-center"
          v-if="isPlaying"
        >
          <Spin :spinning="true" />
          <span class="text-sm text-[#1677ff] pb-2">请听题...</span>
        </div>
        <div class="w-[80%] h-[72px]" v-else>
          <WaveComponent
            :loading="volumeLevel > 10 && audioStatus ? true : false"
          />
        </div>
      </div>
    </div>

    <!-- 加载遮罩层 -->
    <LoadingOverlay
      v-if="!isReady || !isVoiceReady || !isConnected || isGenQuestion"
      :text="'正在加载中，请稍后...'"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from "vue";
import { useSpeechRecognition } from "../composables/useSpeechRecognition.js";
import { useRouter, useRoute } from "vue-router";
import InterviewVoicePlayer from "@/components/InterviewVoicePlayer.vue";
import LoadingOverlay from "@/components/LoadingOverlay/index.vue";
import { Button, message, Progress } from "ant-design-vue";
import { getAssessmentDetail } from "@/api/assessment";
import { getToken } from "@/utils/auth";
import WaveComponent from "@/components/Wave/index.vue";
import MarkdownIt from "markdown-it";
import hljs from "markdown-it-highlightjs";
import { Spin } from "ant-design-vue";
const md = new MarkdownIt().use(hljs);
const calcRender = computed(() => (e: string) => {
  return md.render(e);
});
const {
  transcriptionText,
  initAudio,
  startAudio,
  stopAudio,
  closeAudio,
  startHeartbeat,
  restartAudio,
  volumeLevel,
} = useSpeechRecognition();

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
const audioStatus = ref(false);

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

const updatePlaying = (e: boolean) => {
  isPlaying.value = e;
  if (isPlaying.value == false) {
    if (audioStatus.value) {
      stopAudio();
      audioStatus.value = false;
    }
    startAudio();
    audioStatus.value = true;
    startAnswerQuestions();
  }
};

const connect = () => {
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
    return;
  }
  connectionStatus.value = "Connecting...";

  // 创建 WebSocket 连接
  socket.value = new WebSocket(
    process.env.VUE_APP_BASE_TEXT_WS,
    getToken() || ""
  );

  socket.value.onopen = () => {
    connectionStatus.value = "Connected";
    isConnected.value = true;
    reconnectAttempts.value = 0; // 重置重连计数器
    console.log("WebSocket connected");
    heartbeatTimer = setInterval(() => {
      if (socket.value?.readyState === WebSocket.OPEN) {
        socket.value.send(JSON.stringify({ action: "ping" }));
      }
    }, HEARTBEAT_INTERVAL);
  };

  socket.value.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    console.log(msg);
    if (msg?.normalMessage) {
      if (msg.content == "考核结束") {
        router.push({
          name: "ResultOverPage",
          query: route.query,
        });
      } else {
        if (msg?.content?.includes("title")) {
          const content = JSON.parse(msg?.content);
          if (content?.title == "结束问答") {
            overAssessment();
          } else {
            isGenQuestion.value = false;
            // if (audioStatus.value) {
            //   stopAudio();
            //   audioStatus.value = false;
            // }
            // startAudio();
            // audioStatus.value = true;
            if (content?.follow) {
              questionList.value[questionList.value?.length - 1].follow =
                content;
            } else {
              questionList.value?.push(content);
            }
            // startAnswerQuestions();
          }
        }
      }
    } else {
      if (msg.content == "考核已完成") {
        // router.push({
        //   name: "ResultOverPage",
        //   query: route.query,
        // });
      } else {
        message.error(msg?.content);
      }
    }
  };

  socket.value.onerror = (error: any) => {
    console.error("WebSocket error:", error);
    connectionStatus.value = "Error: " + error.message;
    attemptReconnect();
    isConnected.value = false;
  };

  socket.value.onclose = () => {
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
  if (isConnected.value || reconnectAttempts.value >= maxReconnectAttempts) {
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
  } catch (err) {
    console.error("摄像头启动失败", err);
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
  socket.value?.send(
    JSON.stringify({
      message: route.query?.id,
      action: "start-assessment",
    })
  );
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
  socket.value?.send(
    JSON.stringify({
      message: route.query?.id,
      action: "start-answer",
    })
  );
  handleQuestionTimeout();
}

function endQuestionEarly() {
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  try {
    (voicePlayerRef.value as any)?.stopVoice?.();
  } catch (error) {}
  clearInterval(answerTimer);
  socket.value?.send(
    JSON.stringify({
      message: route.query?.id,
      action: "end-answer",
    })
  );
  // if (
  //   questionList.value?.[questionList?.value?.length - 1]?.index ==
  //   detail.value?.questionCount
  // ) {
  //   overAssessment();
  // } else {
  //   generateQuestions();
  // }
  generateQuestions();
}

function generateQuestions() {
  // if (
  //   questionList.value?.[questionList?.value?.length - 1]?.index >=
  //   detail.value?.questionCount
  // ) {
  //   return;
  // }
  isGenQuestion.value = true;
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  socket.value?.send(
    JSON.stringify({
      message: route.query?.id,
      action: "generate_question",
    })
  );
}

function overAssessment() {
  isGenQuestion.value = false;
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  socket.value?.send(
    JSON.stringify({
      message: route.query?.id,
      action: "end-assessment",
    })
  );
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
      await initAudio();
      startHeartbeat();
      await startCamera();
      connect();
    }
  );
});

onUnmounted(() => {
  pageOff.value = true;
  stopCamera();
  // closeAudio();
  clearInterval(answerTimer);
  clearInterval(durationTimer);
  if (socket.value) {
    socket.value.close();
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
  }
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
      // FIX-F: 仅发起 start-assessment；第 1 题由后端在题目生成完成后主动推送，
      // 避免此处立即请求命中「题目正在生成中」空缓存。第 2..N 题仍由答题后的 generateQuestions() 链路获取。
      startAssessment();
    }
  }
);
</script>

<style scoped>
.reserved-interview-panel {
  margin-top: auto;
  display: flex;
  justify-content: center;
  align-items: flex-end;
  height: 100px;
  color: #999;
  font-size: 20px;
  padding: 8px 8px 0 8px;
  box-sizing: border-box;
}

.question-text {
  font-size: 16px;
  margin-top: 5px;
  padding-right: 8px; /* 避免滚动条压住文字 */
  line-height: 1.5;
}

.video-container {
  position: relative;
  /* height: 300px; */
  /* height: 40vh; */
  border: 1px solid #ddd;
  border-radius: 5px;
  background-color: #222;
  display: flex;
  justify-content: center;
  align-items: center;
}

video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 5px;
}

.horizontal-container {
  display: flex;
  align-items: center; /* 垂直居中对齐 */
  gap: 8px; /* 元素之间间距，也可以用 margin 代替 */
  margin-left: 12px;
}
.question {
  height: calc(100% - 410px);
}
</style>
