<template>
  <main
    class="size-full px-9 pt-6 pb-8 defense flex flex-col overflow-hidden relative min-w-[1500px]"
  >
    <div class="min-w-[1450px] w-full flex-shrink-0">
      <DefenseHeader class="w-full" />
    </div>
    <div class="flex-grow w-full flex items-center justify-center">
      <div
        class="flex mt-4 max-h-[800px] max-w-[1450px] h-full w-full relative"
      >
        <section
          v-show="showArr?.[0] == 1"
          class="w-[800px] h-full bg-[rgba(0,0,0,0.15)] rounded-[32px] mr-4 relative overflow-hidden flex-shrink-0 fade-in"
        >
          <InterviewVoicePlayer
            ref="voicePlayerRef"
            @ready="onVoicePlayerReady"
            @playing="updatePlaying"
            @broadcastingEnd="broadcastingEnd"
            v-if="isReady && detail?.id"
          />
        </section>
        <section
          v-show="showArr?.[1] == 2"
          class="flex-grow max-h-[528px] h-full bg-[rgba(123,123,123,0.5)] rounded-[32px] flex flex-col p-4 fade-in videoBox"
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
              class="w-[140px] h-[50px] bg-[rgba(255,255,255,0.15)] hover:bg-[rgba(255,255,255,0.3)] hover:scale-105 cursor-pointer flex items-center justify-center text-lg font-medium text-white flex-shrink-0 rounded-[693px]"
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
          class="absolute bottom-[-10px] left-0 w-full bottom-card flex flex-col fade-in"
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
              <template
                v-if="questionList?.[questionList?.length - 1]?.follow?.length"
              >
                <div class="text-[32px] font-semibold mt-3">追问</div>
                <div
                  class="mt-2 leading-7 space-y-2"
                  v-for="(item, index) in questionList?.[
                    questionList?.length - 1
                  ]?.follow"
                  :key="index"
                >
                  <div
                    class="leading-7"
                    v-html="calcRender(item?.title || '')"
                  ></div>
                </div>
              </template>
            </div>
          </div>
        </section>
      </div>
    </div>
    <!-- 加载遮罩层 -->
    <LoadingOverlay
      v-if="!isReady || !isVoiceReady || isGenQuestion || endAnswerLoading"
      :text="'正在加载中，请稍后...'"
      :bg="isFirstShow ? 'rgba(95, 90, 87, 1)' : 'rgba(95, 90, 87, 0.8)'"
    />
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
import {
  endAnswer,
  endAssessment,
  generateQuestionApi,
  startAnswer,
  startAssessmentApi,
} from "@/api/kh";
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
const audioStatus = ref(false);
const endAnswerLoading = ref(false);
let voiceFallbackTimer: number | undefined = undefined;

const isPlaying = ref(false);

function clearVoiceFallbackTimer() {
  if (voiceFallbackTimer) {
    clearTimeout(voiceFallbackTimer);
    voiceFallbackTimer = undefined;
  }
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
};

const broadcastingEnd = () => {
  clearVoiceFallbackTimer();
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  startAudio();
  audioStatus.value = true;
  startAnswerQuestions();
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
  startAnswer({
    assessmentId: route.query?.id,
  }).then(() => {
    handleQuestionTimeout();
  });
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
  clearInterval(answerTimer);
  answerTime.value = detail.value?.answerTime || 60;
  endAnswerLoading.value = true;
  endAnswer({
    assessmentId: route.query?.id,
  })
    .then(() => {
      generateQuestions();
    })
    .finally(() => {
      endAnswerLoading.value = false;
    });
}

function generateQuestions() {
  clearVoiceFallbackTimer();
  isGenQuestion.value = true;
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  generateQuestionApi({ assessmentId: route.query?.id })
    .then((res) => {
      const r: Recordable = res || {};
      if (r?.code == 200) {
        if (r.data?.last) {
          overAssessment();
        } else {
          if (r.data?.follow) {
            if (!questionList.value[questionList.value?.length - 1]?.follow) {
              questionList.value[questionList.value?.length - 1].follow = [];
            }
            questionList.value[questionList.value?.length - 1].follow?.push({
              title: r.data?.question,
            });
            try {
              if (questionContainer.value) {
                nextTick(() => {
                  questionContainer.value.scrollTop =
                    questionContainer.value.scrollHeight;
                });
              }
            } catch (error) {}
          } else {
            questionList.value?.push({ title: r.data?.question });
          }
          scheduleVoiceFallbackStart();
        }
      }
    })
    .finally(() => {
      isGenQuestion.value = false;
    });
}

function overAssessment() {
  clearVoiceFallbackTimer();
  isGenQuestion.value = false;
  if (audioStatus.value) {
    stopAudio();
    audioStatus.value = false;
  }
  endAssessment({
    assessmentId: route.query?.id,
  }).then((res) => {
    router.push({
      name: "ResultOverPage",
      query: route.query,
    });
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
    }
  );
});

onUnmounted(() => {
  clearVoiceFallbackTimer();
  stopCamera();
  // closeAudio();
  clearInterval(answerTimer);
  clearInterval(durationTimer);
});

const onVoicePlayerReady = async () => {
  isVoiceReady.value = true;
};

const isFirst = ref(false);
watch(
  () => [isReady.value, isVoiceReady.value],
  async ([ready, voiceReady]: any) => {
    if (ready && voiceReady && !isFirst.value) {
      isFirst.value = true;
      startAssessmentApi({
        assessmentId: route.query?.id,
      }).then(() => {
        generateQuestions();
      });
    }
  }
);

const isFirstShow = ref(true);
watch(
  () => [isReady.value, isVoiceReady.value, isGenQuestion.value],
  () => {
    if (!(!isReady.value || !isVoiceReady.value || isGenQuestion.value)) {
      if (isFirstShow.value) {
        isFirstShow.value = false;
        showArr.value = [1];
        setTimeout(() => {
          showArr.value?.push(2);
        }, 400);
        setTimeout(() => {
          showArr.value?.push(3);
        }, 800);
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
  background: #5f5a57;
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
</style>
