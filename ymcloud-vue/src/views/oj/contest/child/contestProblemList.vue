<template>
  <div class="problem-list">
    <el-table 
        :data="contestProblemList" 
        stripe
        highlight-current-row
        @cell-click="handleCellClick"
      >
      <el-table-column prop="displayId" align="center" label="#" >
        <template #default="{ row }">
          <n-flex justify="center">
            <n-icon :size="30" :component="Balloon" :color="row.color"></n-icon>
            {{ row.displayId }}
          </n-flex>
        </template>
      </el-table-column>
      <el-table-column prop="displayTitle" align="center" label="题目" />
      <el-table-column prop="ac" align="center" label="AC" />
      <el-table-column prop="total" align="center" label="总数" />
      <el-table-column prop="accepted" align="center" label="我的状态">
        <template #default="{row}">
          <el-tag v-if="row.accepted" size="large" disable-transitions color="#21c1c6" style="color: white;">
            已通过
          </el-tag>
          <el-tag v-else size="large" disable-transitions color="#fe8053" style="color: white;">
            未通过
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
//import
import { useRouter,useRoute } from 'vue-router';
import { ref,onMounted } from 'vue';
import { Balloon } from '@vicons/ionicons5';
import type { ContestProblemTableVO } from '@/type/types';
import { getContestProblemList } from '@/api/api';

//data
const router=useRouter();
const route=useRoute();
const contestId=ref<number>(0);
const contestProblemList=ref<ContestProblemTableVO[]>([] as ContestProblemTableVO[]);

//methods
onMounted(() => {
  contestId.value=Number(route.params.contestID);
  //初始化比赛题目列表
  initContestProblemList();
})

//获取比赛题目列表
const initContestProblemList=()=>{
  getContestProblemList(contestId.value).then((res)=>{
    contestProblemList.value=res.data.data;
  }).catch(e=>{
    console.log(e);
  })
}
//跳转到题目详情
const handleCellClick = (row: any) => {
  router.push({
    name:"ContestProblemDetail",
    params:{
      contestID:contestId.value,
      displayID:row.displayId
    }
  })
};
</script>

<style scoped>
:deep(.el-table__header .cell) {
  font-family: 'Microsoft YaHei';
  font-size: 14px;
  font-weight: 550;
}

:deep(.el-table__body .el-table__cell) {
  color: #000305;
}
</style>