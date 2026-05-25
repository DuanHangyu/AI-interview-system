<template>
  <div class="flex items-center justify-between px-[24px] header">
    <div class="w-[140px] h-[54px] overflow-hidden">
      <img
        src="@/assets/company.png"
        alt=""
        class="w-[140px] h-[54px] scale-y-[2.5] translate-x-[-20px] cursor-pointer"
        @click="$router.push('/')"
      />
    </div>
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
<style scoped>
.header {
  height: 53px;
  background: rgba(255, 255, 255, 0.2);
  box-shadow: 0px 4px 24px 0px rgba(32, 33, 41, 0.05);
  border-radius: 12px 12px 12px 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}
</style>
