<template>
  <div class="header-container">
    <n-flex justify="space-between" align="center" style="height: 100%;">
      <n-flex :size="15" align="center" style="flex: 1;min-width: 0;">
        <el-button 
          :icon="appStore.isCollapse ? 'Expand' : 'Fold'"
          type="text" 
          @click="handleCollapse"
          style="font-size: 25px;"
          />
        <el-breadcrumb class="breadcrumb">
          <el-breadcrumb-item v-for="item in route.matched.filter((item) => item.meta.title)" :key="item.path" :to="item.path">
            {{ item.meta.title }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </n-flex>
      <!-- 用户信息 -->
      <n-flex :size="15" align="center" style="flex-shrink: 0;">
        <Avatar :size="40" :src="user.avatar" :text="user.username"/>
        <el-dropdown trigger="click" placement="bottom">
          <n-flex align="center" style="cursor: pointer;">
            <span style="font-size: 17px;font-weight: bold;margin: 0 4px;">{{ user.username }}</span>
            <el-icon class="el-icon--right">
              <arrow-down />
            </el-icon>
          </n-flex>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="LoginOut">退出登录</el-dropdown-item>
              <!-- 返回客户端 -->
              <el-dropdown-item @click="toClientHome">返回客户端</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </n-flex>
    </n-flex>
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent } from 'vue';
import { ArrowDown } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useAppStore } from '@/stores/app';
import { storeToRefs } from 'pinia';
const Avatar=defineAsyncComponent(()=>import('@/components/common/Avatar.vue'));

//data
const appStore=useAppStore();
const router=useRouter();
const route = useRoute();
const userStore=useUserStore();
const { user }=storeToRefs(userStore);

//methods
//菜单折叠
const handleCollapse=()=>{
  appStore.isCollapse = !appStore.isCollapse;
}
//退出登录
const LoginOut=()=>{
  userStore.doLogout();
}
//返回客户端
const toClientHome=()=>{
  router.push('/');
}
</script>

<style scoped>
.header-container{
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.breadcrumb {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>