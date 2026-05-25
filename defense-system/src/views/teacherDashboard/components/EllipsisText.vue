<template>
  <div class="w-full relative">
    <div
      class="whitespace-pre-wrap line-clamp-3 content relative"
      ref="contentRef"
    >
      {{ text }}

      <span
        v-if="textHeight > contentHeight"
        class="absolute bottom-0 right-0 bg-white"
      >
        <span>...</span>
        <span
          class="!text-[#1677FF] !cursor-pointer z-10"
          @click="$emit('expand')"
        >
          查看全部
        </span>
      </span>
    </div>
    <div class="absolute top-0 left-0 w-full opacity-0 -z-10" ref="textRef">
      {{ text }}
    </div>
  </div>
</template>
<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from "vue";

defineProps({
  text: {
    type: String,
    default: "",
  },
});
defineEmits(["expand"]);
const contentRef = ref();
const textRef = ref();
const contentHeight = ref(0);
const textHeight = ref(0);

const listenResize = () => {
  contentHeight.value = contentRef.value?.offsetHeight;
  textHeight.value = textRef.value?.offsetHeight;
};

onMounted(() => {
  nextTick(() => {
    listenResize();
  });
  window.addEventListener("resize", listenResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", listenResize);
});
</script>
