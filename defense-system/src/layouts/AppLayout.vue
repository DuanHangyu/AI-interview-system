<template>
  <div class="shell">
    <header class="shell-header">
      <router-link to="/" class="shell-logo" :title="BRAND.fullName">
        <img :src="logoWhite" :alt="BRAND.school + BRAND.college" class="shell-logo-img" />
        <span class="shell-divider"></span>
        <span class="shell-product">
          <b>{{ BRAND.product }}</b>
          <small>AI INTERVIEW SYSTEM</small>
        </span>
      </router-link>
      <div class="shell-header-right">
        <span class="role-tag">{{ roleLabel }}</span>
        <Dropdown placement="bottomRight" :trigger="['click']">
          <button type="button" class="shell-user">
            <Avatar :size="30" class="shell-avatar">{{ initial }}</Avatar>
            <span class="shell-user-name">{{ auth.info?.name || auth.info?.account }}</span>
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

    <aside class="shell-sider">
      <div class="shell-sider-title">{{ roleLabel }}工作台</div>
      <Menu mode="inline" :selected-keys="[activeKey]" class="shell-menu" @click="onNav">
        <MenuItem v-for="item in menus" :key="item.path">
          <template #icon><component :is="item.icon" /></template>
          {{ item.label }}
        </MenuItem>
      </Menu>
      <div class="shell-sider-foot">
        <img :src="emblemRed" alt="" class="shell-sider-emblem" />
        <div>
          <div class="shell-sider-college">{{ BRAND.college }}</div>
          <div class="shell-sider-motto">{{ BRAND.motto }}</div>
        </div>
      </div>
    </aside>

    <!-- 窄屏时侧边栏隐藏，改为顶部横向导航 -->
    <nav class="shell-tabs">
      <router-link
        v-for="item in menus"
        :key="item.path"
        :to="item.path"
        class="shell-tab"
        :class="{ 'is-active': activeKey === item.path }"
      >
        <component :is="item.icon" />
        <span>{{ item.label }}</span>
      </router-link>
    </nav>

    <main class="shell-main">
      <router-view />
    </main>

    <ChangePasswordModal v-model:open="passwordOpen" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Avatar, Dropdown, Menu, MenuDivider, MenuItem } from "ant-design-vue";
import type { MenuProps } from "ant-design-vue";
import { DownOutlined, LockOutlined, LogoutOutlined } from "@ant-design/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { menusFor, ROLE_LABEL } from "@/router/menus";
import ChangePasswordModal from "@/components/ChangePasswordModal.vue";
import { BRAND } from "@/config/brand";
import logoWhite from "@/assets/brand/logo-marx-white.png";
import emblemRed from "@/assets/brand/emblem-red.svg";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const passwordOpen = ref(false);

const menus = computed(() => menusFor(auth.role));
const roleLabel = computed(() => ROLE_LABEL[auth.role] || "");
const initial = computed(() => String(auth.info?.name || auth.info?.account || "U").slice(-1));
// 详情页（如报告）高亮其所属的一级菜单
const activeKey = computed(() => {
  const hit = menus.value.find((m) => route.path === m.path || route.path.startsWith(m.path + "/"));
  return hit?.path || (route.meta?.menu as string) || "";
});

const onNav: MenuProps["onClick"] = ({ key }) => router.push(String(key));

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
  min-height: 100vh;
  background: var(--c-bg);
}

.shell-header {
  position: fixed;
  inset: 0 0 auto 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-h);
  padding: 0 24px;
  color: #fff;
  /* 与学院官网页眉一致的学院红，叠加一层斜向丝带光泽 */
  background:
    linear-gradient(115deg, transparent 0 58%, rgba(255, 255, 255, 0.07) 58% 64%, transparent 64% 70%,
      rgba(255, 255, 255, 0.05) 70% 73%, transparent 73%),
    linear-gradient(90deg, #a5101e 0%, #c3192a 45%, #b5141f 100%);
  box-shadow: 0 2px 10px rgba(120, 10, 20, 0.25);
}

.shell-header::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--c-gold) 30%, var(--c-gold) 70%, transparent);
  opacity: 0.55;
}

.shell-logo {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
  color: #fff;
  text-decoration: none;
}

.shell-logo-img {
  display: block;
  height: 36px;
  width: auto;
}

.shell-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.45);
}

.shell-product {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  white-space: nowrap;
}

.shell-product b {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
}

.shell-product small {
  margin-top: 3px;
  font-size: 10px;
  letter-spacing: 1.5px;
  color: rgba(255, 255, 255, 0.7);
}

.shell-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag {
  padding: 2px 10px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 12px;
  font-size: 12px;
  line-height: 18px;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
}

.shell-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border: 0;
  border-radius: 20px;
  background: transparent;
  cursor: pointer;
  color: #fff;
  transition: background 0.2s;
}

.shell-user:hover {
  background: rgba(255, 255, 255, 0.14);
}

.shell-avatar {
  background: #fff;
  color: var(--c-primary);
  font-size: 13px;
  font-weight: 600;
}

.shell-user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shell-sider {
  position: fixed;
  top: var(--header-h);
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  width: var(--sider-w);
  padding: 20px 12px 16px;
  background: var(--c-surface);
  border-right: 1px solid var(--c-border-2);
}

.shell-sider-title {
  padding: 0 12px 10px;
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--c-text-3);
}

.shell-menu {
  flex: 1;
  border-inline-end: 0 !important;
}

.shell-menu :deep(.ant-menu-item-selected) {
  position: relative;
  font-weight: 500;
}

.shell-menu :deep(.ant-menu-item-selected)::before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--c-primary);
}

.shell-sider-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 4px;
  padding: 14px 12px;
  border-radius: 10px;
  background: linear-gradient(135deg, #fbf3ef 0%, #f8eee4 100%);
}

.shell-sider-emblem {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
}

.shell-sider-college {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-primary-deep);
}

.shell-sider-motto {
  margin-top: 2px;
  font-size: 11px;
  letter-spacing: 1px;
  color: #a87a35;
}

.shell-main {
  min-height: 100vh;
  padding-top: var(--header-h);
  padding-left: var(--sider-w);
}

.shell-tabs {
  display: none;
}

@media (max-width: 900px) {
  .shell-sider {
    display: none;
  }
  .shell-main {
    padding-top: calc(var(--header-h) + 46px);
    padding-left: 0;
  }
  .shell-tabs {
    position: fixed;
    top: var(--header-h);
    left: 0;
    right: 0;
    z-index: 19;
    display: flex;
    gap: 4px;
    height: 46px;
    padding: 0 8px;
    overflow-x: auto;
    background: var(--c-surface);
    border-bottom: 1px solid var(--c-border-2);
    box-shadow: var(--shadow-1);
  }
  .shell-tab {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    gap: 6px;
    padding: 0 12px;
    border-bottom: 2px solid transparent;
    font-size: 14px;
    color: var(--c-text-2);
    text-decoration: none;
  }
  .shell-tab.is-active {
    border-bottom-color: var(--c-primary);
    color: var(--c-primary);
    font-weight: 500;
  }
}

@media (max-width: 640px) {
  .shell-header {
    padding: 0 12px;
  }
  .shell-logo-img {
    height: 26px;
  }
  .shell-divider,
  .shell-product,
  .role-tag,
  .shell-user-name {
    display: none;
  }
}
</style>
