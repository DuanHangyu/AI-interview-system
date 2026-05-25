<template>
  <div
    class="w-screen h-screen overflow-hidden pt-2 px-[24px] pb-[18px] flex flex-col space-y-3 d_body"
  >
    <Header class="flex-shrink-0" />
    <div class="content flex-grow overflow-hidden">
      <router-view v-slot="{ Component, route }">
        <keep-alive :include="['AdminDashboard', 'TeacherDashboard']">
          <component :is="Component" :key="route.path" />
        </keep-alive>
      </router-view>
    </div>
  </div>
</template>
<script setup lang="ts">
import { Header } from "@/components/Layout";
defineOptions({
  name: "Layout",
});
</script>
<style scoped>
.d_body {
  background-image: url("@/assets/background2.png");
  /* 统一背景图 */
  background-size: cover;
  background-repeat: no-repeat;
  background-attachment: fixed;
  background-position: center center;
}
</style>
