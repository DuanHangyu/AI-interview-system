import { onUnmounted, ref } from "vue";
import { getToken } from "@/utils/auth";
import { mittEmit } from "@/utils/appMitt";

export function useSpeechRecognition(options = {}) {
  const transcriptionText = ref("");
  const audioReady = ref(false);
  const audioError = ref("");
  let ws = ref(null);
  let audioContext = null;
  let micNode = null;
  let audioStream = null;
  let isStreaming = false;

  const audioChunks = [];
  let sendInterval = null;
  let heartbeatInterval = null;

  let manuallyClosed = false;
  let reconnectTimer = null;
  let initPromise = null;
  const handlers = options || {};

  function mergeChunks(chunks) {
    let totalLength = 0;
    chunks.forEach((chunk) => (totalLength += chunk.length));
    const merged = new Uint8Array(totalLength);
    let offset = 0;
    chunks.forEach((chunk) => {
      merged.set(chunk, offset);
      offset += chunk.length;
    });
    return merged;
  }

  async function startAudio() {
    isStreaming = true;
    const maxAttempts = 10;
    let attempt = 0;
    while (
      (!ws.value || ws.value.readyState !== WebSocket.OPEN) &&
      attempt < maxAttempts
    ) {
      console.log(`等待 WebSocket 连接... 尝试次数 ${attempt + 1}`);
      await new Promise((resolve) => setTimeout(resolve, 500));
      attempt++;
    }
    if (!sendInterval && ws.value && ws.value.readyState === WebSocket.OPEN) {
      sendInterval = setInterval(() => {
        flushAudioChunks();
      }, 100);
      return true;
    }
    audioError.value = "录音服务连接失败，请检查网络后重试";
    return false;
  }

  function flushAudioChunks() {
    if (
      audioChunks.length > 0 &&
      ws.value &&
      ws.value.readyState === WebSocket.OPEN
    ) {
      const merged = mergeChunks(audioChunks);
      console.log("ws.send");
      ws.value.send(merged);
      audioChunks.length = 0;
      return true;
    }
    return false;
  }

  function stopAudio(options = {}) {
    const { flush = true } = options;
    if (flush) {
      flushAudioChunks();
    }
    isStreaming = false;
    if (sendInterval) {
      clearInterval(sendInterval);
      sendInterval = null;
    }
    audioChunks.length = 0;
  }

  function startHeartbeat() {
    if (
      heartbeatInterval ||
      !ws.value ||
      ws.value.readyState !== WebSocket.OPEN
    )
      return;
    heartbeatInterval = setInterval(() => {
      if (ws.value.readyState === WebSocket.OPEN) {
        try {
          ws.value.send(JSON.stringify({ action: "ping" }));
          console.log("send heartbeat");
        } catch (e) {
          console.warn("Failed to send heartbeat", e);
        }
      }
    }, 5000); // 每 5 秒发送一次心跳
  }

  function stopHeartbeat() {
    if (heartbeatInterval) {
      clearInterval(heartbeatInterval);
      heartbeatInterval = null;
    }
  }

  let analyser = null;
  let animationFrameId = null;
  const volumeLevel = ref(0);
  // 音量检测函数
  const checkVolume = () => {
    if (!analyser) return;

    const dataArray = new Uint8Array(analyser.frequencyBinCount);
    analyser.getByteFrequencyData(dataArray);

    // 计算平均音量 (0-255)
    let sum = 0;
    for (const value of dataArray) sum += value;
    const average = sum / dataArray.length;

    // 转换为百分比 (0-100)
    volumeLevel.value = Math.min(Math.round((average / 255) * 100), 100);
    animationFrameId = requestAnimationFrame(checkVolume);
  };

  async function cleanupAudioResources({ closeSocket = false, clearChunks = true } = {}) {
    stopHeartbeat();
    if (sendInterval) {
      clearInterval(sendInterval);
      sendInterval = null;
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (animationFrameId) {
      cancelAnimationFrame(animationFrameId);
      animationFrameId = null;
    }

    if (analyser) {
      try {
        analyser.disconnect();
      } catch (e) {}
      analyser = null;
    }

    if (micNode) {
      try {
        micNode.disconnect();
      } catch (e) {}
      micNode = null;
    }

    if (audioStream) {
      audioStream.getTracks().forEach((track) => track.stop());
      audioStream = null;
    }

    if (audioContext) {
      try {
        if (audioContext.state !== "closed") {
          await audioContext.close();
        }
      } catch (e) {
        console.warn("关闭 AudioContext 失败", e);
      }
      audioContext = null;
    }

    if (closeSocket && ws.value) {
      console.log("Client close", ws.value.readyState);
      if (ws.value.readyState !== WebSocket.CLOSED) {
        ws.value.close(1000, "Client close");
      }
      ws.value = null;
    }

    if (clearChunks) {
      audioChunks.length = 0;
    }
    volumeLevel.value = 0;
  }

  function scheduleReconnect(shouldResumeStreaming) {
    if (manuallyClosed || reconnectTimer) {
      return;
    }
    console.warn("WebSocket 被动关闭，尝试重新连接...");
    reconnectTimer = setTimeout(async () => {
      reconnectTimer = null;
      await cleanupAudioResources({ clearChunks: true });
      await initAudio();
      if (shouldResumeStreaming) {
        await startAudio();
      }
    }, 1000);
  }

  async function initAudio() {
    if (initPromise) {
      return initPromise;
    }
    initPromise = doInitAudio().finally(() => {
      initPromise = null;
    });
    return initPromise;
  }

  async function doInitAudio() {
    try {
      audioError.value = "";
      audioReady.value = false;
      manuallyClosed = false;
      audioStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      // { sampleRate: 16000 }
      audioContext = new window.AudioContext({ sampleRate: 16000 });

      await audioContext.audioWorklet.addModule("processor.js");

      const source = audioContext.createMediaStreamSource(audioStream);
      micNode = new AudioWorkletNode(audioContext, "mic-processor");

      micNode.port.onmessage = (event) => {
        if (!isStreaming) return;
        const chunk = new Uint8Array(event.data);
        audioChunks.push(chunk);
      };

      source.connect(micNode);

      // -----------------------------------
      // 2. 单独初始化 AnalyserNode (处理音量检测)
      analyser = audioContext.createAnalyser();
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.2;
      source.connect(analyser); // 注意：同一个 source 可以连接多个节点

      // 启动音量检测循环
      animationFrameId = requestAnimationFrame(checkVolume);
      // -----------------------------------
      const socket = new WebSocket(
        `${process.env.VUE_APP_BASE_VOICE_WS}`,
        getToken()
      );
      ws.value = socket;

      socket.onopen = () => {
        audioReady.value = true;
        audioError.value = "";
        startHeartbeat();
      };

      socket.onmessage = (event) => {
        // mittEmit("SOURCE:BLOB", event.data);
        try {
          const result = event.data;
          if (result) {
            transcriptionText.value += result + " ";
          }
        } catch (e) {
          console.error("WebSocket onmessage", e);
        }
      };

      socket.onerror = (e) => {
        audioReady.value = false;
        audioError.value = "录音服务连接异常，请检查网络后重试";
        console.error("WebSocket error", e);
      };

      socket.onclose = function (event) {
        console.log("WebSocket close");
        console.log("代码:", event.code);
        console.log("是否清理关闭:", event.wasClean);
        console.log("原因:", event.reason);
        stopHeartbeat();
        let shouldResumeStreaming = isStreaming;
        if (
          !manuallyClosed &&
          shouldResumeStreaming &&
          typeof handlers.onConnectionLost === "function"
        ) {
          try {
            shouldResumeStreaming =
              handlers.onConnectionLost({
                code: event.code,
                reason: event.reason,
                wasClean: event.wasClean,
              }) !== false;
          } catch (e) {
            console.error("录音连接中断处理失败", e);
          }
        }
        stopAudio({ flush: false });
        if (ws.value === socket) {
          ws.value = null;
        }
        audioReady.value = false;

        if (!manuallyClosed) {
          scheduleReconnect(shouldResumeStreaming);
        }
      };
      return true;
    } catch (err) {
      audioReady.value = false;
      audioError.value = "无法打开麦克风，请允许浏览器麦克风权限后重试";
      console.error("麦克风打开失败：", err);
      return false;
    }
  }

  async function closeAudio() {
    try {
      manuallyClosed = true; // 主动关闭标志
      stopAudio();
      await cleanupAudioResources({ closeSocket: true, clearChunks: true });
    } catch (err) {
      console.error("停止音频资源出错：", err);
    }
  }

  function warmup() {
    return new Promise((resolve) => {
      const warmupWs = new WebSocket(
        `${process.env.VUE_APP_BASE_VOICE_WS}`,
        getToken()
      );
      warmupWs.onopen = () => {
        const silence = new Int16Array(48000 * 3).fill(0);
        warmupWs.send(new Uint8Array(silence.buffer));
        setTimeout(() => {
          if (warmupWs.readyState === WebSocket.OPEN) {
            warmupWs.close(1000, "Warmup done");
          }
          resolve();
        }, 3000);
      };
      warmupWs.onerror = (e) => {
        console.error("WebSocket warmup error", e);
        resolve();
      };
    });
  }

  async function restartAudio() {
    await closeAudio();
    await initAudio();
    await startAudio();
  }

  onUnmounted(() => {
    closeAudio();
    cancelAnimationFrame(animationFrameId);
  });

  return {
    transcriptionText,
    startAudio,
    stopAudio,
    initAudio,
    closeAudio,
    warmup,
    startHeartbeat,
    stopHeartbeat,
    restartAudio,
    volumeLevel,
    audioReady,
    audioError,
  };
}
