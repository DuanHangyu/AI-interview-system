import { onUnmounted, ref } from "vue";
import { getToken } from "@/utils/auth";
import { mittEmit } from "@/utils/appMitt";

export function useSpeechRecognition() {
  const transcriptionText = ref("");
  let ws = ref(null);
  let audioContext = null;
  let micNode = null;
  let audioStream = null;
  let isStreaming = false;

  const audioChunks = [];
  let sendInterval = null;
  let heartbeatInterval = null;

  let manuallyClosed = false;

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
        if (audioChunks.length > 0 && ws.value.readyState === WebSocket.OPEN) {
          const merged = mergeChunks(audioChunks);
          console.log("ws.send");
          ws.value.send(merged);
          audioChunks.length = 0;
        }
      }, 100);
    }
  }

  function stopAudio() {
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

  async function initAudio() {
    try {
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
      ws.value = new WebSocket(
        `${process.env.VUE_APP_BASE_VOICE_WS}`,
        getToken()
      );

      ws.value.onopen = () => {
        startHeartbeat();
      };

      ws.value.onmessage = (event) => {
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

      ws.value.onerror = (e) => {
        console.error("WebSocket error", e);
      };

      ws.value.onclose = function (event) {
        console.log("WebSocket close");
        console.log("代码:", event.code);
        console.log("是否清理关闭:", event.wasClean);
        console.log("原因:", event.reason);
        stopHeartbeat();

        if (!manuallyClosed) {
          console.warn("WebSocket 被动关闭，尝试重新连接...");
          setTimeout(() => {
            initAudio(); // 尝试重连
          }, 1000);
        }
      };
    } catch (err) {
      console.error("麦克风打开失败：", err);
    }
  }

  async function closeAudio() {
    try {
      manuallyClosed = true; // 主动关闭标志
      stopAudio();
      stopHeartbeat();

      if (audioStream) {
        audioStream.getTracks().forEach((track) => track.stop());
        audioStream = null;
      }

      if (micNode) {
        micNode.disconnect();
        micNode = null;
      }

      if (audioContext) {
        if (audioContext.state !== "closed") {
          await audioContext.close();
        }
        audioContext = null;
      }

      if (ws.value) {
        console.log("Client close", ws.value.readyState);
        if (ws.value.readyState !== WebSocket.CLOSED) {
          ws.value.close(1000, "Client close");
          const maxAttempts = 10;
          let attempt = 0;
          while (
            (!ws.value || ws.value.readyState !== WebSocket.CLOSED) &&
            attempt < maxAttempts
          ) {
            console.log(`等待 WebSocket 关闭... 尝试次数 ${attempt + 1}`);
            await new Promise((resolve) => setTimeout(resolve, 500));
            attempt++;
          }
        }
        ws.value = null;
      }

      audioChunks.length = 0;
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
  };
}
