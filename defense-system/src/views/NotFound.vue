<template>
  <div class="not-found">
    <div class="code num">404</div>
    <h1>页面不存在</h1>
    <p class="muted">你访问的页面不存在或已被移除</p>
    <Button type="primary" @click="router.replace('/')">返回首页</Button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { Button } from "ant-design-vue";

const router = useRouter();
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  text-align: center;
}

.code {
  font-size: 64px;
  font-weight: 700;
  line-height: 1;
  color: var(--c-primary);
}

h1 {
  margin: 16px 0 8px;
  font-size: 20px;
  font-weight: 600;
}

p {
  margin: 0 0 24px;
}
</style>
