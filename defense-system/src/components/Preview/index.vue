<template>
  <Modal
    v-model:open="open"
    title="预览"
    width="60%"
    :bodyStyle="{
      height: 'calc(80vh - 100px)',
      overflowY: 'auto',
      padding: 0,
    }"
  >
    <div class="h-[1px] w-full absolute bg-[#D9D9D9] left-0"></div>
    <div class="py-4 size-full relative">
      <Spin
        :spinning="loading"
        class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-10"
      />
      <iframe :src="url" class="size-full" frameborder="0"></iframe>
    </div>
    <template #footer>
      <div class="flex justify-end">
        <Button @click="open = false">关闭</Button>
      </div>
    </template>
  </Modal>
</template>
<script setup lang="ts">
import { Modal, Button, Spin } from "ant-design-vue";
import { ref } from "vue";
import { getFileSign, previewFile } from "@/api/common";

const open = ref<boolean>(false);
const url = ref<string>("");
const loading = ref(false);

defineExpose({
  openModal: (e: string) => {
    open.value = true;
    url.value = "";
    loading.value = true;
    getFileSign({ url: e })
      .then((res) => {
        url.value = res?.data;
      })
      .finally(() => {
        loading.value = false;
      });
  },
});
</script>
