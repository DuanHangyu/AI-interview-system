<template>
  <div
    class="size-full grid grid-cols-2 gap-x-4 text-[rgba(0,0,0,0.88)] overflow-hidden"
  >
    <!-- 左侧区域 -->
    <div
      class="bg-[#ffffffb3] rounded-lg flex flex-col overflow-hidden size-full p-5"
    >
      <div class="flex items-center mb-3 space-x-8 flex-shrink-0">
        <div class="text-base flex-shrink-0 font-bold truncate">
          {{ detail?.theme }}
        </div>
      </div>
      <div
        class="flex-grow overflow-hidden flex flex-col justify-center items-center"
      >
        <div v-if="!uploadFile?.presignedUrl" class="text-center">
          <img src="@/assets/noImg.png" alt="" class="w-[180px]" />
          <Upload
            name="file"
            accept=".pdf"
            :showUploadList="false"
            :headers="headers"
            :action="action"
            @change="handleChange"
            :beforeUpload="beforeUpload"
          >
            <div v-if="uploadStatus" class="text-[#555] my-4 text-center">
              {{
                uploadStatus == "active"
                  ? "正在上传..."
                  : uploadStatus == "success"
                  ? "上传成功"
                  : uploadStatus == "exception"
                  ? "上传失败"
                  : ""
              }}
            </div>
            <Button type="primary" class="flex items-center" size="large">
              上传文件
            </Button>
          </Upload>
          <div class="text-[#555] mt-3 text-sm">支持上传文件格式：pdf</div>
          <Progress
            v-if="uploadProgress > 0 && uploadProgress < 100"
            :percent="uploadProgress"
            :showInfo="false"
            :status="uploadStatus == 'success' ? 'active' : uploadStatus"
            :size="10"
          />
        </div>

        <div
          v-else
          class="size-full flex flex-col items-center justify-center py-6"
        >
          <div
            class="w-[90%] border-[1px] border-solid border-[#ddd] rounded-md text-center flex-grow"
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
          >
            <div v-if="uploadStatus" class="text-[#555] my-4 text-center">
              {{
                uploadStatus == "active"
                  ? "正在上传..."
                  : uploadStatus == "success"
                  ? "上传成功"
                  : uploadStatus == "exception"
                  ? "上传失败"
                  : ""
              }}
            </div>
            <Button type="primary" class="flex items-center" size="large">
              重新上传
            </Button>
          </Upload>
        </div>
      </div>
    </div>

    <!-- 右侧区域 -->
    <div
      class="bg-[#ffffffb3] rounded-xl flex flex-col justify-between p-5 h-full overflow-hidden"
    >
      <div
        class="flex items-center justify-between mb-3 space-x-8 flex-shrink-0"
      >
        <div class="text-sm flex-shrink-0 w-36">
          剩余时间：{{ countdown }} 秒
        </div>
        <Progress
          class="flex-grow"
          :showInfo="false"
          strokeColor="#01C883"
          trailColor="#DFDFDF"
          :percent="(countdown / detail?.duration) * 100"
          :size="10"
        />
      </div>

      <div class="video-container flex-grow">
        <video
          v-show="isDebating"
          ref="videoRef"
          autoplay
          muted
          playsinline
          :style="{ opacity: isDebating ? 1 : 0.5 }"
          class="size-full"
        ></video>
        <div v-if="!isDebating" class="video-overlay" title="未开始答辩">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="80"
            height="80"
            viewBox="0 0 24 24"
            fill="#999"
          >
            <path
              d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"
            />
          </svg>
        </div>
      </div>
      <div
        class="h-[150px] flex-shrink-0 flex flex-col items-center justify-center"
      >
        <Button
          type="primary"
          size="large"
          class="w-[140px] h-10"
          @click="startDebateBefore"
          v-if="!isDebating"
        >
          开始答辩
        </Button>
        <template v-else>
          <div class="w-[80%] h-[72px] mb-[30px]">
            <WaveComponent :loading="volumeLevel > 10 ? true : false" />
          </div>
          <Button
            type="primary"
            danger
            class="w-[140px] h-10"
            size="large"
            ghost
            @click="stopDebateBefore"
          >
            结束答辩
          </Button>
        </template>
      </div>
    </div>
  </div>
  <!-- 加载遮罩层 -->
  <LoadingOverlay v-if="!isWarmupDone" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, createVNode, watch } from "vue";
import { useSpeechRecognition } from "../composables/useSpeechRecognition.js";
import { useRoute, useRouter } from "vue-router";
import { getAssessmentDetail } from "@/api/assessment";
import {
  Upload,
  Button,
  Progress,
  UploadChangeParam,
  message,
  Modal,
} from "ant-design-vue";
import { getToken } from "@/utils/auth";
import LoadingOverlay from "@/components/LoadingOverlay/index.vue";
import { FileType } from "ant-design-vue/es/upload/interface.js";
import WaveComponent from "@/components/Wave/index.vue";
import { ExclamationCircleOutlined } from "@ant-design/icons-vue";
import { uploadAssessmentFile } from "@/api/studentAssessment";

const action = process.env.VUE_APP_BASE_API + "/file/upload";
const headers = ref({
  Authorization: `Bearer ${getToken()}`,
});

// 心跳间隔（毫秒）
const HEARTBEAT_INTERVAL = 5000; // 30秒
let heartbeatTimer: number | null | undefined = null;
const reconnectAttempts = ref(0);
const maxReconnectAttempts = 5;
const reconnectInterval = 3000; // 3秒重试一次
let reconnectTimer: number | null | undefined = null;
const socket = ref<WebSocket | null>(null);
const isConnected = ref(false);
const connectionStatus = ref("Disconnected");
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
  socket.value?.send(
    JSON.stringify({
      message: route.query?.id,
      action: "start-defense",
    })
  );
}

function stopDebateBefore() {
  stopDebate();
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
      socket.value?.send(
        JSON.stringify({
          message: route.query?.id,
          action: "end-defense",
        })
      );
      isDebating.value = false;
      stopCamera();
      await closeAudio();
      clearInterval(countdownTimer);
    },
  });
}

const reConnection = async () => {
  restartAudio();
};

function startCountdown() {
  countdown.value = detail.value?.duration;
  clearInterval(countdownTimer);
  countdown.value--;
  countdownTimer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      socket.value?.send(
        JSON.stringify({
          message: route.query?.id,
          action: "end-defense",
        })
      );
      isDebating.value = false;
      stopCamera();
      closeAudio();
      clearInterval(countdownTimer);
    }
  }, 1000);
}

const connect = () => {
  if (socket.value) {
    socket.value?.close();
  }
  // 清除之前的重连定时器
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
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
    const message = JSON.parse(event.data);
    if (message?.content == "开始答辩" && message?.normalMessage) {
      startDebate();
    }
    if (message?.content == "结束答辩") {
      Modal.destroyAll();
      // 等待完毕再跳转
      if (detail.value?.question) {
        router.push({
          name: "QuestionPage",
          query: route.query,
        });
      } else {
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

onMounted(async () => {
  const temp = await getAssessmentDetail(route.query?.id as unknown as number);
  detail.value = temp.data || {};
  countdown.value = temp.data?.duration;
  await initAudio();
  startHeartbeat();
  await startCamera();
  connect();
  isWarmupDone.value = true;
});

onUnmounted(() => {
  pageOff.value = true;
  stopCamera();
  // closeAudio();
  clearInterval(countdownTimer);
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
</script>

<style scoped>
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
  margin-bottom: 10px;
  overflow: hidden;
}

video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 5px;
  transition: opacity 0.3s ease;
}

.video-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: black;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
  border-radius: 5px;
}
</style>
