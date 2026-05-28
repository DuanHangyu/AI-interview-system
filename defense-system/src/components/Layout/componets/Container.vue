<template>
  <main class="console-layout">
    <div class="console-frame">
      <Header class="console-header-wrap" />
      <section class="console-content">
      <router-view v-slot="{ Component, route }">
        <keep-alive :include="['AdminDashboard', 'TeacherDashboard']">
          <component :is="Component" :key="route.path" />
        </keep-alive>
      </router-view>
      </section>
    </div>
  </main>
</template>
<script setup lang="ts">
import { Header } from "@/components/Layout";
defineOptions({
  name: "Layout",
});
</script>
<style scoped>
.console-layout {
  width: 100vw;
  height: 100vh;
  padding: 28px;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.36), rgba(0, 0, 0, 0.18)),
    linear-gradient(135deg, #eeeeec 0%, #d5d5d2 45%, #7f807d 100%);
}

.console-frame {
  width: min(1500px, 100%);
  height: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.console-header-wrap {
  flex-shrink: 0;
}

.console-content {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

@media (max-width: 900px) {
  .console-layout {
    padding: 16px;
  }
}
</style>
