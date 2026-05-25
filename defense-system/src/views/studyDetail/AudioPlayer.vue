<template>
  <div ref="audioPlayer"></div>
</template>
<script lang="ts" setup>
import { ref, onMounted } from "vue";
import Player from "xgplayer";
// 现在music作为一个固定的preset使用，不再继承player, 解决耦合性过大问题
import MusicPreset from "xgplayer-music";
import "xgplayer-music/dist/index.min.css";
import "xgplayer/dist/index.min.css";

const props = defineProps({
  url: {
    type: String,
    default: "",
  },
});

const audioPlayer = ref<HTMLDivElement>();
onMounted(() => {
  const player = new Player({
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
});
</script>
<style>
.xgplayer-backward,
.xgplayer-next,
.xgplayer-next,
.xgplayer-forward {
  display: none;
}
.xgplayer .xgplayer-controls {
  background-image: initial;
}
.xgplayer.xgplayer-music .xgplayer-controls {
  height: 50px;
  display: flex;
  align-items: center;
}
.xgplayer .flex-controls .xg-inner-controls{
    bottom: 6px;
}
</style>
