<template>
  <el-container style="height: 100vh">
    <el-aside width="64">
      <el-scrollbar>
        <AdminAside/>
      </el-scrollbar>
    </el-aside>
    <el-container>
      <el-header>
        <AdminHeader/>
      </el-header>
      <el-main>
        <el-scrollbar>
          <router-view />
          <el-backtop :right="40" :bottom="60" target=".el-scrollbar__wrap"/>
        </el-scrollbar>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { defineAsyncComponent, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { usePermissionStore } from '@/stores/permission';
const AdminHeader = defineAsyncComponent(() => import('@/components/admin/AdminHeader.vue'));
const AdminAside = defineAsyncComponent(() => import('@/components/admin/AdminAside.vue'));

//data
const permissionStore = usePermissionStore();
const route = useRoute();
const router = useRouter();

watch(
  () => [permissionStore.isRoutesLoaded, route.path],
  ([routesLoaded, currentPath]) => {
    if (routesLoaded) {
      //检查当前路径是否匹配任何已注册的路由
      const matched = router.resolve(currentPath.toString());
      if (matched.name === undefined) {
        //跳转到404页面
        router.replace('/404');
      }
    }
  },
  { immediate: true }
);
</script>

<style scoped lang="css">
.el-main{
  padding: 0;
  background-color: #eff3f5;
}
</style>