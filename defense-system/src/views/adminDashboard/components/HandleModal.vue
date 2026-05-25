<template>
  <Modal
    v-model:open="open"
    :title="detail?.id ? '编辑' : '新增'"
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
        label="姓名"
        name="name"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Input v-model:value="formState.name" placeholder="请输入" />
      </FormItem>
      <FormItem
        label="账号"
        name="account"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <Input v-model:value="formState.account" placeholder="请输入" />
      </FormItem>
      <FormItem
        label="密码"
        name="password"
        :rules="[{ required: true, message: '该项为必填项' }]"
      >
        <InputPassword
          v-model:value="formState.password"
          autocomplete="off"
          placeholder="请输入"
          visible
        />
      </FormItem>
      <FormItem label="手机号" name="phone">
        <Input v-model:value="formState.phone" placeholder="请输入" />
      </FormItem>
    </Form>
  </Modal>
</template>
<script setup lang="ts">
import {
  Modal,
  Form,
  FormItem,
  Input,
  InputPassword,
  message,
} from "ant-design-vue";
import { ref } from "vue";
import { createTeacher, modifyTeacher } from "@/api/teacher";
const emits = defineEmits(["success"]);

const open = ref<boolean>(false);
const detail = ref<Record<string, any>>({});
const formRef = ref();
const loading = ref(false);
const formState = ref({
  name: "",
  account: "",
  password: "",
  phone: "",
});

const submit = () => {
  formRef.value
    .validate()
    .then(async () => {
      loading.value = true;
      if (detail.value?.id) {
        await modifyTeacher(formState.value);
      } else {
        await createTeacher(formState.value);
      }
      message.success("保存成功");
      emits("success");
      open.value = false;
    })
    .catch((error: any) => {
      console.log("error", error);
    })
    .finally(() => {
      loading.value = false;
    });
};

defineExpose({
  openModal: (e: Record<string, any> = {}) => {
    open.value = true;
    detail.value = e;
    Object.assign(
      formState.value,
      { name: "", account: "", password: "", phone: "" },
      e as any
    );
  },
});
</script>
