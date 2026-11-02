<template>
  <div class="shell">
    <header class="shell-header">
      <router-link to="/" class="shell-logo">
        <span class="shell-mark">AI</span>
        <span>AI 面试评估</span>
      </router-link>
      <div class="shell-header-right">
        <Tag :bordered="false" class="role-tag">{{ roleLabel }}</Tag>
        <Dropdown placement="bottomRight" :trigger="['click']">
          <button type="button" class="shell-user">
            <Avatar :size="28" class="shell-avatar">{{ initial }}</Avatar>
            <span class="shell-user-name">{{ auth.info?.name || auth.info?.account }}</span>
            <DownOutlined class="text-xs muted" />
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
      <Menu mode="inline" :selected-keys="[activeKey]" class="shell-menu" @click="onNav">
        <MenuItem v-for="item in menus" :key="item.path">
          <template #icon><component :is="item.icon" /></template>
          {{ item.label }}
        </MenuItem>
      </Menu>
    </aside>

    <main class="shell-main">
      <router-view />
    </main>

    <ChangePasswordModal v-model:open="passwordOpen" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Avatar, Dropdown, Menu, MenuDivider, MenuItem, Tag } from "ant-design-vue";
import type { MenuProps } from "ant-design-vue";
import { DownOutlined, LockOutlined, LogoutOutlined } from "@ant-design/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { menusFor, ROLE_LABEL } from "@/router/menus";
import ChangePasswordModal from "@/components/ChangePasswordModal.vue";

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
  padding: 0 20px;
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.shell-logo {
  display: flex;
  text-decoration: none;
  align-items: center;
  gap: 10px;
  color: var(--c-text);
  font-size: 16px;
  font-weight: 600;
}

.shell-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: var(--c-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.shell-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag {
  margin: 0;
  background: var(--c-primary-soft);
  color: var(--c-primary);
}

.shell-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  color: var(--c-text);
}

.shell-user:hover {
  background: var(--c-bg);
}

.shell-avatar {
  background: var(--c-primary);
  font-size: 13px;
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
  width: var(--sider-w);
  padding: 12px 8px;
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
}

.shell-menu {
  border-inline-end: 0 !important;
}

.shell-main {
  min-height: 100vh;
  padding-top: var(--header-h);
  padding-left: var(--sider-w);
}

@media (max-width: 900px) {
  .shell-sider {
    display: none;
  }
  .shell-main {
    padding-left: 0;
  }
}
</style>
