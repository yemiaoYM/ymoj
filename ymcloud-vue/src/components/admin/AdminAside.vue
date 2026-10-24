<template>
  <el-menu
    class="admin-menu"
    active-text-color="#319cf4"
    router
    mode="vertical"
    :default-active="route.path"
    :collapse="collapse"
    :collapse-transition="false"
    :unique-opened="true"
    @select="onSelect"
  >
    <x-flex class="menu-brand" justify="center" align="center">
      <BrandLogo class="menu-logo" :class="{ 'is-collapsed': collapse }" :compact="collapse" />
    </x-flex>
    <el-menu-item index="/admin/index">
      <el-icon><Odometer /></el-icon>
      <template #title>仪表盘</template>
    </el-menu-item>
    <SidebarItem :item="permissionStore.sidebarRouters" />
  </el-menu>
</template>

<script setup lang="ts">
import { defineAsyncComponent } from 'vue';
import { Odometer } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';
import { usePermissionStore } from '@/stores/permission';
import BrandLogo from '@/components/common/BrandLogo.vue';

withDefaults(defineProps<{ collapse?: boolean }>(), { collapse: false });
const emit = defineEmits<{ navigate: [] }>();
const SidebarItem = defineAsyncComponent(() => import('./SidebarItem.vue'));
const permissionStore = usePermissionStore();
const route = useRoute();

function onSelect() {
  emit('navigate');
}
</script>

<style scoped>
.admin-menu {
  width: 100%;
  min-height: 100%;
  border-right: 0;
}
.menu-brand {
  min-height: 80px;
  box-sizing: border-box;
  padding: 12px 8px;
}
.menu-logo {
  width: 160px;
}
.menu-logo.is-collapsed {
  width: 44px;
}
</style>
