<template>
  <div class="student-route-shell">
    <router-view />
  </div>
</template>
<script setup lang="ts"></script>
<style scoped>
.student-route-shell {
  width: 100%;
  min-height: 100dvh;
  height: 100dvh;
  overflow: hidden;
}

@media (max-width: 980px) {
  .student-route-shell {
    height: auto;
    overflow: visible;
  }
}
</style>
