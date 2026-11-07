<template>
  <div>
    <el-card class="box-card" style="margin-top: 20px;">
      <el-collapse v-model="activeName" accordion v-if="userStore.user">
        <el-collapse-item name="Account">
          <template #title>
            <el-icon class="icon"><Tools /></el-icon>账户设置
          </template>
          <component :is="Account"></component>
        </el-collapse-item>

        <el-collapse-item name="UserInfo">
          <template #title>
            <el-icon class="icon"><Tools /></el-icon>资料设置
          </template>
          <component :is="UserInfo"></component>
        </el-collapse-item>
      </el-collapse>
      <el-skeleton v-else style="width: 100%;" :rows="10" />
    </el-card>
  </div>  
  
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import Account from './child/account.vue';
import UserInfo from './child/userInfo.vue';
import { useUserStore } from '@/stores/user';

const userStore=useUserStore();
const activeName = ref<string>('UserInfo');
</script>
<style scoped>
.icon{
  margin-right: 5px;
}
@media screen and (min-width: 1200px) {
  .box-card {
    margin-left: 10%;
    margin-right: 10%;
  }
}
:deep(.el-collapse-item__header)  {
  border-radius: 4px;
  font-size: 18px;
  color: var(--ym-link);
}
</style>
