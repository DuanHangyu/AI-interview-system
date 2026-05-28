<template>
  <main class="console-layout">
    <div class="console-frame">
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

.console-content {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

@media (max-width: 900px) {
  .console-layout {
    min-height: 100dvh;
    height: auto;
    padding: 16px;
    overflow: auto;
  }

  .console-frame {
    min-height: calc(100dvh - 32px);
    height: auto;
  }

  .console-content {
    overflow: visible;
  }
}

@media (max-width: 560px) {
  .console-layout {
    padding: 10px;
  }

  .console-frame {
    min-height: calc(100dvh - 20px);
    gap: 10px;
  }
}
</style>
