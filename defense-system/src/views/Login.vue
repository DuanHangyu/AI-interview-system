<template>
  <div class="login">
    <header class="login-header">
      <div class="login-inner login-header-inner">
        <img :src="logoWhite" :alt="BRAND.school + BRAND.college" class="login-logo" />
        <span class="login-product">{{ BRAND.product }}</span>
      </div>
    </header>

    <main class="login-main" :style="{ backgroundImage: `url(${campus})` }">
      <div class="login-inner login-main-inner">
        <div class="login-box">
          <div class="login-box-title">用户登录</div>
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
            <Button type="primary" html-type="submit" size="large" block :loading="loading">登录</Button>
          </Form>
          <p class="login-tip">账号由学院统一分配，忘记密码请联系任课教师重置。</p>
        </div>
      </div>
    </main>

    <footer class="login-footer">
      <div class="login-inner">
        版权所有：{{ BRAND.school }}{{ BRAND.college }}　地址：{{ BRAND.address }}　邮编：{{ BRAND.postcode }}　电话：{{
          BRAND.phone
        }}
      </div>
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
.login {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #fff;
}

.login-inner {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 24px;
}

.login-header {
  background: var(--c-primary);
  color: #fff;
}

.login-header-inner {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 88px;
}

.login-logo {
  display: block;
  height: 50px;
  width: auto;
}

.login-product {
  padding-left: 20px;
  border-left: 1px solid rgba(255, 255, 255, 0.5);
  font-size: 24px;
  line-height: 32px;
  letter-spacing: 3px;
  white-space: nowrap;
}

.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  min-height: 520px;
  background-color: #dfe7f2;
  background-position: center;
  background-size: cover;
}

.login-main-inner {
  display: flex;
  justify-content: flex-end;
  padding-top: 48px;
  padding-bottom: 48px;
}

.login-box {
  width: 380px;
  background: #fff;
  border-top: 3px solid var(--c-primary);
}

.login-box-title {
  padding: 18px 32px;
  border-bottom: 1px solid var(--c-border-2);
  font-size: 18px;
  font-weight: 600;
  color: var(--c-primary);
}

.login-form {
  padding: 24px 32px 4px;
}

.login-tip {
  margin: 0;
  padding: 12px 32px 24px;
  font-size: 12px;
  line-height: 20px;
  color: var(--c-text-3);
}

.login-footer {
  padding: 18px 0;
  background: #00318c;
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

@media (max-width: 640px) {
  .login-inner {
    padding: 0 12px;
  }
  .login-header-inner {
    height: 60px;
  }
  .login-logo {
    height: 32px;
  }
  .login-product {
    display: none;
  }
  .login-main-inner {
    justify-content: center;
    padding-top: 32px;
    padding-bottom: 32px;
  }
  .login-box {
    width: 100%;
  }
}
</style>
