<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { SimpleScrollbar } from '@sa/materials';
import { GLOBAL_SIDER_MENU_ID } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { useRouteStore } from '@/store/modules/route';
import { useRouterPush } from '@/hooks/common/router';
import { useMenu } from '../../../context';

defineOptions({
  name: 'VerticalMenu'
});

const route = useRoute();
const appStore = useAppStore();
const themeStore = useThemeStore();
const routeStore = useRouteStore();
const { routerPushByKeyWithMetaQuery } = useRouterPush();
const { selectedKey } = useMenu();

const inverted = computed(() => !themeStore.darkMode && themeStore.sider.inverted);

const expandedKeys = ref<string[]>([]);

function updateExpandedKeys() {
  if (appStore.siderCollapse || !selectedKey.value) {
    expandedKeys.value = [];
    return;
  }
  expandedKeys.value = routeStore.getSelectedMenuKeyPath(selectedKey.value);
}

watch(
  () => route.name,
  () => {
    updateExpandedKeys();
  },
  { immediate: true }
);
</script>

<template>
  <Teleport :to="`#${GLOBAL_SIDER_MENU_ID}`">
    <SimpleScrollbar class="menu-scrollbar relative">
      <NMenu
        v-model:expanded-keys="expandedKeys"
        mode="vertical"
        :value="selectedKey"
        :collapsed="appStore.siderCollapse"
        :collapsed-width="themeStore.sider.collapsedWidth"
        :collapsed-icon-size="22"
        :options="routeStore.menus"
        :inverted="inverted"
        :indent="18"
        class="vertical-menu"
        @update:value="routerPushByKeyWithMetaQuery"
      />
      <MenuToggler
        v-if="!appStore.isMobile"
        class="menu-toggler absolute bottom-0 w-full"
        :collapsed="appStore.siderCollapse"
        @click="appStore.toggleSiderCollapse"
      />
    </SimpleScrollbar>
  </Teleport>
</template>

<style scoped lang="scss">
.menu-scrollbar {
  height: 100%;
}

.vertical-menu {
  :deep(.n-menu-item) {
    margin: 4px 8px !important;
    border-radius: 12px !important;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
  }

  :deep(.n-menu-item:hover) {
    background: linear-gradient(135deg, rgba(245, 87, 108, 0.08) 0%, rgba(240, 147, 251, 0.08) 100%) !important;
  }

  :deep(.n-menu-item.n-menu-item--selected) {
    background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
    box-shadow: 0 4px 12px rgba(245, 87, 108, 0.25) !important;
  }

  :deep(.n-menu-item.n-menu-item--selected .n-menu-item-content__icon),
  :deep(.n-menu-item.n-menu-item--selected .n-menu-item-content-header) {
    color: #ffffff !important;
  }

  :deep(.n-menu-item-content) {
    padding: 0 12px !important;
    border-radius: 12px !important;
  }

  :deep(.n-menu-item-content__icon) {
    font-size: 20px !important;
    transition: all 0.3s ease !important;
  }

  :deep(.n-menu-item-content-header) {
    font-weight: 500 !important;
    letter-spacing: 0.3px;
  }

  :deep(.n-submenu-children .n-menu-item) {
    margin: 2px 8px !important;
  }

  :deep(.n-submenu-children) {
    background: rgba(245, 87, 108, 0.02) !important;
    border-radius: 12px !important;
    margin: 4px 0 !important;
  }
}

.menu-toggler {
  padding: 12px !important;
  border-radius: 12px !important;
  margin: 8px !important;
  transition: all 0.3s ease !important;
}

.menu-toggler:hover {
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.06) 0%, rgba(240, 147, 251, 0.06) 100%) !important;
}
</style>
