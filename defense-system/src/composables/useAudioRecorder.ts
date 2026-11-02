import { onUnmounted, ref } from "vue";

// 本地录音：麦克风 → 16kHz 单声道 16bit PCM → WAV Blob。不做任何网络传输。
// 多声道输入（部分麦克风阵列的第一个声道是静音的）先混成单声道再处理。
const TARGET_SAMPLE_RATE = 16000;
const MEDIA_REQUEST_TIMEOUT = 10000;
const DEVICE_STORAGE_KEY = "interview.micDeviceId";
// 峰值低于这个电平（约 -30dBFS）视为没有采集到人声
export const SILENCE_PEAK = 0.035;

const WORKLET_SOURCE = `
class RecorderProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0];
    if (input && input.length > 0 && input[0].length > 0) {
      const mono = new Float32Array(input[0].length);
      for (const channel of input) {
        for (let i = 0; i < mono.length; i++) mono[i] += channel[i] / input.length;
      }
      this.port.postMessage(mono, [mono.buffer]);
    }
    return true;
  }
}
registerProcessor("recorder-processor", RecorderProcessor);
`;

function readSavedDevice() {
  try {
    return localStorage.getItem(DEVICE_STORAGE_KEY) || "";
  } catch (e) {
    return "";
  }
}

function saveDevice(deviceId: string) {
  try {
    if (deviceId) localStorage.setItem(DEVICE_STORAGE_KEY, deviceId);
    else localStorage.removeItem(DEVICE_STORAGE_KEY);
  } catch (e) {
    // 存不了就每次用默认设备
  }
}

function requestMicrophone(deviceId: string): Promise<MediaStream> {
  if (!navigator.mediaDevices?.getUserMedia) {
    return Promise.reject(new Error("当前浏览器不支持麦克风访问"));
  }
  const timeoutMessage = "麦克风权限响应超时，请确认浏览器权限弹窗后重试。";
  let finished = false;
  let timeoutId: number | undefined;
  const mediaRequest = navigator.mediaDevices
    .getUserMedia({
      audio: {
        deviceId: deviceId ? { exact: deviceId } : undefined,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    })
    .catch((error) => {
      // 保存的设备已拔出时退回默认设备
      if (deviceId && (error?.name === "OverconstrainedError" || error?.name === "NotFoundError")) {
        saveDevice("");
        return navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
        });
      }
      throw error;
    })
    .then((stream) => {
      if (finished) {
        stream.getTracks().forEach((track) => track.stop());
        throw new Error(timeoutMessage);
      }
      return stream;
    });
  const timeout = new Promise<never>((_, reject) => {
    timeoutId = window.setTimeout(() => reject(new Error(timeoutMessage)), MEDIA_REQUEST_TIMEOUT);
  });
  return Promise.race([mediaRequest, timeout]).finally(() => {
    finished = true;
    if (timeoutId) window.clearTimeout(timeoutId);
  });
}

function encodeWav(chunks: Int16Array[], sampleRate: number): Blob {
  const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const buffer = new ArrayBuffer(44 + length * 2);
  const view = new DataView(buffer);
  const writeString = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i));
  };
  writeString(0, "RIFF");
  view.setUint32(4, 36 + length * 2, true);
  writeString(8, "WAVE");
  writeString(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeString(36, "data");
  view.setUint32(40, length * 2, true);
  let offset = 44;
  for (const chunk of chunks) {
    for (let i = 0; i < chunk.length; i++, offset += 2) {
      view.setInt16(offset, chunk[i], true);
    }
  }
  return new Blob([buffer], { type: "audio/wav" });
}

export function useAudioRecorder() {
  const ready = ref(false);
  const recording = ref(false);
  const error = ref("");
  const volumeLevel = ref(0);
  // 本次录音（或麦克风测试）期间的最大峰值，0～1
  const peakLevel = ref(0);
  const devices = ref<{ deviceId: string; label: string }[]>([]);
  const deviceId = ref(readSavedDevice());
  const deviceLabel = ref("");

  let stream: MediaStream | null = null;
  let audioContext: AudioContext | null = null;
  let source: MediaStreamAudioSourceNode | null = null;
  let workletNode: AudioWorkletNode | null = null;
  let scriptNode: ScriptProcessorNode | null = null;
  let muteNode: GainNode | null = null;
  let chunks: Int16Array[] = [];
  // 降采样：把输入样本按 ratio 个一组求平均（简单低通），跨帧累计
  let resampleAcc = 0;
  let resampleCount = 0;
  let resamplePos = 0;

  function handleFrame(frame: Float32Array) {
    let sum = 0;
    let peak = 0;
    for (let i = 0; i < frame.length; i++) {
      sum += frame[i] * frame[i];
      peak = Math.max(peak, Math.abs(frame[i]));
    }
    volumeLevel.value = Math.min(100, Math.round(Math.sqrt(sum / frame.length) * 400));
    peakLevel.value = Math.max(peakLevel.value, peak);
    if (!recording.value || !audioContext) return;

    const ratio = audioContext.sampleRate / TARGET_SAMPLE_RATE;
    const out: number[] = [];
    for (let i = 0; i < frame.length; i++) {
      resampleAcc += frame[i];
      resampleCount++;
      resamplePos += 1;
      if (resamplePos >= ratio) {
        resamplePos -= ratio;
        const sample = Math.max(-1, Math.min(1, resampleAcc / resampleCount));
        out.push(sample < 0 ? sample * 0x8000 : sample * 0x7fff);
        resampleAcc = 0;
        resampleCount = 0;
      }
    }
    if (out.length) chunks.push(Int16Array.from(out));
  }

  async function refreshDevices() {
    try {
      const list = await navigator.mediaDevices.enumerateDevices();
      devices.value = list
        .filter((d) => d.kind === "audioinput")
        .map((d, i) => ({ deviceId: d.deviceId, label: d.label || `麦克风 ${i + 1}` }));
    } catch (e) {
      devices.value = [];
    }
    const track = stream?.getAudioTracks()[0];
    deviceLabel.value = track?.label || "";
    const active = track?.getSettings().deviceId;
    if (active) deviceId.value = active;
  }

  // 切换麦克风：重新打开音频链路
  async function selectDevice(id: string): Promise<boolean> {
    if (recording.value) return false;
    saveDevice(id);
    deviceId.value = id;
    dispose();
    return init();
  }

  // 浏览器要求用户操作后才能启动音频；在点击事件里调用
  async function resume() {
    if (audioContext?.state === "suspended") {
      await audioContext.resume().catch(() => undefined);
    }
  }

  function resetPeak() {
    peakLevel.value = 0;
  }

  async function init(): Promise<boolean> {
    if (ready.value) return true;
    error.value = "";
    try {
      stream = await requestMicrophone(deviceId.value);
      audioContext = new AudioContext();
      source = audioContext.createMediaStreamSource(stream);
      try {
        const url = URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: "application/javascript" }));
        await audioContext.audioWorklet.addModule(url);
        URL.revokeObjectURL(url);
        workletNode = new AudioWorkletNode(audioContext, "recorder-processor");
        workletNode.port.onmessage = (event) => handleFrame(event.data as Float32Array);
        source.connect(workletNode);
        // 部分浏览器只处理连接到输出的节点；接一个静音增益，保证 process() 被调用且不会回放麦克风声音
        muteNode = audioContext.createGain();
        muteNode.gain.value = 0;
        workletNode.connect(muteNode);
        muteNode.connect(audioContext.destination);
      } catch (workletError) {
        // 旧浏览器不支持 AudioWorklet 时回退到 ScriptProcessor
        scriptNode = audioContext.createScriptProcessor(4096, 1, 1);
        scriptNode.onaudioprocess = (event) => handleFrame(event.inputBuffer.getChannelData(0));
        source.connect(scriptNode);
        muteNode = audioContext.createGain();
        muteNode.gain.value = 0;
        scriptNode.connect(muteNode);
        muteNode.connect(audioContext.destination);
      }
      ready.value = true;
      await refreshDevices();
      return true;
    } catch (e) {
      error.value = e instanceof Error && e.message ? e.message : "无法打开麦克风，请允许浏览器麦克风权限后重试。";
      dispose();
      return false;
    }
  }

  async function start(): Promise<boolean> {
    if (!ready.value && !(await init())) return false;
    await resume();
    chunks = [];
    resampleAcc = 0;
    resampleCount = 0;
    resamplePos = 0;
    peakLevel.value = 0;
    recording.value = true;
    return true;
  }

  async function stop(): Promise<Blob> {
    // 先等最后几帧回调落地再停止采集，避免截掉回答的结尾
    await new Promise((resolve) => setTimeout(resolve, 150));
    recording.value = false;
    const blob = encodeWav(chunks, TARGET_SAMPLE_RATE);
    chunks = [];
    return blob;
  }

  function dispose() {
    recording.value = false;
    ready.value = false;
    chunks = [];
    workletNode?.port.close();
    workletNode?.disconnect();
    scriptNode?.disconnect();
    muteNode?.disconnect();
    source?.disconnect();
    stream?.getTracks().forEach((track) => track.stop());
    audioContext?.close().catch(() => undefined);
    workletNode = null;
    scriptNode = null;
    muteNode = null;
    source = null;
    stream = null;
    audioContext = null;
    volumeLevel.value = 0;
  }

  onUnmounted(dispose);

  return {
    init,
    start,
    stop,
    dispose,
    resume,
    resetPeak,
    selectDevice,
    ready,
    recording,
    error,
    volumeLevel,
    peakLevel,
    devices,
    deviceId,
    deviceLabel,
  };
}
