<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { mixColor } from '@sa/color';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);

const bgColor = computed(() => {
  const ratio = themeStore.darkMode ? 0.9 : 0;

  return mixColor('#fff', '#000', ratio);
});
</script>

<template>
  <div class="login-container">
    <!-- Animated background -->
    <div class="bg-gradient">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="gradient-orb orb-4"></div>
    </div>

    <!-- Floating decorative shapes -->
    <div class="floating-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
      <div class="shape shape-4"></div>
      <div class="shape shape-5"></div>
    </div>

    <!-- Main card -->
    <NCard :bordered="false" class="glass-card">
      <div class="card-content">
        <header class="header-section">
          <div class="logo-wrapper">
            <SystemLogo class="logo-icon" />
          </div>
          <h1 class="title-text">{{ $t('system.title') }}</h1>
          <div class="controls-wrapper">
            <ThemeSchemaSwitch
              :theme-schema="themeStore.themeScheme"
              :show-tooltip="false"
              class="control-btn"
              @switch="themeStore.toggleThemeScheme"
            />
            <LangSwitch
              v-if="themeStore.header.multilingual.visible"
              :lang="appStore.locale"
              :lang-options="appStore.localeOptions"
              :show-tooltip="false"
              @change-lang="appStore.changeLocale"
            />
          </div>
        </header>
        <main class="main-section">
          <h2 class="module-title">{{ $t(activeModule.label) }}</h2>
          <div class="form-wrapper">
            <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
              <component :is="activeModule.component" />
            </Transition>
          </div>
        </main>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.login-container {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}

/* Animated gradient background */
.bg-gradient {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.6;
  animation: float 20s infinite ease-in-out;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: linear-gradient(180deg, #e94560 0%, #ff6b9d 100%);
  top: -200px;
  left: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: linear-gradient(180deg, #0f3460 0%, #533483 100%);
  bottom: -150px;
  right: -100px;
  animation-delay: -5s;
}

.orb-3 {
  width: 400px;
  height: 400px;
  background: linear-gradient(180deg, #16213e 0%, #00d9ff 100%);
  top: 50%;
  left: 60%;
  animation-delay: -10s;
}

.orb-4 {
  width: 350px;
  height: 350px;
  background: linear-gradient(180deg, #e94560 0%, #533483 100%);
  bottom: 20%;
  left: 10%;
  animation-delay: -15s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  25% {
    transform: translate(50px, -80px) scale(1.1);
  }
  50% {
    transform: translate(-30px, 60px) scale(0.95);
  }
  75% {
    transform: translate(-60px, -40px) scale(1.05);
  }
}

/* Floating decorative shapes */
.floating-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.shape {
  position: absolute;
  border: 1px solid rgba(255, 255, 255, 0.1);
  animation: shapeFloat 25s infinite linear;
}

.shape-1 {
  width: 80px;
  height: 80px;
  top: 15%;
  left: 10%;
  border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%;
  animation-delay: 0s;
}

.shape-2 {
  width: 60px;
  height: 60px;
  top: 70%;
  left: 5%;
  border-radius: 50%;
  animation-delay: -5s;
}

.shape-3 {
  width: 100px;
  height: 100px;
  top: 20%;
  right: 8%;
  transform: rotate(45deg);
  animation-delay: -10s;
}

.shape-4 {
  width: 50px;
  height: 50px;
  bottom: 15%;
  right: 15%;
  border-radius: 20%;
  animation-delay: -15s;
}

.shape-5 {
  width: 70px;
  height: 70px;
  top: 50%;
  right: 25%;
  border-radius: 40% 60% 60% 40% / 60% 40% 60% 40%;
  animation-delay: -20s;
}

@keyframes shapeFloat {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
    opacity: 0.3;
  }
  50% {
    transform: translateY(-30px) rotate(180deg);
    opacity: 0.6;
  }
}

/* Glass card */
.glass-card {
  position: relative;
  z-index: 10;
  width: auto;
  background: rgba(255, 255, 255, 0.08) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  border-radius: 24px !important;
  box-shadow:
    0 25px 50px -12px rgba(0, 0, 0, 0.4),
    0 0 0 1px rgba(255, 255, 255, 0.05) inset,
    0 0 80px rgba(233, 69, 96, 0.1);
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.glass-card:hover {
  box-shadow:
    0 30px 60px -15px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset,
    0 0 100px rgba(233, 69, 96, 0.15);
  transform: translateY(-2px);
}

:deep(.n-card__content) {
  padding: 48px !important;
}

.card-content {
  width: 400px;
}

@media (max-width: 480px) {
  .card-content {
    width: 320px;
  }

  :deep(.n-card__content) {
    padding: 32px !important;
  }
}

/* Header section */
.header-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.logo-wrapper {
  position: relative;
}

.logo-icon {
  font-size: 56px;
  color: #e94560;
  filter: drop-shadow(0 0 20px rgba(233, 69, 96, 0.5));
  animation: logoGlow 3s infinite ease-in-out;
}

@keyframes logoGlow {
  0%, 100% {
    filter: drop-shadow(0 0 20px rgba(233, 69, 96, 0.5));
  }
  50% {
    filter: drop-shadow(0 0 35px rgba(233, 69, 96, 0.8));
  }
}

.title-text {
  font-size: 28px;
  font-weight: 600;
  background: linear-gradient(135deg, #ffffff 0%, #e94560 50%, #00d9ff 100%);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: gradientShift 5s ease infinite;
  letter-spacing: 1px;
}

@keyframes gradientShift {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

.controls-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.control-btn {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.7);
  transition: all 0.3s ease;
}

.control-btn:hover {
  color: #e94560;
  transform: scale(1.1);
}

/* Main section */
.main-section {
  padding-top: 24px;
}

.module-title {
  font-size: 20px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 24px;
  position: relative;
  padding-bottom: 12px;
}

.module-title::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 40px;
  height: 3px;
  background: linear-gradient(90deg, #e94560, #00d9ff);
  border-radius: 2px;
}

.form-wrapper {
  padding-top: 24px;
}

/* Dark mode adjustments */
:global(.dark) .login-container {
  background: linear-gradient(135deg, #0a0a0f 0%, #0f0f1a 50%, #0a1628 100%);
}

:global(.dark) .glass-card {
  background: rgba(0, 0, 0, 0.3) !important;
  border-color: rgba(255, 255, 255, 0.08) !important;
}
</style>
