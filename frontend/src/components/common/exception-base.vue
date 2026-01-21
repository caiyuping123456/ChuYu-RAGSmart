<script lang="ts" setup>
import { computed } from 'vue';
import { useRouterPush } from '@/hooks/common/router';
import { $t } from '@/locales';

defineOptions({ name: 'ExceptionBase' });

type ExceptionType = '403' | '404' | '500';

interface Props {
  /**
   * Exception type
   *
   * - 403: no permission
   * - 404: not found
   * - 500: service error
   */
  type: ExceptionType;
}

const props = defineProps<Props>();

const { routerPushByKey } = useRouterPush();

const iconMap: Record<ExceptionType, string> = {
  '403': 'no-permission',
  '404': 'not-found',
  '500': 'service-error'
};

const icon = computed(() => iconMap[props.type]);
</script>

<template>
  <div class="exception-container">
    <div class="icon-wrapper">
      <SvgIcon :local-icon="icon" class="exception-icon" />
    </div>
    <NButton type="primary" class="home-btn" @click="routerPushByKey('root')">{{ $t('common.backToHome') }}</NButton>
  </div>
</template>

<style scoped lang="scss">
.exception-container {
  width: 100%;
  height: 100%;
  min-height: 520px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  overflow: hidden;
  background: #ffffff;
}

.icon-wrapper {
  display: flex;
  font-size: 400px;
  color: #f5576c;
  opacity: 0.9;
  filter: drop-shadow(0 8px 32px rgba(245, 87, 108, 0.2));
}

.exception-icon {
  transition: transform 0.3s ease;
}

.exception-icon:hover {
  transform: scale(1.05);
}

.home-btn {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  border: none !important;
  border-radius: 12px !important;
  padding: 12px 32px !important;
  font-size: 16px !important;
  font-weight: 500 !important;
  transition: all 0.3s ease;
}

.home-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(245, 87, 108, 0.3) !important;
}
</style>
