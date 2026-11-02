<template>
  <div class="login">
    <section class="login-brand">
      <img :src="emblemWhite" alt="" class="brand-watermark" />
      <svg class="brand-ribbon" viewBox="0 0 800 600" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 470 C 220 380, 420 560, 800 360 L 800 420 C 440 600, 230 430, 0 520 Z" />
        <path d="M0 540 C 260 450, 470 610, 800 450 L 800 475 C 480 640, 250 485, 0 565 Z" />
      </svg>

      <img :src="logoWhite" :alt="BRAND.school + BRAND.college" class="brand-logo" />

      <div class="brand-body">
        <div class="brand-eyebrow">AI INTERVIEW SYSTEM</div>
        <h1>{{ BRAND.product }}</h1>
        <p class="brand-lead">{{ BRAND.college }}结构化面试与答辩评估平台</p>
        <div class="brand-features">
          <div v-for="f in features" :key="f.title" class="brand-feature">
            <component :is="f.icon" class="brand-feature-icon" />
            <div>
              <div class="brand-feature-title">{{ f.title }}</div>
              <div class="brand-feature-desc">{{ f.desc }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="brand-foot">
        <div class="brand-motto">
          <span v-for="w in mottoWords" :key="w">{{ w }}</span>
        </div>
        <div>{{ BRAND.address }} · 邮编 {{ BRAND.postcode }}</div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <img :src="logoRed" :alt="BRAND.school + BRAND.college" class="login-card-logo" />
        <h2>欢迎登录</h2>
        <p class="muted">请使用学院分配的账号登录系统</p>
        <Form layout="vertical" :model="form" @finish="submit" class="mt-8" :required-mark="false">
          <FormItem label="账号" name="account" :rules="[{ required: true, message: '请输入账号' }]">
            <Input v-model:value="form.account" size="large" placeholder="请输入学号 / 工号" autocomplete="username">
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
          <Button type="primary" html-type="submit" size="large" block :loading="loading" class="login-submit">
            登 录
          </Button>
        </Form>
        <div class="login-tip">
          <SafetyCertificateOutlined />
          <span>忘记密码请联系任课教师重置</span>
        </div>
      </div>
      <div class="login-copy">© {{ year }} {{ BRAND.school }}{{ BRAND.college }}</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { markRaw, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Form, FormItem, Input, InputPassword } from "ant-design-vue";
import {
  AudioOutlined,
  LockOutlined,
  ReadOutlined,
  SafetyCertificateOutlined,
  TrophyOutlined,
  UserOutlined,
} from "@ant-design/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { BRAND } from "@/config/brand";
import logoWhite from "@/assets/brand/logo-marx-white.png";
import logoRed from "@/assets/brand/logo-marx-red.png";
import emblemWhite from "@/assets/brand/emblem-white.svg";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const form = reactive({ account: "", password: "" });
const loading = ref(false);
const year = new Date().getFullYear();
const mottoWords = BRAND.motto.split(" ");

const features = [
  { icon: markRaw(ReadOutlined), title: "题库随机抽题", desc: "教师预设题库与追问，按考核维度均衡抽取" },
  { icon: markRaw(AudioOutlined), title: "语音作答 · 自动转写", desc: "断网、刷新不丢进度，录音全程留存可回放" },
  { icon: markRaw(TrophyOutlined), title: "多维评分 · 教师复核", desc: "按评分标准逐项打分，报告一键导出 PDF" },
];

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
  background: var(--c-bg);
}

.login-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 56%;
  max-width: 860px;
  padding: 40px 64px 32px;
  color: #fff;
  background:
    radial-gradient(circle at 15% 10%, rgba(255, 210, 160, 0.18), transparent 45%),
    linear-gradient(150deg, #b3141f 0%, #c3192a 40%, #8c0f1a 100%);
  overflow: hidden;
}

.brand-watermark {
  position: absolute;
  right: -120px;
  top: 50%;
  width: 560px;
  height: 560px;
  transform: translateY(-50%);
  opacity: 0.07;
  pointer-events: none;
}

.brand-ribbon {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 60%;
  pointer-events: none;
}

.brand-ribbon path:first-child {
  fill: rgba(232, 190, 120, 0.16);
}

.brand-ribbon path:last-child {
  fill: rgba(255, 220, 160, 0.12);
}

.brand-logo {
  position: relative;
  width: 360px;
  max-width: 100%;
  height: auto;
}

.brand-body {
  position: relative;
  margin: auto 0;
  padding: 40px 0;
}

.brand-eyebrow {
  display: inline-block;
  padding: 3px 10px;
  border: 1px solid rgba(240, 205, 150, 0.6);
  border-radius: 3px;
  font-size: 12px;
  letter-spacing: 3px;
  color: #f3d7a8;
}

.brand-body h1 {
  margin: 18px 0 0;
  color: #fff;
  font-size: 44px;
  line-height: 1.25;
  font-weight: 700;
  letter-spacing: 6px;
  text-shadow: 0 2px 12px rgba(80, 0, 10, 0.3);
}

.brand-lead {
  margin: 12px 0 36px;
  font-size: 17px;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.85);
}

.brand-features {
  display: grid;
  gap: 12px;
  max-width: 460px;
}

.brand-feature {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 18px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.07);
  backdrop-filter: blur(4px);
}

.brand-feature-icon {
  margin-top: 2px;
  font-size: 20px;
  color: #f3d7a8;
}

.brand-feature-title {
  font-size: 15px;
  font-weight: 600;
}

.brand-feature-desc {
  margin-top: 2px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

.brand-foot {
  position: relative;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.brand-motto {
  display: flex;
  gap: 18px;
  margin-bottom: 8px;
  font-size: 15px;
  letter-spacing: 4px;
  color: #f3d7a8;
}

.login-panel {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background:
    radial-gradient(circle at 100% 0%, rgba(195, 25, 42, 0.06), transparent 40%),
    radial-gradient(circle at 0% 100%, rgba(201, 160, 99, 0.1), transparent 45%),
    var(--c-bg);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 40px 28px;
  background: #fff;
  border-radius: 14px;
  border-top: 4px solid var(--c-primary);
  box-shadow: 0 12px 40px rgba(90, 30, 20, 0.1);
}

.login-card-logo {
  display: none;
  width: 240px;
  height: auto;
  margin-bottom: 24px;
}

.login-card h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 1px;
}

.login-card p {
  margin: 8px 0 0;
}

.login-submit {
  height: 44px;
  margin-top: 8px;
  font-size: 16px;
  letter-spacing: 4px;
}

.login-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 20px;
  font-size: 12px;
  color: var(--c-text-3);
}

.login-copy {
  margin-top: 24px;
  font-size: 12px;
  color: var(--c-text-3);
}

@media (max-width: 960px) {
  .login-brand {
    display: none;
  }
  .login-card-logo {
    display: block;
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 28px 20px 20px;
  }
}
</style>
