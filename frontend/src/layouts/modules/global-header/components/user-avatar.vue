<script setup lang="ts">
import { computed } from 'vue';
import type { VNode } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useRouterPush } from '@/hooks/common/router';
import { useSvgIcon } from '@/hooks/common/icon';
import { $t } from '@/locales';

defineOptions({
  name: 'UserAvatar'
});

const authStore = useAuthStore();
const knowledgeBaseStore = useKnowledgeBaseStore();
const { routerPushByKey, toLogin } = useRouterPush();
const { SvgIconVNode } = useSvgIcon();

function loginOrRegister() {
  toLogin();
}

type DropdownKey = 'logout';

type DropdownOption =
  | {
      key: DropdownKey;
      label: string;
      icon?: () => VNode;
    }
  | {
      type: 'divider';
      key: string;
    };

const options = computed(() => {
  const opts: DropdownOption[] = [
    {
      label: $t('common.logout'),
      key: 'logout',
      icon: SvgIconVNode({ icon: 'ph:sign-out', fontSize: 18 })
    }
  ];

  return opts;
});

function logout() {
  window.$dialog?.info({
    title: $t('common.tip'),
    content: $t('common.logoutConfirm'),
    positiveText: $t('common.confirm'),
    negativeText: $t('common.cancel'),
    onPositiveClick: () => {
      authStore.resetStore();
      knowledgeBaseStore.$reset();
    }
  });
}

function handleDropdown(key: DropdownKey) {
  if (key === 'logout') {
    logout();
  } else {
    // If your other options are jumps from other routes, they will be directly supported here
    routerPushByKey(key);
  }
}
</script>

<template>
  <NButton v-if="!authStore.isLogin" quaternary class="login-btn" @click="loginOrRegister">
    {{ $t('page.login.common.loginOrRegister') }}
  </NButton>
  <NDropdown v-else placement="bottom" trigger="click" :options="options" class="user-dropdown" @select="handleDropdown">
    <div class="user-wrapper">
      <ButtonIcon class="user-btn">
        <SvgIcon icon="ph:user-circle" class="user-icon" />
        <span class="username">{{ authStore.userInfo.username }}</span>
      </ButtonIcon>
    </div>
  </NDropdown>
</template>

<style scoped>
.login-btn {
  border-radius: 10px !important;
  font-weight: 500 !important;
  transition: all 0.3s ease !important;
}

.login-btn:hover {
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.08) 0%, rgba(240, 147, 251, 0.08) 100%) !important;
}

.user-wrapper {
  cursor: pointer;
}

.user-btn {
  padding: 6px 12px !important;
  border-radius: 12px !important;
  transition: all 0.3s ease !important;
}

.user-btn:hover {
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.06) 0%, rgba(240, 147, 251, 0.06) 100%) !important;
}

.user-icon {
  font-size: 22px;
  color: #f5576c;
}

.username {
  font-size: 15px;
  font-weight: 500;
  margin-left: 6px;
  color: #2d3748;
}

.user-dropdown :deep(.n-dropdown-menu) {
  background: rgba(255, 255, 255, 0.95) !important;
  backdrop-filter: blur(10px);
  border-radius: 12px !important;
  border: 1px solid rgba(245, 87, 108, 0.08) !important;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1) !important;
  overflow: hidden;
}

.user-dropdown :deep(.n-dropdown-option) {
  padding: 10px 16px !important;
  transition: all 0.2s ease !important;
}

.user-dropdown :deep(.n-dropdown-option:hover) {
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.06) 0%, rgba(240, 147, 251, 0.06) 100%) !important;
}
</style>
