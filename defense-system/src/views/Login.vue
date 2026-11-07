<template>
  <div class="login" :style="{ backgroundImage: `url(${campus})` }">
    <div class="login-shade"></div>

    <header class="login-inner login-header">
      <img :src="logoWhite" :alt="BRAND.school + BRAND.college" class="login-logo" />
    </header>

    <main class="login-inner login-main">
      <section class="login-intro">
        <div class="login-intro-college">{{ BRAND.school }} · {{ BRAND.college }}</div>
        <h1 class="login-intro-title">{{ BRAND.product }}</h1>
        <div class="login-intro-rule"></div>
        <p class="login-intro-desc">在线语音面试 · 智能评分 · 教师复核</p>

        <dl class="login-notice">
          <dt>考前请准备</dt>
          <dd><span class="login-notice-no">1</span>带麦克风的耳机，作答时全程佩戴</dd>
          <dd><span class="login-notice-no">2</span>最新版 Chrome 或 Edge 浏览器</dd>
          <dd><span class="login-notice-no">3</span>安静的环境与稳定的网络</dd>
        </dl>
      </section>

      <section class="login-box">
        <div class="login-box-head">
          <div class="login-box-title">用户登录</div>
          <div class="login-box-sub">请使用学院分配的账号登录</div>
        </div>
        <Form layout="vertical" :model="form" @finish="submit" :required-mark="false" class="login-form">
          <FormItem label="账号" name="account" :rules="[{ required: true, message: '请输入账号' }]">
            <Input v-model:value="form.account" size="large" placeholder="学号 / 工号" autocomplete="username">
              <template #prefix><UserOutlined class="muted" /></template>
            </Input>
          </FormItem>
          <FormItem label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <InputPassword
              v-model:value="form.password"
              size="large"
              placeholder="密码"
              autocomplete="current-password"
            >
              <template #prefix><LockOutlined class="muted" /></template>
            </InputPassword>
          </FormItem>
          <Button type="primary" html-type="submit" size="large" block :loading="loading" class="login-submit">
            登录
          </Button>
        </Form>
        <p class="login-tip">账号由学院统一分配，忘记密码请联系任课教师重置。</p>
      </section>
    </main>

    <footer class="login-inner login-footer">
      <span>版权所有：{{ BRAND.school }}{{ BRAND.college }}</span>
      <span>地址：{{ BRAND.address }}</span>
      <span>邮编：{{ BRAND.postcode }}</span>
      <span>电话：{{ BRAND.phone }}</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button, Form, FormItem, Input, InputPassword } from "ant-design-vue";
import { LockOutlined, UserOutlined } from "@ant-design/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { BRAND } from "@/config/brand";
import logoWhite from "@/assets/brand/logo-marx-white.png";
import campus from "@/assets/brand/campus.jpg";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const form = reactive({ account: "", password: "" });
const loading = ref(false);

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
/* 校园照片铺底，上面压一层大工蓝，照片分辨率不高也不显粗糙 */
.login {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--c-primary-deep);
  background-position: center 60%;
  background-size: cover;
  color: #fff;
}

.login-shade {
  position: absolute;
  inset: 0;
  background: rgba(0, 38, 110, 0.72);
}

.login-inner {
  position: relative;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.login-header {
  padding-top: 28px;
}

.login-logo {
  display: block;
  height: 52px;
  width: auto;
}

.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 64px;
  padding-top: 40px;
  padding-bottom: 40px;
}

.login-intro {
  max-width: 560px;
}

.login-intro-college {
  font-size: 16px;
  letter-spacing: 4px;
  color: rgba(255, 255, 255, 0.82);
}

.login-intro-title {
  margin: 14px 0 0;
  font-size: 52px;
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: 8px;
  color: #fff;
}

.login-intro-rule {
  width: 56px;
  height: 3px;
  margin: 26px 0 22px;
  background: #fff;
}

.login-intro-desc {
  margin: 0;
  font-size: 16px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.85);
}

.login-notice {
  margin: 48px 0 0;
  padding: 20px 24px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-left: 3px solid #fff;
  max-width: 400px;
}

.login-notice dt {
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 600;
}

.login-notice dd {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 5px 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.88);
}

.login-notice-no {
  flex: none;
  width: 20px;
  height: 20px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.login-box {
  flex: none;
  width: 400px;
  background: #fff;
  color: var(--c-text);
  box-shadow: 0 12px 32px rgba(0, 20, 60, 0.28);
}

.login-box-head {
  padding: 28px 36px 0;
}

.login-box-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--c-primary);
  letter-spacing: 2px;
}

.login-box-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--c-text-3);
}

.login-form {
  padding: 24px 36px 0;
}

.login-submit {
  height: 44px;
  margin-top: 4px;
  font-size: 16px;
  letter-spacing: 6px;
}

.login-tip {
  margin: 0;
  padding: 16px 36px 28px;
  font-size: 12px;
  line-height: 20px;
  color: var(--c-text-3);
}

.login-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px 20px;
  padding-top: 18px;
  padding-bottom: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.16);
  max-width: none;
  font-size: 12px;
  line-height: 20px;
  color: rgba(255, 255, 255, 0.62);
}

@media (max-width: 960px) {
  .login-main {
    flex-direction: column;
    justify-content: center;
    gap: 32px;
  }
  .login-intro {
    max-width: 400px;
    width: 100%;
  }
  .login-intro-title {
    font-size: 36px;
    letter-spacing: 4px;
  }
  .login-intro-rule {
    margin: 18px 0 14px;
  }
  .login-notice {
    display: none;
  }
}

@media (max-width: 640px) {
  .login-inner {
    padding: 0 16px;
  }
  .login-header {
    padding-top: 18px;
  }
  .login-logo {
    height: 34px;
  }
  .login-main {
    padding-top: 28px;
    padding-bottom: 28px;
  }
  .login-intro-college {
    font-size: 13px;
    letter-spacing: 2px;
  }
  .login-intro-title {
    font-size: 30px;
  }
  .login-intro-desc {
    font-size: 13px;
    letter-spacing: 1px;
  }
  .login-box {
    width: 100%;
  }
  .login-box-head,
  .login-form {
    padding-left: 24px;
    padding-right: 24px;
  }
  .login-tip {
    padding: 14px 24px 22px;
  }
}
</style>
