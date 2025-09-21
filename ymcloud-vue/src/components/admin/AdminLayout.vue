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
import { defineAsyncComponent } from 'vue';
const AdminHeader = defineAsyncComponent(() => import('@/components/admin/AdminHeader.vue'));
const AdminAside = defineAsyncComponent(() => import('@/components/admin/AdminAside.vue'));
</script>

<style scoped lang="css">
.el-main{
  padding: 0;
  background-color: #eff3f5;
}
</style>