<template>
  <div class="status-list-container">
    <el-card>
      <n-flex vertical :size="20">
        <!-- 筛选区域 -->
        <n-flex justify="space-between" align="center" :wrap="true" :size="[20, 16]">
          <n-flex align="center" :size="24" :wrap="true">
            <span class="filter-label">范围</span>
            <el-switch
              v-model="isMine"
              active-text="我的"
              inactive-text="全部"
              @change="pushRouter"
            />
            <span class="filter-label">状态</span>
            <n-select 
              v-model:value="status" 
              :options="statusOptions"
              clearable 
              placeholder="选择状态" 
              style="width: 200px"
              @update:value="pushRouter"
            />
          </n-flex>
          <el-button style="margin-right: 15px;" round type="primary" :icon="Refresh"
                            @click="reset">重置</el-button>
          <!-- 设置搜索框 -->
          <n-flex align="center" :size="16">
            <el-input
              v-model="problemKey"
              style="width: 240px"
              size="large"
              clearable
              placeholder="请输入题目/题号..."
              :suffix-icon="Search"
              @keyup.enter="pushRouter"
            />
            <el-input
              v-model="username"
              style="width: 240px"
              size="large"
              clearable
              placeholder="请输入用户名..."
              :suffix-icon="Search"
              @keyup.enter="pushRouter"
            />
          </n-flex>
        </n-flex>

        <!-- 表格展示 -->
        <div>
          <el-skeleton v-if="loading" style="width: 100%;" :rows="10" />
          <el-table
            v-else
            empty-text="暂无相关评测列表"
            :data="tableData"
            stripe
            style="width: 100%;"
            label-width="auto"
          >
            <el-table-column prop="submissionId" label="提交ID" width="80" />
            <el-table-column prop="problemTitle" label="题目" min-width="200">
              <template #default="{ row }">
                  <span @click="handToProblem(row.displayId)" style="cursor: pointer;color: #409eff;">
                      {{ row.displayId+'  '+row.problemTitle }}
                  </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="150">
              <template #default="{ row }">
                <el-tag 
                  :type="getStatusType(row.status).tag" 
                  effect="dark"
                  size="large"
                  disable-transitions
                  round
                  >
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="分数">
              <template #default="{ row }">
                <el-tag 
                  disable-transitions
                  size="large"
                  effect="plain"
                  :type="getScoreType(row.score)"
                  round
                  style="font-weight: bold;"
                  >
                  {{row.score===null? '- -': row.score}}
                </el-tag> 
              </template>
            </el-table-column>
            <el-table-column prop="time" label="运行时间">
              <template #default="{ row }">
                {{ formatSubmissionTime(row.time) }}
              </template>
            </el-table-column>
            <el-table-column prop="memory" label="运行内存">
              <template #default="{ row }">
                {{ formatSubmissionMemory(row.memory) }}
              </template>
            </el-table-column>
            <el-table-column prop="codeLength" label="代码长度">
              <template #default="{ row }">
                {{ formatSubmissionLength(row.codeLength) }}
              </template>
            </el-table-column>
            <el-table-column prop="language" label="语言">
                <template #default="{ row }">
                  <el-tooltip content="查看提交详情" placement="top" >
                    <span style="cursor: pointer;color: #409eff;" @click="handSubmissionDetail(row.submissionId)">{{row.language}}</span>
                  </el-tooltip>
                </template>
            </el-table-column>
            <el-table-column prop="username" label="用户">
              <template #default="{ row }">
                <span @click="handToUserInfo(row.username)" style="cursor: pointer;color: #409eff;">
                      {{ row.username }}
                  </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="提交时间" width="200px" />
          </el-table>
        </div>
        
        <!-- 分页组件 -->
        <n-flex justify="flex-end" v-if="total > 0">
          <el-pagination 
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10,15,20]"  
            :total="total"
            :page-size="pageSize"
            :current-page="pageNo"
            @update:current-page="handleCurrentChange"
            @update:page-size="handleSizeChange" 
          />
        </n-flex>
      </n-flex>
    </el-card>
  </div>
</template>

<script setup lang="ts">
//import
import { Search,Refresh } from '@element-plus/icons-vue';
import { ref,watch } from 'vue';
import { useRouter,useRoute } from 'vue-router';
import type { SubmissionTableVO } from '@/type/types';
import { getStatusType,getScoreType,
  formatSubmissionMemory,
  formatSubmissionTime,
  formatSubmissionLength } from '@/utils/utils';
import { getSubmissionTableList,getContestSubmissionList } from '@/api/api';

//data
const statusOptions: { label: string; value: number }[] = [
  { label: 'Accepted', value: 0 },
  { label: 'Wrong Answer', value: -1 },
  { label: 'Time Limit Exceeded', value: 1 },
  { label: 'Memory Limit Exceeded', value: 2 },
  { label: 'Runtime Error', value: 3 },
  { label: 'Compile Error', value: -2 },
  { label: 'Presentation Error', value: -3 },
  { label: 'Partial Accepted', value: 8 },
  { label: 'Submitted Failed', value: 10 },
  { label: 'System Error', value: 4 }
];
const route=useRoute();
const router=useRouter();
const routeName=ref<string>("StatusList");
const isContest=ref<boolean>(false);
const contestId=ref<number>(0);
const loading=ref<boolean>(false);
const tableData = ref<SubmissionTableVO[]>([]);
const pageNo=ref<number>(1);
const pageSize=ref<number>(15);
const total=ref<number>(0);

const isMine=ref<boolean>(false);
const username=ref<string|null>(null);
const problemKey=ref<string|null>(null);
const status=ref<number|null>(null);

//methods
const init=()=>{
  routeName.value=route.name as string;
  if(routeName.value==="ContestSubmissionList"){
    isContest.value=true;
    contestId.value=Number(route.params.contestID);
  }else{
    isContest.value=false;
    contestId.value=0;
  }
  const queryParams = route.query;
  isMine.value=queryParams.isMine==="true"?true:false;
  status.value = queryParams.status !== undefined ? Number(queryParams.status) : null;
  problemKey.value=queryParams.problemKey as string || null;
  username.value=queryParams.username as string || null;
  pageNo.value=Number(queryParams.pageNo) || 1;
  pageSize.value=Number(queryParams.pageSize) || 15;
}
//分页查询
const getData=()=>{
  loading.value=true;
  let params={
    isMine:isMine.value,
    status:status.value,
    problemKey:problemKey.value,
    username:username.value,
    pageNo:pageNo.value,
    pageSize:pageSize.value,
  }
  //比赛提交列表查询
  if(isContest.value&&contestId.value>0){
    getContestSubmissionList({
      contestId:contestId.value,
      ...params
    }).then(res=>{
      tableData.value=res.data.data.records;
      total.value=res.data.data.total;
    }).catch(e=>{
      console.log(e);
    }).finally(()=>{
      loading.value=false;
    })
  }else{
    getSubmissionTableList(params).then(res=>{
      tableData.value=res.data.data.records;
      total.value=res.data.data.total;
    }).catch(e=>{
      console.log(e);
    }).finally(()=>{
      loading.value=false;
    })
  }
}
//查看提交详情
const handSubmissionDetail=(submitId:number)=>{
  router.push({name:'SubmissionDetail',params:{submitID:submitId}});
}
//改变页码
const handleCurrentChange=(val: number): void =>{
  pageNo.value=val;
  pushRouter();
}
//改变页大小
const handleSizeChange=(val: number): void =>{
  pageSize.value=val;
  pushRouter();
}
const handToUserInfo=(username:string)=>{
  router.push({name:'UserHome',query:{username:username}})
}
const handToProblem = (displayId: string) => {
  router.push({ name: 'ProblemDetail', params: { problemID: displayId } });
};
const pushRouter=()=>{
  let query: { [key: string]: string | number | null } ={
    isMine:isMine.value?"true":"false",
    status:status.value,
    problemKey:problemKey.value,
    username:username.value,
    pageNo:pageNo.value,
    pageSize:pageSize.value
  }
  //删除空值
  Object.keys(query).forEach(key => {
    if (query[key] === null || query[key] === undefined || query[key] === '') {
      delete query[key]
    }
  })
  router.push({ name: routeName.value, query: query });
}
//重置
const reset=()=>{
  isMine.value=false;
  status.value=null;
  problemKey.value=null;
  username.value=null;
  pageNo.value=1;
  pageSize.value=10;
  router.push({ name: routeName.value });
}
//监听路由变化
watch(
  () => [route.query,route.name],
  (newRouteQuery: any,newRouteName: any) => {
    routeName.value=newRouteName;
    init();
    getData();
  },
  { immediate: true }
)
</script>

<style lang="css" scoped>
.status-list-container {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "微软雅黑", Arial, sans-serif;
}

.filter-label {
    font-size: 14px;
    font-weight: 600;
    color: #606266;
    margin-right: 8px;
}

::v-deep(.el-table__header th) {
  font-size: 15px;
  color: #666;
  font-weight: bold;
  text-align: center;
}

::v-deep(.el-table__body .el-table__cell) {
  font-size: 15px;
  color: #000305;
  font-family: Consolas;
  text-align: center;
}
  
@media screen and (min-width: 1024px) {
  .status-list-container {
    padding: 10px 25px;
  }
}
</style>
