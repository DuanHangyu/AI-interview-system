<template>
  <main class="login-page">
    <section class="login-shell" aria-label="AI 面试评估系统登录">
      <div class="login-showcase">
        <div class="brand-lockup">
          <div class="brand-logo-wrap">
            <img src="/logo.png" class="brand-logo" alt="AI 面试评估系统" />
          </div>
          <div class="brand-copy">
            <div class="brand-name">AI 面试评估系统</div>
            <div class="brand-meta">Interview Assessment Platform</div>
          </div>
        </div>

        <div class="showcase-copy">
          <div class="eyebrow">Minimal B-end Console</div>
          <h1>
            让每次答辩<br />
            都有清晰记录
          </h1>
          <p>
            面向课程答辩、项目验收和能力测评，集中完成预约、实时问答、评分与复盘分析。
          </p>
        </div>

        <div class="dashboard-preview" aria-hidden="true">
          <div class="preview-sidebar">
            <span></span>
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div class="preview-panel">
            <div class="preview-row">
              <div class="preview-title"></div>
              <div class="preview-badge">AI</div>
            </div>
            <div class="preview-progress">
              <div></div>
            </div>
            <div class="preview-grid">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>

        <div class="capability-row" aria-label="系统能力">
          <div class="capability-item">
            <AudioOutlined />
            <span>实时语音</span>
          </div>
          <div class="capability-item">
            <FileSearchOutlined />
            <span>材料分析</span>
          </div>
          <div class="capability-item">
            <SafetyCertificateOutlined />
            <span>智能评分</span>
          </div>
        </div>
      </div>

      <div class="login-panel" aria-label="登录">
        <div class="panel-heading">
          <div class="panel-icon">
            <UserOutlined />
          </div>
          <div>
            <h2>欢迎登录</h2>
            <p>进入你的面试评估工作台</p>
          </div>
        </div>

        <Form
          ref="formRef"
          :model="data"
          :rules="rules"
          layout="vertical"
          size="large"
          class="login-form"
          @finish="submitLogin"
        >
          <FormItem name="account" label="账号">
            <Input
              v-model:value.trim="data.account"
              autocomplete="username"
              placeholder="请输入账号"
            >
              <template #prefix>
                <UserOutlined />
              </template>
            </Input>
          </FormItem>
          <FormItem name="password" label="密码">
            <InputPassword
              v-model:value.trim="data.password"
              autocomplete="current-password"
              placeholder="请输入密码"
            >
              <template #prefix>
                <LockOutlined />
              </template>
            </InputPassword>
          </FormItem>
          <FormItem class="login-action">
            <Button
              html-type="submit"
              type="primary"
              :loading="loading"
              :disabled="!data.account || !data.password"
              @click="submitLogin"
              block
            >
              <span>登录</span>
              <ArrowRightOutlined />
            </Button>
          </FormItem>
        </Form>

        <div class="login-footer">
          仅供授权用户访问，所有评估操作将被记录。
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Form, FormItem, Input, InputPassword, Button } from "ant-design-vue";
import {
  ArrowRightOutlined,
  AudioOutlined,
  FileSearchOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from "@ant-design/icons-vue";
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const loading = ref(false);
const data = ref({
  account: "",
  password: "",
});
const rules = {
  account: [{ required: true, message: "请输入账号", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};
const formRef = ref();
const route = useRoute();
const router = useRouter();

const submitLogin = () => {
  if (!data.value.account || !data.value.password || loading.value) {
    return;
  }
  loading.value = true;
  useAuthStore()
    ?.login(data.value)
    .then(() => {
      router.replace((route.query.redirect as string) || "/");
    })
    .catch(() => {
      // Request interceptor owns the visible error notification.
    })
    .finally(() => {
      loading.value = false;
    });
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.36), rgba(0, 0, 0, 0.18)),
    linear-gradient(135deg, #eeeeec 0%, #d5d5d2 45%, #7f807d 100%);
}

.login-shell {
  width: min(1180px, 100%);
  min-height: 660px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  align-items: stretch;
  gap: 18px;
}

.login-showcase {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 36px;
  border: 1px solid var(--color-border-light);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow:
    0 28px 80px rgba(20, 21, 22, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(22px);
  color: var(--color-text-primary);
  overflow: hidden;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo-wrap {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: linear-gradient(145deg, #444544, #2f302f);
  box-shadow:
    0 12px 26px rgba(20, 21, 22, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.brand-logo {
  width: 30px;
  height: 30px;
  object-fit: contain;
  border-radius: 8px;
}

.brand-name {
  font-size: 18px;
  line-height: 1.3;
  font-weight: 800;
  color: var(--color-text-primary);
}

.brand-meta {
  margin-top: 3px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.showcase-copy {
  max-width: 640px;
  padding: 54px 0 22px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 800;
}

.showcase-copy h1 {
  margin: 22px 0 18px;
  max-width: 560px;
  color: var(--color-text-primary);
  font-size: 52px;
  line-height: 1.08;
  font-weight: 800;
  letter-spacing: 0;
}

.showcase-copy p {
  max-width: 500px;
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 16px;
  line-height: 1.85;
}

.dashboard-preview {
  width: min(560px, 100%);
  min-height: 210px;
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 14px;
  margin: 10px 0 26px;
}

.preview-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, #3d3e3d, #2d2e2d);
  box-shadow: 0 22px 54px rgba(20, 21, 22, 0.2);
}

.preview-sidebar span {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
}

.preview-sidebar span:first-child {
  background: #ffffff;
  box-shadow: inset 0 0 0 9px var(--color-accent-soft);
}

.preview-panel {
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.76);
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow:
    0 18px 46px rgba(20, 21, 22, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.preview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-title {
  width: 48%;
  height: 18px;
  border-radius: 999px;
  background: #17181a;
}

.preview-badge {
  height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--color-accent);
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 10px 24px var(--color-accent-glow);
}

.preview-progress {
  height: 42px;
  margin: 28px 0 18px;
  padding: 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: inset 0 1px 4px rgba(20, 21, 22, 0.05);
}

.preview-progress div {
  width: 58%;
  height: 100%;
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 8px 18px rgba(20, 21, 22, 0.08);
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.preview-grid span {
  height: 48px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: 0 10px 20px rgba(20, 21, 22, 0.06);
}

.capability-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.capability-item {
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 10px 24px rgba(20, 21, 22, 0.06);
}

.capability-item :deep(.anticon) {
  color: var(--color-accent);
  font-size: 16px;
}

.login-panel {
  align-self: center;
  min-height: 560px;
  padding: 38px 36px 30px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border: 1px solid var(--color-border-light);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow:
    0 28px 80px rgba(20, 21, 22, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
}

.panel-heading {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 34px;
}

.panel-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--color-accent);
  color: #ffffff;
  font-size: 18px;
  box-shadow: 0 12px 28px var(--color-accent-glow);
}

.panel-heading h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 28px;
  line-height: 1.2;
  font-weight: 800;
}

.panel-heading p {
  margin: 6px 0 0;
  color: var(--color-text-muted);
  font-size: 14px;
}

.login-form {
  width: 100%;
}

.login-form :deep(.ant-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.ant-form-item-label) {
  padding-bottom: 7px;
}

.login-form :deep(.ant-form-item-label > label) {
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 700;
}

.login-form :deep(.ant-input-affix-wrapper) {
  height: 50px;
  padding: 0 16px;
  border-radius: 999px !important;
}

.login-form :deep(.ant-input-affix-wrapper:hover),
.login-form :deep(.ant-input-affix-wrapper-focused) {
  border-color: rgba(231, 95, 73, 0.42) !important;
  box-shadow:
    inset 0 1px 4px rgba(20, 21, 22, 0.05),
    0 0 0 3px rgba(231, 95, 73, 0.12) !important;
}

.login-form :deep(.ant-input) {
  background: transparent;
  color: var(--color-text-primary);
  font-size: 15px;
}

.login-form :deep(.ant-input-prefix) {
  margin-right: 10px;
  color: var(--color-text-muted);
}

.login-action {
  margin-top: 14px;
  margin-bottom: 0 !important;
}

.login-action :deep(.ant-btn) {
  height: 50px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  border-radius: 999px;
  background: var(--color-accent);
  color: #ffffff;
  font-size: 16px;
  font-weight: 800;
  box-shadow: 0 14px 30px var(--color-accent-glow);
}

.login-action :deep(.ant-btn:not(:disabled):hover) {
  background: var(--color-accent-hover);
  color: #ffffff;
}

.login-action :deep(.ant-btn:disabled) {
  background: #c8c9c7;
  color: #ffffff;
  box-shadow: none;
}

.login-footer {
  margin-top: 26px;
  color: var(--color-text-muted);
  font-size: 13px;
  text-align: center;
}

@media (max-width: 960px) {
  .login-page {
    min-height: 100dvh;
    padding: 24px;
    align-items: flex-start;
    overflow-y: auto;
  }

  .login-shell {
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .login-showcase {
    padding: 26px;
  }

  .showcase-copy {
    padding: 40px 0 20px;
  }

  .showcase-copy h1 {
    font-size: 34px;
  }

  .showcase-copy p {
    font-size: 15px;
  }

  .login-panel {
    width: 100%;
    min-height: auto;
  }
}

@media (max-width: 520px) {
  .login-page {
    padding: 18px;
  }

  .brand-lockup {
    gap: 10px;
  }

  .brand-logo-wrap {
    width: 38px;
    height: 38px;
  }

  .brand-logo {
    width: 24px;
    height: 24px;
  }

  .showcase-copy {
    padding: 28px 0 16px;
  }

  .showcase-copy h1 {
    margin-top: 18px;
    font-size: 28px;
  }

  .dashboard-preview {
    grid-template-columns: 62px minmax(0, 1fr);
    min-height: 160px;
  }

  .preview-sidebar,
  .preview-panel {
    padding: 16px;
    border-radius: 20px;
  }

  .capability-row {
    display: none;
  }

  .login-panel {
    padding: 28px 22px 24px;
  }
}
</style>
