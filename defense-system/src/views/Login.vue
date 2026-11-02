<template>
  <div class="login">
    <section class="login-brand">
      <div class="brand-logo">
        <span class="brand-mark">AI</span>
        <span class="brand-name">AI 面试评估</span>
      </div>
      <div class="brand-body">
        <h1>结构化 AI 面试与答辩评估</h1>
        <p>教师预设题库，学生随机抽题作答，系统自动转写并按评分标准出具面试报告。</p>
        <ul>
          <li>
            <CheckCircleFilled />
            <span>题库随机抽题，按考核维度均衡分布</span>
          </li>
          <li>
            <CheckCircleFilled />
            <span>语音作答自动转写，断网刷新不丢进度</span>
          </li>
          <li>
            <CheckCircleFilled />
            <span>按维度评分，教师可复核调整，报告可导出 PDF</span>
          </li>
        </ul>
      </div>
      <div class="brand-foot">© {{ year }} AI 面试评估</div>
    </section>

    <section class="login-panel">
      <div class="login-form">
        <h2>账号登录</h2>
        <p class="muted">请使用学校分配的账号登录</p>
        <Form layout="vertical" :model="form" @finish="submit" class="mt-8" :required-mark="false">
          <FormItem label="账号" name="account" :rules="[{ required: true, message: '请输入账号' }]">
            <Input v-model:value="form.account" size="large" placeholder="请输入账号" autocomplete="username">
              <template #prefix><UserOutlined class="muted" /></template>
            </Input>
          </FormItem>
          <FormItem label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <InputPassword
              v-model:value="form.password"
              size="large"
              placeholder="请输入密码"
              autocomplete="current-password"
            >
              <template #prefix><LockOutlined class="muted" /></template>
            </InputPassword>
          </FormItem>
          <Button type="primary" html-type="submit" size="large" block :loading="loading" class="mt-2">
            登录
          </Button>
        </Form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Form, FormItem, Input, InputPassword } from "ant-design-vue";
import { CheckCircleFilled, LockOutlined, UserOutlined } from "@ant-design/icons-vue";
import { useAuthStore } from "@/stores/auth";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const form = reactive({ account: "", password: "" });
const loading = ref(false);
const year = new Date().getFullYear();

async function submit() {
  loading.value = true;
  try {
    await authStore.login(form);
    router.replace((route.query.redirect as string) || "/");
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login {
  display: flex;
  min-height: 100vh;
  background: var(--c-surface);
}

.login-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 44%;
  max-width: 640px;
  padding: 40px 56px;
  color: #fff;
  background: linear-gradient(160deg, #1d3d8f 0%, #2b5ce6 100%);
  overflow: hidden;
}

.login-brand::after {
  content: "";
  position: absolute;
  right: -160px;
  bottom: -160px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  border: 64px solid rgba(255, 255, 255, 0.06);
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #fff;
  color: var(--c-primary);
  font-weight: 700;
  font-size: 14px;
}

.brand-name {
  font-size: 16px;
  font-weight: 600;
}

.brand-body {
  margin: auto 0;
}

.brand-body h1 {
  margin: 0;
  color: #fff;
  font-size: 32px;
  line-height: 44px;
  font-weight: 600;
}

.brand-body p {
  margin: 16px 0 32px;
  max-width: 420px;
  font-size: 15px;
  line-height: 26px;
  color: rgba(255, 255, 255, 0.78);
}

.brand-body ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.brand-body li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
}

.brand-body li :deep(.anticon) {
  color: #7fb0ff;
}

.brand-foot {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.login-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.login-form {
  width: 100%;
  max-width: 360px;
}

.login-form h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.login-form p {
  margin: 8px 0 0;
}

@media (max-width: 860px) {
  .login-brand {
    display: none;
  }
}
</style>
