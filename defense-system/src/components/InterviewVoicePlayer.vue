<template>
  <div class="hidden" aria-hidden="true"></div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, defineEmits } from "vue";
import { getToken } from "@/utils/auth";
import PCMPlayer from "pcm-player";

const emit = defineEmits(["ready", "playing", "broadcastingEnd"]);
const AUDIO_SAMPLE_RATE = 24000;

const audioLength = ref(0);
const fragmentAllLength = ref(0);
const isWsEnd = ref(false);
const time = ref(0);
const timer = ref(null);

let player = null;
let socketVoice = null;
let heartbeatInterval = null;
let ignorePayloadUntilEnd = false;

function resetPlaybackState() {
  audioLength.value = 0;
  fragmentAllLength.value = 0;
  isWsEnd.value = false;
  time.value = 0;
  if (timer.value) {
    clearInterval(timer.value);
    timer.value = null;
  }
}

function broadcastingEnd() {
  emit("broadcastingEnd");
  emit("playing", false);
  resetPlaybackState();
}

function base64ToArrayBuffer(base64, isSlice) {
  const binaryString = atob(base64);
  const length = binaryString.length;
  let bytes = new Uint8Array(length);

  for (let i = 0; i < length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  if (isSlice) {
    const wavHeaderLength = 44;
    if (bytes.length > wavHeaderLength) {
      bytes = bytes.slice(wavHeaderLength);
    }
  }

  return bytes.buffer;
}

function ensurePlayer() {
  if (player) {
    return;
  }

  player = new PCMPlayer({
    encoding: "16bitInt",
    channels: 1,
    sampleRate: AUDIO_SAMPLE_RATE,
    flushingTime: 500,
    onstatechange: () => {
      emit("playing", true);
      if (timer.value) {
        clearInterval(timer.value);
        timer.value = null;
      }
      time.value = 0;
      timer.value = setInterval(() => {
        time.value += 1;
        if (
          time.value >= audioLength.value / AUDIO_SAMPLE_RATE / 2 + 1 &&
          isWsEnd.value
        ) {
          broadcastingEnd();
        }
      }, 1000);
    },
    onended: (node) => {
      fragmentAllLength.value +=
        node?.buffer?.byteLength || node?.buffer?.length * 2 || 0;
      if (
        fragmentAllLength.value >= audioLength.value &&
        fragmentAllLength.value &&
        isWsEnd.value
      ) {
        broadcastingEnd();
      }
    },
  });
}

function handleAudioPayload(payload) {
  if (ignorePayloadUntilEnd) {
    return;
  }

  ensurePlayer();

  const pcmData = base64ToArrayBuffer(payload, true);
  audioLength.value += pcmData.byteLength;

  if (player?.gainNode?.gain && player?.audioCtx) {
    player.gainNode.gain.setValueAtTime(1.2, player.audioCtx.currentTime);
  }
  player.feed(pcmData);
}

function handleAudioEnd() {
  if (ignorePayloadUntilEnd) {
    ignorePayloadUntilEnd = false;
    resetPlaybackState();
    return;
  }

  isWsEnd.value = true;
  if (audioLength.value === 0) {
    broadcastingEnd();
  }
}

function startHeartbeat() {
  if (
    heartbeatInterval ||
    !socketVoice ||
    socketVoice.readyState !== WebSocket.OPEN
  ) {
    return;
  }

  heartbeatInterval = setInterval(() => {
    if (socketVoice?.readyState === WebSocket.OPEN) {
      socketVoice.send(JSON.stringify({ action: "ping" }));
    }
  }, 5000);
}

function stopHeartbeat() {
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval);
    heartbeatInterval = null;
  }
}

const stopVoice = () => {
  if (audioLength.value > 0) {
    ignorePayloadUntilEnd = true;
  }

  if (player) {
    player.destroy();
    player = null;
  }

  if (socketVoice?.readyState === WebSocket.OPEN) {
    socketVoice.send(JSON.stringify({ action: "stop" }));
  }

  resetPlaybackState();
};

onMounted(() => {
  socketVoice = new WebSocket(
    `${process.env.VUE_APP_BASE_PLAYVOICE_WS}`,
    getToken()
  );

  socketVoice.addEventListener("open", () => {
    emit("ready");
    startHeartbeat();
  });

  socketVoice.addEventListener("message", (event) => {
    if (
      event?.data?.includes("audio:end") &&
      event?.data?.includes("normalMessage")
    ) {
      handleAudioEnd();
      return;
    }

    if (event?.data?.includes("normalMessage")) {
      return;
    }

    if (event?.data && !/^\s*$/.test(event.data)) {
      handleAudioPayload(event.data);
    }
  });
});

onUnmounted(() => {
  stopVoice();
  if (socketVoice?.readyState === WebSocket.OPEN) {
    socketVoice.close();
  }
  stopHeartbeat();
});

defineExpose({
  stopVoice,
  stopVideo: stopVoice,
});
</script>
