<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'GlobalLogo'
});

interface Props {
  /** Whether to show the title */
  showTitle?: boolean;
}

withDefaults(defineProps<Props>(), {
  showTitle: true
});
</script>

<template>
  <RouterLink to="/" class="logo-wrapper w-full flex-center nowrap-hidden">
    <SystemLogo class="logo-icon" />
    <h2 v-show="showTitle" class="logo-title">
      {{ $t('system.title') }}
    </h2>
  </RouterLink>
</template>

<style scoped>
.logo-wrapper {
  padding: 0 16px;
  height: 100%;
  transition: all 0.3s ease;
}

.logo-icon {
  font-size: 36px;
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  filter: drop-shadow(0 2px 8px rgba(245, 87, 108, 0.3));
  animation: logoFloat 3s ease-in-out infinite;
}

@keyframes logoFloat {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

.logo-title {
  margin-left: 10px;
  font-size: 17px;
  font-weight: 700;
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 50%, #667eea 100%);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: titleGradient 5s ease infinite;
  letter-spacing: 1px;
  transition: all 0.3s ease;
}

@keyframes titleGradient {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}
</style>
