<template>
  <div
    class="w-screen h-screen login-container overflow-hidden flex items-center justify-end"
    :style="`background-size:${size ? '100% auto' : 'auto 100%'}`"
  >
    <div class="absolute top-20 left-[91px] flex items-center">
      <img
        src="@/assets/company.png"
        class="w-[297px] h-[92px] scale-y-[3.4]"
        alt=""
      />
      <div class="w-[3px] h-[48px] bg-[#4B84FF] ml-3"></div>
      <img src="@/assets/logo.png" class="w-[194px] h-[65px] ml-8" alt="" />
    </div>
    <!-- <div class="login-box flex flex-col items-center justify-center px-[54px]">
      <h1 class="mb-10 flex items-center relative !font-bold">
        综合<span class="text-[#4B84FF]"> 答辩 </span>
        系统
        <img
          src="@/assets/login-tip.png"
          class="w-[38px] h-[30px] absolute top-[-18px] right-[-20px]"
          alt=""
        />
      </h1>
      <Form ref="formRef" :model="data" size="large" class="w-full">
        <FormItem name="account" label="">
          <Input
            v-model:value.trim="data.account"
            class="w-full"
            placeholder="请输入账号"
            @keydown.enter.native="submitLogin"
          />
        </FormItem>
        <FormItem name="password" label="">
          <InputPassword
            v-model:value.trim="data.password"
            placeholder="请输入密码"
            @keydown.enter.native="submitLogin"
          />
        </FormItem>
        <FormItem class="mt-10">
          <div class="w-full text-center">
            <Button
              @click="submitLogin"
              :loading="loading"
              type="primary"
              shape="round"
            >
              登录
            </Button>
          </div>
        </FormItem>
      </Form>
    </div> -->
    <div class="login-box2 flex flex-col items-center justify-center">
      <img src="@/assets/dllgIcon.png" class="w-[280px] h-[60px]" alt="" />
      <div class="text-[28px] font-bold text-[#0E0A31] mt-4 text-center">
        <div>大连理工大学</div>
        <div>思政课AI综合答辩系统</div>
      </div>
      <Form ref="formRef" :model="data" size="large" class="w-full mt-[50px]">
        <FormItem name="account" label="">
          <Input
            v-model:value.trim="data.account"
            class="w-full"
            placeholder="请输入账号"
            @keydown.enter.native="submitLogin"
          />
        </FormItem>
        <FormItem name="password" label="">
          <InputPassword
            v-model:value.trim="data.password"
            placeholder="请输入密码"
            @keydown.enter.native="submitLogin"
          />
        </FormItem>
        <FormItem class="mt-10">
          <div class="w-full text-center">
            <Button
              @click="submitLogin"
              :loading="loading"
              type="primary"
              :disabled="!data.account || !data.password"
              :class="data.account && data.password ? '!bg-[#524fff]' : ''"
            >
              登录
            </Button>
          </div>
        </FormItem>
      </Form>
      <div class="mt-6 text-center text-sm font-normal text-[#A9A9AB]">
        <span>登录即代表阅读并同意</span>
        <span class="cursor-pointer text-[#524FFF]">
          《服务协议与隐私政策》
        </span>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { Form, FormItem, Input, InputPassword, Button } from "ant-design-vue";
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { onMounted } from "vue";
import { onUnmounted } from "vue";

const loading = ref(false);
const data = ref({
  account: "",
  password: "",
});
const formRef = ref();
const router = useRouter();
const submitLogin = () => {
  if (!data.value.account && !data.value.password) {
    return;
  }
  formRef.value
    .validate()
    .then(() => {
      loading.value = true;
      useAuthStore()
        ?.login(data.value)
        .then(() => {
          router.replace("/");
        })
        .finally(() => {
          loading.value = false;
        });
    })
    .catch((error: any) => {
      console.log("error", error);
    });
};

const size = ref(window.innerWidth / window.innerHeight > 2880 / 1800);
const handleResize = () => {
  size.value = window.innerWidth / window.innerHeight > 2880 / 1800;
};

onMounted(() => {
  window.addEventListener("resize", handleResize);
});
onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
});
</script>
<style scoped>
.login-container {
  /* background: url(@/assets/background.png); */
  background: url(https://defense-assessment-new.oss-ap-southeast-1.aliyuncs.com/defense-assessment-new/2025/08/18/WechatIMG1253.jpg);
  background-size: 100% auto;
  background-repeat: no-repeat;
  background-position: center center;
}

.login-box {
  width: 428px;
  height: 528px;
  background: rgba(243, 248, 254, 0.95);
  box-shadow: 0 15px 25px rgba(0, 0, 0, 0.1);
  border-radius: 15px;
  margin-right: 5vw;
}
.login-box h1 {
  font-weight: 500;
  font-size: 30px;
  color: #333333;
}

.login-box2 {
  width: 404px;
  background: #f4f7fd;
  box-shadow: 0px 4px 176px 0px rgba(32, 96, 168, 0.08);
  border-radius: 12px 12px 12px 12px;
  padding: 40px 32px;
  margin-right: 5vw;
}

:deep(.el-input__wrapper) {
  height: 50px;
  border-radius: 100px 100px 100px 100px;
  border: 1px solid rgba(0, 0, 0, 0.2);
  padding: 0px 18px;
  box-shadow: initial;
  .el-input__inner {
    font-size: 16px;
    &::placeholder {
      font-weight: 500;
      font-size: 16px;
      color: #999999;
    }
  }
}
.login-box :deep(.ant-btn) {
  height: 50px;
  width: 240px;
  margin: 0 auto;
  margin-top: 20px;
  font-size: 20px;
  font-weight: bold;
  &:hover {
    background: #4b84ff;
    color: #ffffff;
  }
}
.login-box2 :deep(.ant-btn) {
  width: 100%;
  margin: 0 auto;
  margin-top: 20px;
  font-size: 20px;
  font-weight: bold;
  height: 43px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.default {
  background: #dbdbdb;
  color: #666666;
}
.active {
  background: #4b84ff;
  color: #ffffff;
}

/* 默认状态（密码隐藏） */
:deep(.el-input__wrapper .el-input__suffix .el-icon) {
  color: #999999;
}

:deep(.el-input__wrapper input[type="text"] ~ .el-input__suffix .el-icon) {
  color: #4b84ff;
}
</style>
