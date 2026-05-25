<template>
  <div ref="audioPlayer" v-show="false"></div>
  <div
    class="player cursor-pointer flex items-center justify-between px-4 space-x-2"
  >
    <img
      :src="playerStatus == 'play' ? PlayIcon : StopIcon"
      class="w-6 h-6 flex-shrink-0 cursor-pointer"
      alt=""
      @click="startPlayer()"
    />
    <div class="flex-grow">
      <Slider
        :value="currentTime"
        @update:value="updateCurrentTime"
        @afterChange="blurFn"
        :max="audioTime"
        :tooltipOpen="false"
      />
    </div>
    <span class="text-sm font-bold text-white flex-shrink-0">
      {{ audioTime == Infinity ? 0 : audioTime }}s''
    </span>
    <!-- <img
      :src="
        playerStatus == 'stop'
          ? Audio3
          : currentTime % 3 == 2
          ? Audio3
          : currentTime % 3 == 1
          ? Audio2
          : Audio1
      "
      class="w-[23px] h-[24px]"
      alt=""
    /> -->
  </div>
</template>
<script lang="ts" setup>
import { Slider } from "ant-design-vue";
import { ref, onMounted } from "vue";
import Player from "xgplayer";
// 现在music作为一个固定的preset使用，不再继承player, 解决耦合性过大问题
import MusicPreset from "xgplayer-music";
import "xgplayer-music/dist/index.min.css";
import "xgplayer/dist/index.min.css";
const Audio1 = require("@/assets/studentDetail/audio1.png");
const Audio2 = require("@/assets/studentDetail/audio2.png");
const Audio3 = require("@/assets/studentDetail/audio3.png");
const PlayIcon = require("@/assets/studentDetail/play.png");
const StopIcon = require("@/assets/studentDetail/stop.png");

const emits = defineEmits(["stopPlayer"]);

const props = defineProps({
  url: {
    type: String,
    default: "",
  },
});

const audioPlayer = ref<HTMLDivElement>();
const audioTime = ref(0);
const playerStatus = ref("stop");
const player = ref();
const currentTime = ref(0);
onMounted(() => {
  player.value = new Player({
    el: audioPlayer.value,
    url: props.url,
    // url: "//sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/music/audio.mp3", //[{ src: '//sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/music/audio.mp3', name: '林宥嘉·脆弱一分钟', poster: '//sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/music/poster-small.jpeg' }],
    volume: 0.8,
    width: 500,
    height: 50,
    mediaType: "audio",
    presets: ["default", MusicPreset],
    ignores: ["playbackrate"],
    controls: {
      initShow: true,
      mode: "flex",
      next: false,
    },
    marginControls: true,
    videoConfig: {
      crossOrigin: "anonymous",
    },
  });
  player.value.on("loadeddata", () => {
    const duration = player.value?.duration;
    console.log("音频时长:", duration);
    audioTime.value = Math.floor(duration);
  });
  player.value.on("play", () => {
    playerStatus.value = "play";
  });

  player.value.on("pause", () => {
    playerStatus.value = "stop";
  });

  player.value.on("ended", () => {
    playerStatus.value = "stop";
  });

  player.value.on("waiting", () => {
    playerStatus.value = "stop";
  });

  player.value.on("playing", () => {
    playerStatus.value = "play";
  });

  player.value.on("error", () => {
    playerStatus.value = "stop";
  });

  player.value.on("timeupdate", () => {
    currentTime.value = player.value?.currentTime;
  });
});

const startPlayer = () => {
  emits("stopPlayer");
  console.log(player.value);

  if (playerStatus.value == "stop") {
    player.value?.play();
  } else {
    player.value?.pause();
  }
};

const updateCurrentTime = (e: any) => {
  emits("stopPlayer");
  currentTime.value = e;
};
const blurFn = () => {
  player.value?.seek(currentTime.value);
};

defineExpose({
  stopPlayer: () => {
    player.value?.pause();
  },
});
</script>
<style scoped>
:deep(.ant-slider-handle) {
  width: 16px;
  height: 16px;
  border-radius: 100%;
  background: linear-gradient(180deg, #6ae5df 0%, #6f7cf0 87%);
  box-sizing: border-box;
  border: 1px solid #ffffff;
  box-shadow: 0px 4px 10px 0px rgba(0, 0, 0, 0.12);
  top: -1px;
  &::before {
    display: none;
  }
  &::after {
    display: none;
  }
}

:deep(.ant-slider-track) {
  background: white;
}
:deep(.ant-slider-rail) {
  background: rgba(255, 255, 255, 0.7);
}
</style>
<style>
.player {
  width: 400px;
  height: 38px;
  background: linear-gradient(270deg, #69f7dc 0%, #7169f4 100%);
  box-shadow: 0px 9px 12px 0px rgba(18, 21, 73, 0.08);
  border-radius: 693px 693px 693px 693px;
}
</style>
