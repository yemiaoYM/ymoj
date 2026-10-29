<template>
  <div class="status-list-container">
    <el-card>
      <x-flex vertical :size="20">
        <!-- 筛选区域 -->
        <x-flex
          justify="space-between"
          align="center"
          :wrap="true"
          :size="[20, 16]"
        >
          <x-flex align="center" :size="24" :wrap="true">
            <span class="filter-label">范围</span>
            <AsyncSwitch
              :model-value="isMine"
              :disabled="loading"
              active-text="我的"
              inactive-text="全部"
              :before-change="handleOnlyMine"
            />
            <span class="filter-label">状态</span>
            <AsyncSelect
              style="width: 200px"
              aria-label="评测状态"
              clearable
              placeholder="选择状态"
              :model-value="status"
              :disabled="loading"
              :value-on-clear="null"
              :before-change="onStatusChange"
            >
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </AsyncSelect>
          </x-flex>
          <el-button
            style="margin-right: 15px"
            round
            type="primary"
            :icon="Refresh"
            :disabled="loading"
            @click="reset"
            >重置</el-button
          >
          <!-- 设置搜索框 -->
          <x-flex align="center" :size="16">
            <el-input
              v-model="problemKey"
              style="width: 240px"
              size="large"
              clearable
              placeholder="请输入题目/题号..."
              :suffix-icon="Search"
              :disabled="loading"
              @keyup.enter="pushRouter()"
            />
            <el-input
              v-model="username"
              style="width: 240px"
              size="large"
              clearable
              placeholder="请输入用户名..."
              :suffix-icon="Search"
              :disabled="loading"
              @keyup.enter="pushRouter()"
            />
          </x-flex>
        </x-flex>

        <!-- 表格展示 -->
        <div>
          <el-skeleton v-if="loading" style="width: 100%" :rows="10" />
          <el-table
            v-else
            empty-text="暂无相关评测列表"
            :data="tableData"
            stripe
            style="width: 100%"
            label-width="auto"
          >
            <el-table-column prop="submissionId" label="提交ID" width="80" />
            <el-table-column prop="problemTitle" label="题目" min-width="200">
              <template #default="{ row }">
                <span
                  @click="handToProblem(row.displayId)"
                  style="cursor: pointer; color: #409eff"
                >
                  {{ row.displayId + " " + row.problemTitle }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="180">
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
                  style="font-weight: bold"
                >
                  {{ row.score === null ? "- -" : row.score }}
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
                <el-tooltip content="查看提交详情" placement="top">
                  <span
                    style="cursor: pointer; color: #409eff"
                    @click="handSubmissionDetail(row.submissionId)"
                    >{{ row.language }}</span
                  >
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户">
              <template #default="{ row }">
                <span
                  @click="handToUserInfo(row.username)"
                  style="cursor: pointer; color: #409eff"
                >
                  {{ row.username }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              prop="createdTime"
              label="提交时间"
              width="200px"
            />
          </el-table>
        </div>

        <!-- 分页组件 -->
        <x-flex justify="flex-end" v-if="total > 0">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 15, 20]"
            :total="total"
            :disabled="loading"
            :page-size="pageSize"
            :current-page="pageNo"
            @update:current-page="handleCurrentChange"
            @update:page-size="handleSizeChange"
          />
        </x-flex>
      </x-flex>
    </el-card>
  </div>
</template>

<script setup lang="ts">
//import
import AsyncSwitch from '@/components/common/AsyncSwitch.vue';
import AsyncSelect from '@/components/common/AsyncSelect.vue';
import { Search, Refresh } from "@element-plus/icons-vue";
import { onBeforeUnmount, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useRouter, useRoute } from "vue-router";
import type { SubmissionTableVO } from "@/type/types";
import {
  getStatusType,
  getScoreType,
  formatSubmissionMemory,
  formatSubmissionTime,
  formatSubmissionLength,
} from "@/utils/utils";
import { getSubmissionTableList, getContestSubmissionList } from "@/api/api";
import { useUserStore } from "@/stores/user";

//data
const statusOptions: { label: string; value: number }[] = [
  { label: "Accepted", value: 0 },
  { label: "Wrong Answer", value: -1 },
  { label: "Time Limit Exceeded", value: 1 },
  { label: "Memory Limit Exceeded", value: 2 },
  { label: "Runtime Error", value: 3 },
  { label: "Compile Error", value: -2 },
  { label: "Presentation Error", value: -3 },
  { label: "Partial Accepted", value: 8 },
  { label: "Submitted Failed", value: 10 },
  { label: "System Error", value: 4 },
];
const userStore = useUserStore();
const route = useRoute();
const router = useRouter();
const routeName = ref<string>("StatusList");
const isContest = ref<boolean>(false);
const contestId = ref<number>(0);
const loading = ref<boolean>(false);
const tableData = ref<SubmissionTableVO[]>([]);
const pageNo = ref<number>(1);
const pageSize = ref<number>(15);
const total = ref<number>(0);

const isMine = ref<boolean>(false);
const username = ref<string | null>(null);
const problemKey = ref<string | null>(null);
const status = ref<number | null>(null);

type SubmissionQuery = {
  isMine: boolean;
  status: number | null;
  problemKey: string | null;
  username: string | null;
  pageNo: number;
  pageSize: number;
};
let requestGeneration = 0;
let confirmedPath = '';

//列表、筛选状态和路由在同一次请求成功后一起生效
const getData = async (params: SubmissionQuery, targetPath: string) => {
  const generation = ++requestGeneration;
  const target = router.resolve(targetPath);
  const nextIsContest = target.name === 'ContestSubmissionList';
  const nextContestId = nextIsContest ? Number(target.params.contestID) : 0;
  loading.value = true;
  try {
    const response = nextIsContest
      ? await getContestSubmissionList({ contestId: nextContestId, ...params })
      : await getSubmissionTableList(params);
    if (generation !== requestGeneration) return;
    tableData.value = response.data.data.records;
    total.value = response.data.data.total;
    isMine.value = params.isMine;
    status.value = params.status;
    problemKey.value = params.problemKey;
    username.value = params.username;
    pageNo.value = params.pageNo;
    pageSize.value = params.pageSize;
    isContest.value = nextIsContest;
    contestId.value = nextContestId;
    routeName.value = target.name as string;
    confirmedPath = targetPath;
    if (route.fullPath !== targetPath) await router.push(targetPath);
  } catch {
    //request 已提示失败，恢复最近成功的路由并保留原列表和筛选状态
    if (generation === requestGeneration && confirmedPath && route.fullPath !== confirmedPath) {
      await router.replace(confirmedPath);
    }
  } finally {
    if (generation === requestGeneration) loading.value = false;
  }
};
//查看提交详情
const handSubmissionDetail = (submitId: number) => {
  if(isContest.value && contestId.value > 0){
    router.push({ 
      name: "ContestSubmissionDetail", 
      params: { 
        contestID: contestId.value,
        submitID: submitId
      }});
    return;
  }
  router.push({ name: "SubmissionDetail", params: { submitID: submitId } });
};
//分页只提交候选值，成功后才更新显示页码
const handleCurrentChange = (val: number) => {
  pushRouter({ pageNo: val });
};
const handleSizeChange = (val: number) => {
  pushRouter({ pageNo: 1, pageSize: val });
};
//由 getData 提交确认状态，阻止开关自行提前翻转
const handleOnlyMine = async (): Promise<boolean> => {
  if (!isMine.value && !userStore.isLogin) {
    ElMessage.warning("请先登录");
    return false;
  }
  await pushRouter({ isMine: !isMine.value, pageNo: 1 });
  return false;
};
const onStatusChange = async (value: number | null) => {
  await pushRouter({ status: value, pageNo: 1 });
  return false;
};
const handToUserInfo = (username: string) => {
  router.push({ name: "UserHome", query: { username: username } });
};
const handToProblem = (displayId: string) => {
  if (contestId.value > 0 && isContest.value) {
    router.push({
      name: "ContestProblemDetail",
      params: { 
        contestID: contestId.value,
        displayID: displayId
      },
    });
  } else {
    router.push({ name: "ProblemDetail", params: { problemID: displayId } });
  }
};
const pushRouter = async (changes: Partial<SubmissionQuery> = {}) => {
  if (loading.value) return;
  const params: SubmissionQuery = {
    isMine: isMine.value,
    status: status.value,
    problemKey: problemKey.value,
    username: username.value,
    pageNo: 1,
    pageSize: pageSize.value,
    ...changes,
  };
  const target = router.resolve({
    name: routeName.value,
    params: route.params,
    query: {
      ...params,
      isMine: String(params.isMine),
      status: params.status ?? undefined,
      problemKey: params.problemKey || undefined,
      username: params.username || undefined,
    },
  });
  await getData(params, target.fullPath);
};
const reset = () => {
  void pushRouter({ isMine: false, status: null, problemKey: null, username: null, pageNo: 1, pageSize: 15 });
};
//刷新、前进后退及比赛路由复用都通过同一查询入口
watch(
  () => route.fullPath,
  (path) => {
    if (path === confirmedPath) {
      requestGeneration++;
      loading.value = false;
      return;
    }
    const query = route.query;
    void getData({
      isMine: query.isMine === 'true',
      status: query.status !== undefined ? Number(query.status) : null,
      problemKey: (query.problemKey as string) || null,
      username: (query.username as string) || null,
      pageNo: Number(query.pageNo) || 1,
      pageSize: Number(query.pageSize) || 15,
    }, path);
  },
  { immediate: true }
);
onBeforeUnmount(() => { requestGeneration++; });
</script>

<style lang="css" scoped>
.status-list-container {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB",
    "Microsoft YaHei", "微软雅黑", Arial, sans-serif;
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
  font-family: Consolas,serif;
  text-align: center;
}

@media screen and (min-width: 1024px) {
  .status-list-container {
    padding: 10px 25px;
  }
}
</style>
