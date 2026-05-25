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
          v-if="showArr?.[0] == 1"
          class="w-[800px] h-full bg-[rgba(0,0,0,0.15)] rounded-[32px] mr-4 p-4 relative fade-in flex-shrink-0"
        >
          <div
            class="h-full w-full flex flex-col items-center justify-center pb-[250px]"
            v-if="!uploadFile?.presignedUrl"
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
              :headers="headers"
              :action="action"
              @change="handleChange"
              :beforeUpload="beforeUpload"
              class="z-10"
            >
              <div
                class="w-[140px] h-[50px] bg-[rgba(255,255,255,0.15)] hover:bg-[rgba(255,255,255,0.3)] rounded-[693px] text-lg font-medium text-white flex items-center justify-center hover:scale-105 cursor-pointer z-10"
              >
                上传文件
              </div>
            </Upload>
          </div>
          <div
            class="bg-white rounded-[32px] w-full h-full overflow-hidden"
            v-else
          >
            <iframe
              :src="uploadFile?.presignedUrl"
              class="size-full"
              frameborder="0"
            ></iframe>
          </div>
          <Upload
            name="file"
            accept=".pdf"
            :showUploadList="false"
            :headers="headers"
            :action="action"
            @change="handleChange"
            class="mt-3 flex-shrink-0"
            :beforeUpload="beforeUpload"
            v-if="uploadFile?.presignedUrl"
          >
            <div
              class="reUpload hover:scale-105 hover:bg-[rgba(255,255,255,0.3)] absolute bottom-[190px] right-[40px] flex items-center justify-center"
            >
              重新上传
            </div>
          </Upload>
        </section>
        <section
          v-show="showArr?.[1] == 2"
          class="flex-grow max-h-[600px] bg-[rgba(123,123,123,0.5)] rounded-[32px] flex flex-col p-4 fade-in videoBox"
        >
          <div class="flex-grow w-full rounded-[32px] overflow-hidden relative">
            <video
              v-show="isDebating"
              ref="videoRef"
              autoplay
              muted
              playsinline
              :style="{ opacity: isDebating ? 1 : 0.5 }"
              class="size-full"
            ></video>
            <div class="wave" v-if="isDebating">
              <div class="wave-1"></div>
              <WaveComponent
                class="relative z-20"
                :loading="volumeLevel > 10 ? true : false"
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
                :percent="(countdown / detail?.duration) * 100"
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
              class="w-[140px] h-[50px] bg-[rgba(255,255,255,0.15)] hover:bg-[rgba(255,255,255,0.3)] hover:scale-105 cursor-pointer flex items-center justify-center text-lg font-medium text-white flex-shrink-0 rounded-[693px]"
              @click="
                () => {
                  if (submitLoading) return;
                  if (!isDebating) {
                    startDebateBefore();
                  } else {
                    stopDebateBefore();
                  }
                }
              "
            >
              <LoadingOutlined class="mr-1" v-if="submitLoading" />
              {{
                !isDebating && !submitLoading
                  ? "开始答辩"
                  : `结束答辩${
                      detail.duration - countdown < 4
                        ? "(" + (4 - (detail.duration - countdown)) + "秒)"
                        : ""
                    }`
              }}
            </div>
          </div>
        </section>
        <section
          v-if="showArr?.[2] == 3"
          class="absolute bottom-[-10px] left-0 w-full bottom-card fade-in"
        >
          <div
            class="bottom-card-in py-6 px-2 text-[#FFFFFF] font-semibold text-2xl"
          >
            <div class="w-full h-full overflow-y-auto custom-scrollbar px-4">
              {{ detail?.theme }}
            </div>
          </div>
        </section>
      </div>
    </div>
    <!-- 加载遮罩层 -->
    <LoadingOverlay v-if="!isWarmupDone" />
  </main>
</template>
<script setup lang="ts">
import DefenseHeader from "@/components/Layout/componets/DefenseHeader.vue";
import WaveComponent from "@/components/Wave/index.vue";
import { ref, onMounted, onUnmounted, createVNode, watch } from "vue";
import { useSpeechRecognition } from "@/composables/useSpeechRecognition.js";
import { useRoute, useRouter } from "vue-router";
import { getAssessmentDetail } from "@/api/assessment";
import {
  Upload,
  Progress,
  UploadChangeParam,
  message,
  Modal,
} from "ant-design-vue";
import { getToken } from "@/utils/auth";
import LoadingOverlay from "@/components/LoadingOverlay/newIndex.vue";
import { FileType } from "ant-design-vue/es/upload/interface.js";
import {
  ExclamationCircleOutlined,
  LoadingOutlined,
} from "@ant-design/icons-vue";
import { uploadAssessmentFile } from "@/api/studentAssessment";
import { endAssessment, endDefense, startDefense } from "@/api/kh";

const action = process.env.VUE_APP_BASE_API + "/file/upload";
const headers = ref({
  Authorization: `Bearer ${getToken()}`,
});
const showArr = ref<number[]>([]);
const pageOff = ref(false);

const route = useRoute();
const router = useRouter();
const uploadProgress = ref(0);
const uploadStatus = ref<
  "success" | "normal" | "active" | "exception" | undefined
>(undefined);
const videoRef = ref<any>(null);
const isDebating = ref(false);
const countdown = ref(0);
let countdownTimer: any = null;
let videoStream: any = null;
const uploadFile = ref<Recordable>({});
const isWarmupDone = ref(false);
const detail = ref<Recordable>({});
const submitLoading = ref(false);

const {
  transcriptionText,
  startAudio,
  initAudio,
  closeAudio,
  startHeartbeat,
  restartAudio,
  volumeLevel,
} = useSpeechRecognition();

async function startCamera() {
  try {
    videoStream = await navigator.mediaDevices.getUserMedia({ video: true });
    console.log(videoRef.value);

    if (videoRef.value) {
      videoRef.value.srcObject = videoStream;
      videoRef.value.onloadedmetadata = () => {
        videoRef.value.play();
      };
    }
  } catch (err) {
    console.error("摄像头打开失败：", err);
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

function startDebateBefore() {
  startCountdown();
  startDefense({ assessmentId: route.query?.id }).then((res) => {
    startDebate();
  });
}

function stopDebateBefore() {
  if (detail.value?.duration - countdown.value > 3) {
    stopDebate();
  }
}

async function startDebate() {
  isDebating.value = true;
  await startAudio();
  // startCountdown();
}

async function stopDebate() {
  Modal.confirm({
    title: "确认结束答辩吗？",
    icon: createVNode(ExclamationCircleOutlined),
    okText: "确认结束",
    onOk: async () => {
      submitLoading.value = true;
      isDebating.value = false;
      stopCamera();
      await closeAudio();
      clearInterval(countdownTimer);
      endDefense({
        assessmentId: route.query?.id,
      }).then(() => {
        if (detail.value?.question) {
          router.push({
            name: "QuestionPage",
            query: route.query,
          });
        } else {
          endAssessment({
            assessmentId: route.query?.id,
          }).then(() => {
            router.push({
              name: "ResultOverPage",
              query: route.query,
            });
          });
        }
      });
    },
  });
}
function startCountdown() {
  countdown.value = detail.value?.duration;
  clearInterval(countdownTimer);
  countdown.value--;
  countdownTimer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      isDebating.value = false;
      stopCamera();
      closeAudio();
      clearInterval(countdownTimer);
      endDefense({
        assessmentId: route.query?.id,
      }).then(() => {
        if (detail.value?.question) {
          router.push({
            name: "QuestionPage",
            query: route.query,
          });
        } else {
          endAssessment({
            assessmentId: route.query?.id,
          });
          router.push({
            name: "ResultOverPage",
            query: route.query,
          });
        }
      });
    }
  }, 1000);
}

onMounted(async () => {
  const temp = await getAssessmentDetail(route.query?.id as unknown as number);
  detail.value = temp.data || {};
  countdown.value = temp.data?.duration;
  await initAudio();
  startHeartbeat();
  await startCamera();
  isWarmupDone.value = true;
});

onUnmounted(() => {
  pageOff.value = true;
  stopCamera();
  // closeAudio();
  clearInterval(countdownTimer);
});

window.addEventListener("beforeunload", async () => {
  stopCamera();
  await closeAudio();
  clearInterval(countdownTimer);
});

const beforeUpload = (file: FileType) => {
  if (file?.type != "application/pdf") {
    message.error("只允许上传 PDF 文件");
    return;
  }
  uploadProgress.value = 0;
  return true;
};
const handleChange = (info: UploadChangeParam) => {
  uploadStatus.value = "active";
  if (info.file.status == "uploading") {
    uploadProgress.value = info.file?.percent as number;
  }
  if (info.file.status == "done") {
    if (info.file?.response?.code == 200) {
      uploadStatus.value = "success";
      uploadProgress.value = 100;
      uploadFile.value = {
        fileSize: info.file?.size,
        fileUrl: info.file?.response?.data?.url,
        fileName: info.file?.response?.data?.fileName,
        presignedUrl: info.file?.response?.data?.presignedUrl,
      };
      uploadAssessmentFile({
        assessmentId: route.query?.id,
        file: {
          fileSize: info.file?.size,
          fileUrl: info.file?.response?.data?.url,
          fileName: info.file?.response?.data?.fileName,
        },
      });
    } else {
      uploadStatus.value = "exception";
    }
  } else if (info.file.status == "error") {
    uploadStatus.value = "exception";
  }
};

watch(
  () => isWarmupDone.value,
  () => {
    if (isWarmupDone.value) {
      showArr.value = [1];
      setTimeout(() => {
        showArr.value?.push(2);
      }, 400);
      setTimeout(() => {
        showArr.value?.push(3);
      }, 800);
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
  border-radius: 50px 50px 50px 50px;
  padding: 24px;
  backdrop-filter: blur(40px);
}
.bottom-card-in {
  width: 100%;
  height: 100%;
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
.reUpload {
  width: 140px;
  height: 50px;
  background: linear-gradient(
    270deg,
    rgba(255, 255, 255, 0.4) 0%,
    rgba(255, 255, 255, 0.8) 99%
  );
  box-shadow: 0px 10px 20px 0px rgba(18, 21, 73, 0.08);
  border-radius: 693px 693px 693px 693px;
  border: 1px solid #ffffff;
  cursor: pointer;
  font-weight: 500;
  font-size: 18px;
  color: #4866ff;
}
.wave {
  width: 100%;
  height: 128px;
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
  transition: opacity 0.3s ease;
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
