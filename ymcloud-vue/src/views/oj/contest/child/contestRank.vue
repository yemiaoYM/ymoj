<template>
  <div class="container">
    <el-card>
      <el-skeleton v-if="loading" style="width: 100%;" :rows="20" />
      <x-flex v-else vertical :size="10">
        <x-flex justify="space-between">
          <!-- 查询框 -->
          <el-input
            v-model="keyword"
            placeholder="请输入查询的用户名称或者学校名称"
            clearable
            size="large"
            :prefix-icon="Search"
            style="width: 300px;"
            @keyup.enter="initContestRankList"
            @clear="initContestRankList"
          />
          <!-- 自动刷新 -->
          <span>
            自动刷新(10s)
            <el-switch v-model="autoRefresh"/>
          </span>
        </x-flex>
        
        <!-- 排名列表信息 -->
        <el-table
          :data="tableData.userRankData"
          style="width: 100%"
          border
          stripe
          highlight-current-row
        >
          <el-table-column prop="rank" label="排名" align="center" width="80"/>
          <el-table-column prop="username" label="用户" align="center" width="350">
            <template #default="{ row }">
              <x-flex justify="center">
                <el-tag size="large" disable-transitions color="#21c1c6" style="cursor:pointer;">
                  <span style="color: white;font-weight:bold;" @click="toUserInfo(row.username)">{{ row.username }}</span>
                </el-tag>
                <el-tag v-if="row.nickname" size="large" disable-transitions color="#409eff" style="color: white;">
                  {{ row.nickname }}
                </el-tag>
                <el-tag v-if="row.school" size="large" disable-transitions color="#fe9000" style="color: white;">
                  {{ row.school }}
                </el-tag>
              </x-flex>
            </template>
          </el-table-column>
          <el-table-column prop="ac" label="AC" align="center" width="100" />
          <el-table-column v-if="isScoreMode" prop="totalScore" label="总分数" align="center" width="150" />
          <el-table-column prop="totalTime" label="总时间" align="center" width="150" >
            <template #default="{ row }">
              {{ formatMinutes(row.totalTime) }}
            </template>
          </el-table-column>
          <el-table-column
            v-for="q in tableData.problemInfoData"
            :key="q.displayId"
            align="center"
          >
            <!-- 题目信息 -->
            <template #header>
              <x-flex justify="center">
                <el-icon :size="25" :color="q.color"><component :is="Balloon" /></el-icon>
                <span style="font-weight: bold;">{{ q.displayId }}</span>
              </x-flex>
              <span style="font-size: 12px;">({{q.ac}}/{{q.total}})</span>
            </template>
            <!-- 用户该题评测信息 -->
            <template #default="{ row }">
              <div
                v-if="row.submissionInfo[q.displayId]"
                class="problem-cell"
                :style="getSubmissionStyle(row.submissionInfo[q.displayId])"
              >
                <el-tooltip
                  effect="dark"
                  :content="formatActimeFull(row.submissionInfo[q.displayId].actime)"
                  placement="top"
                >
                  <span class="problem-cell__main">
                    {{ isScoreMode ? (row.submissionInfo[q.displayId].score ?? '--') : formatMinutes(row.submissionInfo[q.displayId].actime) }}
                  </span>
                </el-tooltip>
                <span class="problem-cell__tries">
                  {{ formatTries(row.submissionInfo[q.displayId].errorNum + row.submissionInfo[q.displayId].isAC) }}
                </span>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <!-- 分页 -->
        <x-flex justify="end" style="width: 100%;">
          <el-pagination
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[15, 30, 50]"
            layout="prev, pager, next, sizes, total"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
            background
          />
        </x-flex>
      </x-flex>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed,ref,onMounted,watch,onBeforeUnmount } from 'vue';
import { useRouter,useRoute } from 'vue-router';
import { Balloon,Search } from '@vicons/ionicons5';
import type { ContestRankTableVO } from '@/type/types';
import { getContestRankList } from '@/api/api';

//data
const router=useRouter();
const route=useRoute();
const loading=ref<boolean>(false);
const tableData=ref<ContestRankTableVO>({} as ContestRankTableVO);
const isScoreMode = computed(() => tableData.value.mode === 1 || tableData.value.mode === 2);
const contestId=ref<number>(0);
const keyword=ref<string>('');
const pageSize=ref<number>(50);
const pageNo=ref<number>(1);
const total=ref<number>(0);
const autoRefresh=ref<boolean>(false);
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null;

//methods
onMounted(()=>{
  loading.value=true;
  contestId.value=Number(route.params.contestID);
  console.log('contestId:', contestId.value);
  //获取比赛排名列表
  initContestRankList();
})

onBeforeUnmount(() => {
  stopAutoRefresh();
})

//获取比赛排名列表
const initContestRankList=()=>{
  loading.value=true;
  getContestRankList({
    contestId:contestId.value,
    keyword:keyword.value,
    pageNo:pageNo.value,
    pageSize:pageSize.value
  }).then(res=>{
    tableData.value=res.data.data;
    total.value=res.data.data.total;
  }).catch(e=>{
    console.log(e);
  }).finally(()=>{
    loading.value=false;
  }) 
}
//分页条变化
const handlePageChange = (newPage: number) => {
  pageNo.value=newPage;
  initContestRankList();
};
//每页条数变化
const handlePageSizeChange = (newPageSize: number) => {
  pageSize.value=newPageSize;
  initContestRankList();
};
//题目单元格样式
const getSubmissionStyle = (info: { isAC: boolean; isFirstAC: boolean }) => {
  if(!info){
    return {};
  }
  if(info.isFirstAC){
    return {
      backgroundColor: '#ffe08a',
      color: '#5c3b00'
    }
  }
  return info.isAC
    ? { backgroundColor: '#d7f5d0', color: '#0a4012' }
    : { backgroundColor: '#ffd7d7', color: '#5a0404' };
};
//格式化尝试次数
const formatTries=(tries:number)=>{
  if(tries>1){
    return tries+' tries';
  }else{
    return tries+' try';
  }
}
//格式化分钟
const formatMinutes=(time?:number|null)=>{
  if(time === undefined || time === null){
    return '--';
  }
  return Number(time / 60).toFixed(0);
}
//格式化完整时间
const formatActimeFull=(time?:number|null)=>{
  if(time === undefined || time === null){
    return '--';
  }
  const hours = Math.floor(time / 3600);
  const minutes = Math.floor((time % 3600) / 60);
  const seconds = Math.floor(time % 60);
  const pad = (val:number)=>String(val).padStart(2,'0');
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}
//跳转到用户主页
const toUserInfo=(username:string)=>{
    router.push({name:'UserHome',query:{username:username}})
}
//自动刷新
const startAutoRefresh = () => {  
  stopAutoRefresh();
  autoRefreshTimer = setInterval(() => {
    initContestRankList();
  }, 10000);
}
//停止自动刷新
const stopAutoRefresh = () => {
  if(autoRefreshTimer){
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
}
//监听自动刷新
watch(autoRefresh, (val) => {
  if(val){
    startAutoRefresh();
  }else{
    stopAutoRefresh();
  }
});
</script>

<style scoped>
.submit-cell{
  height: 70px;
  span{
    color:#082a07;
  }
}

.problem-cell{
  padding: 15px 0px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-weight: 600;
}
.problem-cell__main{
  font-size: 18px;
}
.problem-cell__tries{
  font-size: 15px;
}

:deep(.el-table__row .el-table__cell){
  width: 100%;
  height: 100%;
  padding: 0;
}
:deep(.el-table__row .cell) {
  width: 100%;
  height: 100%;
  padding: 0;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: #f8f8f9;
  color: #515a6e;
  font-size: 15px;
}
</style>
