<template>
  <div class="flex items-center justify-between">
    <section class="flex items-center space-x-[20px] overflow-hidden">
      <img
        src="@/assets/company.png"
        alt=""
        class="w-[180px] h-[60px] scale-y-[3] translate-x-[-20px] cursor-pointer"
        @click="$router.push('/')"
      />
      <span
        class="text-[rgba(0,0,0,0.88)] text-lg font-semibold translate-x-[-20px]"
      >
        您好！欢迎回来
      </span>
    </section>
    <div class="flex items-center space-x-[6px]">
      <img src="@/assets/defaultAvatar.png" class="size-10" alt="" />
      <Dropdown placement="bottomRight" trigger="hover" :arrow="false">
        <section
          class="text-[#1677FF] text-sm font-semibold flex items-center space-x-1 !cursor-pointer pl-2 pr-1 py-[5px] hover:bg-[rgba(255,255,255,0.5)] rounded-lg focus-within:outline-none"
        >
          <span>{{ authStore?.info?.name }}</span>
          <img src="@/assets/arrow-down.png" class="size-6 mb-[2px]" alt="" />
        </section>
        <template #overlay>
          <Menu class="w-[150px]" @click="commandFn">
            <MenuItem key="1"> 修改密码 </MenuItem>
            <MenuItem key="2"> 退出登录 </MenuItem>
          </Menu>
        </template>
      </Dropdown>
    </div>
    <UpdatePassword ref="updatePass" />
  </div>
</template>
<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { Dropdown, Menu, MenuItem, MenuProps } from "ant-design-vue";
import UpdatePassword from "./UpdatePassword.vue";
import { ref } from "vue";

defineOptions({ name: "Header" });

const authStore = useAuthStore();
const updatePass = ref();

const commandFn: MenuProps["onClick"] = ({ key }) => {
  if (key == 1) {
    updatePass.value?.openModal();
  } else {
    authStore.logout();
    window.location.href = "/login";
  }
};
</script>
