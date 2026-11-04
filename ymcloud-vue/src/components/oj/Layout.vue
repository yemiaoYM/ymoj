<template>
  <el-container class="oj-layout">
    <el-header>
      <CommonHeader/>
    </el-header>
    <el-main>
      <el-scrollbar class="oj-content" height="100%">
        <router-view />
        <el-backtop target=".oj-content > .el-scrollbar__wrap" :right="40" :bottom="60"/>
      </el-scrollbar>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { defineAsyncComponent } from 'vue';

const CommonHeader = defineAsyncComponent(() => import('@/components/oj/CommonHeader.vue'));
</script>

<style scoped lang="css">
.oj-layout {
  height: 100vh;
  height: 100dvh;
}
.el-main{
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: 0;
  background-color: var(--ym-page);
}
.el-header{
  padding: 0;
}
</style>
