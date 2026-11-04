<template>
  <div class="user-online-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>在线用户</span>
        </div>
      </template>
      <el-table :data="userOnlineList" v-loading="loading" header-cell-class-name="header-cell">
        <el-table-column prop="token" label="会话编号" align="center">
          <template #default="scope">
            <el-tooltip
              class="box-item"
              effect="dark"
              :content="scope.row.token"
              placement="top"
            >
              <el-text truncated>{{scope.row.token}}</el-text>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" align="center"/>
        <el-table-column prop="ipaddr" label="登录IP" align="center"/>
        <el-table-column prop="loginLocation" label="登录地点" align="center"/>
        <el-table-column prop="browser" label="浏览器" align="center"/>
        <el-table-column prop="os" label="操作系统" align="center"/>
        <el-table-column prop="loginDateTime" label="登录时间" align="center" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button
              v-permission="['monitor:online:forceLogout']"
              type="primary"
              size="small"
              text
              :icon="Delete"
              @click="handleForceOffline(scope.row)"
            >
              强制下线
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
//import
import { ref, onMounted } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { UserOnlineVO } from '@/type/types';
import { getUserOnlineList, forceOffline } from '@/api/api';

//data
const loading = ref<boolean>(false);
const userOnlineList = ref<UserOnlineVO[]>([] as UserOnlineVO[]);

//methods
onMounted(() => {
  getList();
});

//获取在线用户列表
const getList = () => {
  loading.value = true;
  getUserOnlineList().then(res => {
    userOnlineList.value = res.data.data;
  }).catch(e=>{
    console.log(e);
  }).finally(() => {
    loading.value = false;
  });
}
//强制下线
const handleForceOffline = (row: UserOnlineVO) => {
  ElMessageBox.confirm(
    `是否确认强退名称为 "${row.username}" 的用户？`,
    '强退确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    forceOffline(row.token).then(res => {
      ElMessage.success(res.data.msg);
      getList();
    }).catch(e => {
      console.log(e);
    });
  })
}
</script>

<style scoped>
.user-online-container {
  padding: 20px;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: var(--ym-table-header);
  color: var(--ym-table-heading);
  height: 40px;
  font-size: 13px;
  text-align: center;
}

:deep(.el-table__header-wrapper th *){
  font-weight: bold;
}
</style>
