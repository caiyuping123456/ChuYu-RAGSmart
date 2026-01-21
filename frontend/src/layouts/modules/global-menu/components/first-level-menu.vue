<script setup lang="ts">
import { computed } from 'vue';
import { createReusableTemplate } from '@vueuse/core';
import { SimpleScrollbar } from '@sa/materials';
import { transformColorWithOpacity } from '@sa/color';

defineOptions({
  name: 'FirstLevelMenu'
});

interface Props {
  menus: App.Global.Menu[];
  activeMenuKey?: string;
  inverted?: boolean;
  siderCollapse?: boolean;
  darkMode?: boolean;
  themeColor: string;
}

const props = defineProps<Props>();

interface Emits {
  (e: 'select', menu: App.Global.Menu): boolean;
  (e: 'toggleSiderCollapse'): void;
}

const emit = defineEmits<Emits>();

interface MixMenuItemProps {
  /** Menu item label */
  label: App.Global.Menu['label'];
  /** Menu item icon */
  icon: App.Global.Menu['icon'];
  /** Active menu item */
  active: boolean;
  /** Mini size */
  isMini?: boolean;
}
const [DefineMixMenuItem, MixMenuItem] = createReusableTemplate<MixMenuItemProps>();

const selectedBgColor = computed(() => {
  const { darkMode, themeColor } = props;

  const light = transformColorWithOpacity(themeColor, 0.1, '#ffffff');
  const dark = transformColorWithOpacity(themeColor, 0.3, '#000000');

  return darkMode ? dark : light;
});

function handleClickMixMenu(menu: App.Global.Menu) {
  emit('select', menu);
}

function toggleSiderCollapse() {
  emit('toggleSiderCollapse');
}
</script>

<template>
  <!-- define component: MixMenuItem -->
  <DefineMixMenuItem v-slot="{ label, icon, active, isMini }">
    <div
      class="mix-menu-item mx-4px mb-6px flex-col-center cursor-pointer rounded-12px bg-transparent px-4px py-8px transition-300"
      :class="{
        'active-menu': active,
        'inverted-menu': inverted
      }"
    >
      <component :is="icon" :class="[isMini ? 'text-icon-small' : 'text-icon-large']" />
      <p
        class="w-full ellipsis-text text-center text-12px transition-height-300"
        :class="[isMini ? 'h-0 pt-0' : 'h-20px pt-4px']"
      >
        {{ label }}
      </p>
    </div>
  </DefineMixMenuItem>
  <!-- define component end: MixMenuItem -->

  <div class="first-level-menu h-full flex-col-stretch flex-1-hidden">
    <slot></slot>
    <SimpleScrollbar>
      <MixMenuItem
        v-for="menu in menus"
        :key="menu.key"
        :label="menu.label"
        :icon="menu.icon"
        :active="menu.key === activeMenuKey"
        :is-mini="siderCollapse"
        @click="handleClickMixMenu(menu)"
      />
    </SimpleScrollbar>
    <MenuToggler
      arrow-icon
      :collapsed="siderCollapse"
      :z-index="99"
      class="menu-toggler"
      :class="{ 'text-white:88 !hover:text-white': inverted }"
      @click="toggleSiderCollapse"
    />
  </div>
</template>

<style scoped>
.first-level-menu {
  position: relative;
}

.mix-menu-item {
  background: transparent;
}

.mix-menu-item:hover {
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.08) 0%, rgba(240, 147, 251, 0.08) 100%);
}

.active-menu {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  box-shadow: 0 4px 12px rgba(245, 87, 108, 0.3);
  color: #ffffff !important;
}

.active-menu.inverted-menu {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
}

.inverted-menu {
  color: rgba(255, 255, 255, 0.65);
}

.inverted-menu:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.selected-mix-menu {
  background-color: v-bind(selectedBgColor);
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
