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
    <n-flex class="menu-brand" justify="center" align="center">
      <el-image class="menu-logo" alt="YMOJ" :src="LogoImage" />
    </n-flex>
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
import LogoImage from '@/assets/image/logo.jpg';

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
  padding: 15px 0;
}
.menu-logo {
  width: 50px;
  height: 50px;
}
</style>