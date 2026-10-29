<template>
  <el-container class="admin-layout">
    <el-aside v-if="!isMobile" class="admin-sidebar" :width="appStore.isCollapse ? '64px' : '200px'">
      <el-scrollbar height="100%">
        <AdminAside :collapse="appStore.isCollapse" />
      </el-scrollbar>
    </el-aside>
    <el-drawer
      v-if="isMobile"
      title="后台导航"
      body-class="admin-menu-drawer-body"
      direction="ltr"
      size="min(280px, 85vw)"
      v-model="menuOpen"
      :destroy-on-close="true"
      :append-to-body="true"
    >
      <el-scrollbar height="100%">
        <AdminAside @navigate="onCloseMenu" />
      </el-scrollbar>
    </el-drawer>
    <el-container class="admin-workspace">
      <el-header class="admin-header">
        <AdminHeader :mobile="isMobile" :menu-open="menuOpen" @toggle-menu="onToggleMenu" />
      </el-header>
      <el-main class="admin-main">
        <el-scrollbar class="admin-content">
          <router-view />
          <el-backtop target=".admin-content .el-scrollbar__wrap" :right="24" :bottom="32" />
        </el-scrollbar>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAppStore } from '@/stores/app';
import { usePermissionStore } from '@/stores/permission';

const AdminHeader = defineAsyncComponent(() => import('./AdminHeader.vue'));
const AdminAside = defineAsyncComponent(() => import('./AdminAside.vue'));
const appStore = useAppStore();
const permissionStore = usePermissionStore();
const route = useRoute();
const router = useRouter();
const media = window.matchMedia('(max-width: 1023px)');
const isMobile = ref(media.matches);
const menuOpen = ref(false);

function onToggleMenu() {
  if (isMobile.value) {
    menuOpen.value = !menuOpen.value;
  } else {
    appStore.isCollapse = !appStore.isCollapse;
  }
}

function onCloseMenu() {
  menuOpen.value = false;
}

function onViewportChange(event: MediaQueryListEvent) {
  isMobile.value = event.matches;
  menuOpen.value = false;
}

watch(() => route.fullPath, onCloseMenu);
watch(
  () => [permissionStore.isRoutesLoaded, route.path] as const,
  ([routesLoaded, currentPath]) => {
    if (routesLoaded && router.resolve(currentPath).name === undefined) {
      router.replace('/404');
    }
  },
  { immediate: true }
);
onMounted(() => media.addEventListener('change', onViewportChange));
onBeforeUnmount(() => media.removeEventListener('change', onViewportChange));
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
}
.admin-sidebar {
  flex-shrink: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
}
.admin-workspace {
  min-width: 0;
  min-height: 0;
}
.admin-header {
  padding: 0 20px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}
.admin-main {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: 0;
  background: #eff3f5;
}
/* 后台列表沿用用户、题目列表的表头；列对齐仍由 el-table-column 决定。 */
.admin-main :deep(.el-table) {
  --el-table-header-bg-color: #f8f8f9;
}
.admin-main :deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: var(--el-table-header-bg-color);
  color: #515a6e;
  height: 40px;
  font-size: 13px;
}
/* 抽屉传送到 body，用专属 body-class 限定覆盖范围，滚动交给内部 el-scrollbar。 */
:global(.admin-menu-drawer-body) {
  min-height: 0;
  overflow: hidden;
}
@media (max-width: 767px) {
  .admin-header {
    padding: 0 12px;
  }
}
</style>
