<script setup lang="ts">
import { changePassword } from "@/api/common";
import { useAuthStore } from "@/stores/auth";
import { Modal, Form, FormItem, InputPassword, message } from "ant-design-vue";
import { Rule } from "ant-design-vue/es/form";
import { ref } from "vue";

const formRef = ref();
const loading = ref(false);
const open = ref<boolean>(false);
const formState = ref({
  password: "",
  confirmPassword: "",
});
const authStore = useAuthStore();

defineExpose({
  openModal: () => {
    open.value = true;
  },
});

const submit = () => {
  formRef.value
    .validate()
    .then(async () => {
      loading.value = true;
      await changePassword({
        password: formState.value?.password,
      });
      message.success("密码修改成功");
      open.value = false;
      setTimeout(() => {
        authStore.logout();
        location.href = "/login";
      }, 800);
    })
    .catch((error: any) => {
      console.log("error", error);
    })
    .finally(() => {
      loading.value = false;
    });
};

const validatePass = async (_rule: Rule, value: string) => {
  if (value === "") {
    return Promise.reject("该项为必填项");
  } else {
    if (formState.value.confirmPassword !== "") {
      formRef.value.validateFields("confirmPassword");
    }
    return Promise.resolve();
  }
};
const validatePass2 = async (_rule: Rule, value: string) => {
  if (value === "") {
    return Promise.reject("该项为必填项");
  } else if (value !== formState.value.password) {
    return Promise.reject("两次输入的密码不一致");
  } else {
    return Promise.resolve();
  }
};
</script>

<template>
  <Modal
    v-model:open="open"
    title="修改密码"
    @ok="submit"
    :confirm-loading="loading"
    :after-close="
      () => {
        formRef?.resetFields();
      }
    "
  >
    <div class="h-[1px] w-full absolute bg-[#D9D9D9] left-0"></div>
    <Form layout="vertical" class="pt-3" :model="formState" ref="formRef">
      <FormItem
        label="新密码"
        name="password"
        :rules="[
          { required: true, validator: validatePass, trigger: 'change' },
        ]"
      >
        <InputPassword
          v-model:value="formState.password"
          autocomplete="off"
          placeholder="请输入"
          visible
        />
      </FormItem>
      <FormItem
        label="确认密码"
        name="confirmPassword"
        :rules="[
          { required: true, validator: validatePass2, trigger: 'change' },
        ]"
      >
        <InputPassword
          v-model:value="formState.confirmPassword"
          autocomplete="off"
          placeholder="请输入"
          visible
        />
      </FormItem>
    </Form>
  </Modal>
</template>
