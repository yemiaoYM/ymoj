<template>
  <div class="container">
    <el-card>
      <el-skeleton v-if="loading" style="width: 100%;" :rows="20" />
      <n-flex v-else vertical :size="10">
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
              <n-flex justify="center">
                <el-tag size="large" disable-transitions color="#21c1c6" style="cursor:pointer;">
                  <span style="color: white;font-weight:bold;" @click="toUserInfo(row.username)">{{ row.username }}</span>
                </el-tag>
                <el-tag v-if="row.school" size="large" disable-transitions color="#fe9000" style="color: white;">
                  {{ row.school }}
                </el-tag>
              </n-flex>
            </template>
          </el-table-column>
          <el-table-column prop="ac" label="AC" align="center" width="100" />
          <el-table-column v-if="tableData.mode === 1" prop="totalScore" label="总分数" align="center" width="150" />
          <el-table-column prop="totalTime" label="总时间" align="center" width="150" />
          <el-table-column
            v-for="q in tableData.problemInfoData"
            :key="q.displayId"
            align="center"
          >
            <!-- 题目信息 -->
            <template #header>
              <n-flex justify="center">
                <n-icon :size="25" :component="Balloon" :color="q.color"></n-icon>
                <span style="font-weight: bold;">{{ q.displayId }}</span>
              </n-flex>
              <span style="font-size: 12px;">({{q.ac}}/{{q.total}})</span>
            </template>
            <!-- 用户该题评测信息 -->
            <template #default="{ row }">
              <template v-if="row.submissionInfo[q.displayId]">
                <n-flex justify="center" vertical v-if="row.submissionInfo[q.displayId].ac" style="height: 70px;background-color: #60e760;">
                  <span v-if="tableData.mode === 1" style="font-size: 18px;color: black;">
                    {{ row.submissionInfo[q.displayId].score }}
                  </span>
                  <span v-else style="font-size: 18px;color: black;">
                    {{ row.submissionInfo[q.displayId].acTime }}
                  </span>
                  <span v-if="row.submissionInfo[q.displayId].errorNum > 0" style="color: black;">
                    {{ formatTries(row.submissionInfo[q.displayId].errorNum) }}
                  </span>
                  <span v-else style="color: black;">
                    1 try
                  </span>
                </n-flex>
                <n-flex justify="center" vertical v-else style="height: 70px;background-color: #e87272;">
                  <span v-if="tableData.mode === 1" style="font-size: 18px;color: black;">
                    {{ row.submissionInfo[q.displayId].score }}
                  </span>
                  <span style="color: black;">
                    {{ formatTries(row.submissionInfo[q.displayId].errorNum) }}
                  </span>
                </n-flex>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <!-- 分页 -->
        <n-flex justify="end" style="width: 100%;">
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
        </n-flex>
      </n-flex>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref,onMounted } from 'vue';
import { useRouter,useRoute } from 'vue-router';
import { Balloon,Search } from '@vicons/ionicons5';
import type { ContestRankTableVO } from '@/type/types';
import { getContestRankList } from '@/api/api';

//data
const router=useRouter();
const route=useRoute();
const loading=ref<boolean>(false);
const tableData=ref<ContestRankTableVO>({} as ContestRankTableVO);
const contestId=ref<number>(0);
const keyword=ref<string>('');
const pageSize=ref<number>(50);
const pageNo=ref<number>(1);
const total=ref<number>(0);

//methods
onMounted(()=>{
  loading.value=true;
  contestId.value=Number(route.params.contestID);
  console.log('contestId:', contestId.value);
  //获取比赛排名列表
  initContestRankList();
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
//格式化尝试次数
const formatTries=(tries:number)=>{
  if(tries>1){
    return tries+' tries';
  }else{
    return tries+' try';
  }
}
//跳转到用户主页
const toUserInfo=(username:string)=>{
    router.push({name:'UserHome',query:{username:username}})
}
</script>

<style scoped>
.submit-cell{
  height: 70px;
  span{
    color:#082a07;
  }
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
</style>
