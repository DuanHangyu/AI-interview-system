<template>
  <div class="shell">
    <header class="shell-header">
      <div class="shell-inner shell-header-inner">
        <router-link to="/" class="shell-logo" :title="BRAND.fullName">
          <img :src="logoWhite" :alt="BRAND.school + BRAND.college" class="shell-logo-img" />
          <span class="shell-product">{{ BRAND.product }}</span>
        </router-link>
        <Dropdown placement="bottomRight" :trigger="['click']">
          <button type="button" class="shell-user">
            <UserOutlined />
            <span class="shell-user-name">{{ auth.info?.name || auth.info?.account }}</span>
            <span class="shell-user-role">{{ roleLabel }}</span>
            <DownOutlined class="text-xs" />
          </button>
          <template #overlay>
            <Menu @click="onUserMenu">
              <MenuItem key="password"><LockOutlined /> 修改密码</MenuItem>
              <MenuDivider />
              <MenuItem key="logout"><LogoutOutlined /> 退出登录</MenuItem>
            </Menu>
          </template>
        </Dropdown>
      </div>
    </header>

    <nav class="shell-nav">
      <div class="shell-inner shell-nav-inner">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="shell-nav-item"
          :class="{ 'is-active': activeKey === item.path }"
        >
          {{ item.label }}
        </router-link>
      </div>
    </nav>

    <main class="shell-main">
      <router-view />
    </main>

    <footer class="shell-footer">
      <div class="shell-inner">
        版权所有：{{ BRAND.school }}{{ BRAND.college }}　地址：{{ BRAND.address }}　邮编：{{ BRAND.postcode }}　电话：{{
          BRAND.phone
        }}
      </div>
    </footer>

    <ChangePasswordModal v-model:open="passwordOpen" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import { Dropdown, Menu, MenuDivider, MenuItem } from "ant-design-vue";
import type { MenuProps } from "ant-design-vue";
import { DownOutlined, LockOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { menusFor, ROLE_LABEL } from "@/router/menus";
import ChangePasswordModal from "@/components/ChangePasswordModal.vue";
import { BRAND } from "@/config/brand";
import logoWhite from "@/assets/brand/logo-marx-white.png";

const route = useRoute();
const auth = useAuthStore();
const passwordOpen = ref(false);

const menus = computed(() => menusFor(auth.role));
const roleLabel = computed(() => ROLE_LABEL[auth.role] || "");
// 详情页（如报告）高亮其所属的一级菜单
const activeKey = computed(() => {
  const hit = menus.value.find((m) => route.path === m.path || route.path.startsWith(m.path + "/"));
  return hit?.path || (route.meta?.menu as string) || "";
});

const onUserMenu: MenuProps["onClick"] = async ({ key }) => {
  if (key === "password") {
    passwordOpen.value = true;
    return;
  }
  await auth.logout();
  window.location.href = "/login";
};
</script>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--c-bg);
}

.shell-inner {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 24px;
}

.shell-header {
  background: var(--c-primary);
  color: #fff;
}

.shell-header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-h);
}

.shell-logo {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
  color: #fff;
  text-decoration: none;
}

.shell-logo-img {
  display: block;
  height: 42px;
  width: auto;
}

.shell-product {
  padding-left: 20px;
  border-left: 1px solid rgba(255, 255, 255, 0.5);
  font-size: 20px;
  line-height: 28px;
  letter-spacing: 2px;
  white-space: nowrap;
}

.shell-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border: 0;
  background: transparent;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.shell-user:hover .shell-user-name {
  text-decoration: underline;
}

.shell-user-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shell-user-role {
  color: rgba(255, 255, 255, 0.75);
}

.shell-user-role::before {
  content: "（";
}

.shell-user-role::after {
  content: "）";
}

.shell-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #fff;
  border-bottom: 1px solid var(--c-border);
}

.shell-nav-inner {
  display: flex;
  height: var(--nav-h);
  overflow-x: auto;
}

.shell-nav-item {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  padding: 0 24px;
  border-bottom: 3px solid transparent;
  font-size: 15px;
  color: var(--c-text);
  text-decoration: none;
}

.shell-nav-item:first-child {
  margin-left: -24px;
}

.shell-nav-item:hover {
  color: var(--c-primary);
}

.shell-nav-item.is-active {
  border-bottom-color: var(--c-primary);
  color: var(--c-primary);
  font-weight: 600;
}

.shell-main {
  flex: 1;
}

.shell-footer {
  padding: 18px 0;
  background: #00318c;
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

@media (max-width: 640px) {
  .shell-inner {
    padding: 0 12px;
  }
  .shell-header-inner {
    height: 56px;
  }
  .shell-logo-img {
    height: 30px;
  }
  .shell-product,
  .shell-user-name,
  .shell-user-role {
    display: none;
  }
  .shell-nav-item {
    padding: 0 14px;
  }
  .shell-nav-item:first-child {
    margin-left: -14px;
  }
}
</style>
