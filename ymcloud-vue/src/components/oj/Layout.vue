<template>
  <el-container style="height: 100vh">
    <el-header>
      <CommonHeader/>
    </el-header>
    <el-main>
      <el-scrollbar>
        <router-view />
        <el-backtop :right="40" :bottom="60" target=".el-scrollbar__wrap"/>
      </el-scrollbar>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { defineAsyncComponent } from 'vue';

const CommonHeader = defineAsyncComponent(() => import('@/components/oj/CommonHeader.vue'));
</script>

<style scoped lang="css">
.el-main{
  padding: 0;
  background-color: #eff3f5;
}
.el-header{
  padding: 0;
}
</style>
