<script setup lang="ts">
import { computed } from 'vue';
import { GLOBAL_SIDER_MENU_ID } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import GlobalLogo from '../global-logo/index.vue';

defineOptions({
  name: 'GlobalSider'
});

const appStore = useAppStore();
const themeStore = useThemeStore();

const isVerticalMix = computed(() => themeStore.layout.mode === 'vertical-mix');
const isHorizontalMix = computed(() => themeStore.layout.mode === 'horizontal-mix');
const darkMenu = computed(() => !themeStore.darkMode && !isHorizontalMix.value && themeStore.sider.inverted);
const showLogo = computed(() => !isVerticalMix.value && !isHorizontalMix.value);
const menuWrapperClass = computed(() => (showLogo.value ? 'flex-1-hidden' : 'h-full'));
</script>

<template>
  <DarkModeContainer class="sider-container size-full flex-col-stretch" :inverted="darkMenu">
    <GlobalLogo
      v-if="showLogo"
      :show-title="!appStore.siderCollapse"
      :style="{ height: themeStore.header.height + 'px' }"
    />
    <div :id="GLOBAL_SIDER_MENU_ID" :class="menuWrapperClass"></div>
  </DarkModeContainer>
</template>

<style scoped>
.sider-container {
  position: relative;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95) 0%, rgba(253, 245, 250, 0.95) 100%) !important;
  border-right: 1px solid rgba(245, 87, 108, 0.08) !important;
  box-shadow:
    4px 0 24px rgba(0, 0, 0, 0.03),
    0 0 40px rgba(245, 87, 108, 0.02) !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 装饰性背景光晕 */
.sider-container::before {
  content: '';
  position: absolute;
  top: 10%;
  right: -30px;
  width: 80px;
  height: 150px;
  background: linear-gradient(180deg, rgba(245, 87, 108, 0.06) 0%, rgba(240, 147, 251, 0.06) 100%);
  border-radius: 50%;
  filter: blur(25px);
  pointer-events: none;
}

.sider-container::after {
  content: '';
  position: absolute;
  bottom: 20%;
  right: -20px;
  width: 60px;
  height: 100px;
  background: linear-gradient(180deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  border-radius: 50%;
  filter: blur(20px);
  pointer-events: none;
}
</style>
