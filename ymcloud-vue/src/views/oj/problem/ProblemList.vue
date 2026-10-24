<template>
  <div class="container">
    <x-flex vertical class="l-content">
      <el-card>
        <template #header>
          <x-flex justify="space-between" align="center">
            <span style="font-size: 20px;font-weight: bold;">题目筛选</span>
            <el-button round type="primary" :icon="Refresh" @click="reset">重置</el-button>
          </x-flex>
        </template>

        <x-flex vertical :size="20">
          <el-input
            class="problem-search"
            aria-label="搜索题目或题号"
            placeholder="搜索题目名称或题号…"
            size="large"
            clearable
            v-model="query.keyword"
            :prefix-icon="Search"
            @keyup.enter="pushRouter"
          >
            <template #suffix>
              <el-button type="primary" @click="pushRouter">搜索</el-button>
            </template>
          </el-input>
          <x-flex justify="space-between" align="center" :size="16">
            <x-flex class="difficulty-filter" align="center" :size="12" :wrap="false">
              <span>难度</span>
              <el-radio-group
                class="difficulty-options"
                aria-label="题目难度"
                v-model="query.difficulty"
                @change="pushRouter"
              >
                <el-radio-button
                  v-for="item in difficultyList"
                  :key="item.value"
                  :value="item.value"
                >{{ item.label }}</el-radio-button>
              </el-radio-group>
            </x-flex>
            <x-flex align="center" :size="8" :wrap="false">
              <el-switch
                aria-label="显示标签"
                inline-prompt
                v-model="isLabel"
                :active-icon="Check"
                :inactive-icon="Close"
              />
              <span>显示标签</span>
            </x-flex>
          </x-flex>
        </x-flex>
      </el-card>
      <!-- 题目列表 -->
      <el-card>
        <template #header>
          <span style="font-size: 20px;font-weight: bold;">题目列表</span>
        </template>
        <!-- 表单 -->
        <el-skeleton v-if="problemListLoading" style="width: 100%;" :rows="10" />
        <el-table v-else :data="tableData" stripe style="width: 100%;" empty-text="暂无相关题目列表" highlight-current-row>
          <el-table-column label="题目ID" width="120" prop="problemId">
            <template #default="{ row }">
              <span style="font-weight: 600;">{{ row.problemId }}</span>
            </template>
          </el-table-column>
          <el-table-column min-width="250" label="题目">
            <template #default="{ row }">
              <span @click="doProblem(row.problemId)" style="color: #409EFF; cursor: pointer;font-weight: bold;">
                {{ row.title }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="难度" width="120" align="center">
            <template #default="{ row }">
              <el-tag
                effect="dark"
                round
                disable-transitions
                size="large"
                style="padding: 0px 20px;"
                :type="PROBLEM_DIFFICULTIES.find(item => item.value === row.difficulty)?.type"
              >{{ PROBLEM_DIFFICULTIES.find(item => item.value === row.difficulty)?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标签" min-width="200" align="center">
            <template #default="{ row }">
              <div v-show="isLabel">
                <el-tag disable-transitions v-for="tag in row.tagList" :key="tag.id" :color="tag.color"
                  style="margin: 3px;color:white" size="large" effect="dark">
                  {{ tag.name }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="AC通过率" width="200">
            <template #default="{ row }">
              <el-tooltip :content="row.acceptCount + '/' + row.submitCount" placement="top">
                <el-progress :text-inside="true" :stroke-width="20"
                  :percentage="row.submitCount === 0 ? 0 : Number(((row.acceptCount / row.submitCount) * 100).toFixed(2))"
                  :color="colors" style="font-family: Consolas;" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <!-- 分页条 -->
        <div class="page">
          <el-pagination :page-size="query.pageSize" :current-page="query.pageNo" :total="totalPage"
            :page-sizes="[15, 20, 25]" background layout="total, sizes, prev, pager, next"
            @update:current-page="handlePageChange" @update:page-size="handlePageSizeChange"
            style="margin-top: 10px;" />
        </div>
      </el-card>
    </x-flex>
    <x-flex vertical class="r-content">
      <!-- 标签分类 -->
      <el-card style="padding: 10px;">
        <template #header>
          <span style="text-align: center;font-size: 20px;font-weight: bold;">标签分类</span>
        </template>
        <el-skeleton v-if="tagListLoading" style="width: 100%;" :rows="10" />
        <x-flex v-else :size="8">
          <el-tag v-for="tag in tagsList" :key="tag.id" size="large" :color="tag.color"
            style="color: white;font-size: 15px;padding: 16px;">
            {{ tag.name }}
          </el-tag>
        </x-flex>
        <!-- 底部 -->
        <template #footer>
          <el-button type="primary" :icon="MagicStick" style="height: 40px;width: 100%;" @click="handRandomProblem"
            color="#626aef">随机选择一题</el-button>
        </template>
      </el-card>
    </x-flex>
  </div>
</template>

<script lang="ts" setup>
//import
import { PROBLEM_DIFFICULTIES } from '@/constants/problem';
import { useRouter, useRoute } from 'vue-router';
import { onMounted, ref, watch } from 'vue';
import { MagicStick, Check, Close, Refresh, Search } from '@element-plus/icons-vue';
import type { Tag, ProblemTableVO } from '@/type/types';
import { getTagsList, getProblemTableList, getRandomProblem } from '@/api/api';

//data
const route = useRoute();
const router = useRouter();
const isLabel = ref<boolean>(false);
const difficultyList = [{ label: '全部', value: -1 }, ...PROBLEM_DIFFICULTIES];
const colors = [
  { color: '#f56c6c', percentage: 20 },
  { color: '#e6a23c', percentage: 40 },
  { color: '#6f7ad3', percentage: 60 },
  { color: '#1989fa', percentage: 80 },
  { color: '#5cb87a', percentage: 100 },
]
const tableData = ref<ProblemTableVO[]>([] as ProblemTableVO[]);
const tagsList = ref<Tag[]>([] as Tag[]);

type ProblemListQuery = {
  pageNo: number;
  pageSize: number;
  keyword: string;
  difficulty: number;
};

const query = ref<ProblemListQuery>({
  pageNo: 1,
  pageSize: 15,
  keyword: '',
  difficulty: -1,
});

const totalPage = ref<number>(0);
const problemListLoading = ref<boolean>(false);
const tagListLoading = ref<boolean>(false);

//methods
const init = () => {
  //获取query参数
  const queryParams = route.query;
  query.value.pageNo = Number(queryParams.pageNo) || 1;
  query.value.pageSize = Number(queryParams.pageSize) || 15;
  query.value.keyword = queryParams.keyword as string || '';
  query.value.difficulty = Number(queryParams.difficulty) || -1;
}
//初始化
init();
onMounted(() => {
  //加载tag列表
  handTagList();
  //加载题目表单
  getProblemTableListData();
})
//获取标签列表
const handTagList = () => {
  tagListLoading.value = true;
  getTagsList().then(res => {
    tagsList.value = res.data.data;
  }).catch(e => {
    console.log(e);
  }).finally(() => {
    tagListLoading.value = false;
  })
}
//获取随机题目
const handRandomProblem = () => {
  getRandomProblem().then(res => {
    const problemId = res.data.data;
    doProblem(problemId);
  }).catch(e => {
    console.log(e);
  })
}
//跳转题目详情
const doProblem = (problemId: string) => {
  router.push({ name: 'ProblemDetail', params: { problemID: problemId } });
};
//分页条变化
const handlePageChange = (newPage: number) => {
  query.value.pageNo = newPage;
  pushRouter();
};
//每页条数变化
const handlePageSizeChange = (newPageSize: number) => {
  query.value.pageSize = newPageSize;
  pushRouter();
};
//查询题目列表
const getProblemTableListData = () => {
  problemListLoading.value = true;
  getProblemTableList(query.value).then(res => {
    tableData.value = res.data.data.records;
    totalPage.value = res.data.data.total;
  }).catch(error => {
    console.log(error);
  }).finally(() => {
    problemListLoading.value = false;
  })
}
//分页条件查询
const pushRouter = () => {
  let queryObj = query.value as { [key: string]: string | number | null };
  //删除空值
  Object.keys(queryObj).forEach(key => {
    if (queryObj[key] === null || queryObj[key] === undefined || queryObj[key] === '') {
      delete queryObj[key]
    }
  })
  router.push({ name: 'ProblemList', query: queryObj })
}
//重置查询值
const reset = () => {
  query.value.pageNo = 1;
  query.value.pageSize = 15;
  query.value.keyword = '';
  query.value.difficulty = -1;
  router.push({ name: 'ProblemList' });
}
//监听路由变化
watch(
  () => route.query,
  (newQuery: any) => {
    getProblemTableListData()
  }
)
</script>

<style scoped>
.container {
  height: 100%;
  width: 100%;
  display: flex;
  /* max-width: 1500px; */
  margin: 0 auto;
  /* padding: 20px; */
  gap: 10px;
}

.l-content {
  flex: 2;
  min-width: 0;
}

.r-content {
  flex: 1;
}

.problem-search {
  --el-border-radius-base: 10px;
  --el-input-border-color: #dce7fa;
  height: 48px;
}

.problem-search :deep(.el-input__wrapper) {
  padding: 0 8px 0 16px;
}

.problem-search :deep(.el-input__prefix) {
  margin-right: 4px;
  color: #7186a6;
  font-size: 20px;
}

.problem-search :deep(.el-button) {
  min-width: 68px;
  height: 34px;
  border-radius: 8px;
}

.difficulty-filter {
  min-width: 0;
  max-width: 100%;
}

.difficulty-filter > span {
  flex-shrink: 0;
}

.difficulty-options {
  gap: 6px;
}

.difficulty-options :deep(.el-radio-button__inner) {
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

:deep(.el-table__header .cell) {
  font-family: Consolas;
  font-size: 15px;
  font-weight: 550;
}

@media screen and (max-width: 980px) {
  .container {
    flex-direction: column;
  }
}

@media screen and (min-width: 980px) {
  .container {
    padding: 15px 40px;
  }
}
</style>
