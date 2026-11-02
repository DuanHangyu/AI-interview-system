<template>
  <Modal
    :open="open"
    title="修改密码"
    :confirm-loading="saving"
    ok-text="保存"
    destroy-on-close
    @ok="save"
    @cancel="emit('update:open', false)"
  >
    <Form ref="formRef" layout="vertical" :model="form" class="pt-2">
      <FormItem
        label="新密码"
        name="password"
        :rules="[
          { required: true, message: '请输入新密码' },
          { min: 6, message: '密码至少 6 位' },
        ]"
      >
        <InputPassword v-model:value="form.password" placeholder="至少 6 位" autocomplete="new-password" />
      </FormItem>
      <FormItem label="确认新密码" name="confirm" :rules="[{ validator: checkConfirm }]">
        <InputPassword v-model:value="form.confirm" placeholder="再次输入新密码" autocomplete="new-password" />
      </FormItem>
    </Form>
  </Modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { Form, FormItem, InputPassword, Modal, message } from "ant-design-vue";
import { changePassword } from "@/api/common";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ (e: "update:open", v: boolean): void }>();

const formRef = ref();
const saving = ref(false);
const form = reactive({ password: "", confirm: "" });

watch(
  () => props.open,
  (v) => {
    if (v) Object.assign(form, { password: "", confirm: "" });
  }
);

function checkConfirm(_: unknown, value: string) {
  if (!value) return Promise.reject("请再次输入新密码");
  if (value !== form.password) return Promise.reject("两次输入的密码不一致");
  return Promise.resolve();
}

async function save() {
  await formRef.value.validate();
  saving.value = true;
  try {
    await changePassword({ password: form.password });
    message.success("密码已修改");
    emit("update:open", false);
  } finally {
    saving.value = false;
  }
}
</script>
