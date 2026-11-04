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
            @keyup.enter="applyFilters"
          >
            <template #suffix>
              <el-button type="primary" @click="applyFilters">搜索</el-button>
            </template>
          </el-input>
          <x-flex justify="space-between" align="center" :size="16">
            <x-flex class="difficulty-filter" align="center" :size="12" :wrap="false">
              <span>难度</span>
              <el-radio-group
                class="difficulty-options"
                aria-label="题目难度"
                v-model="query.difficulty"
                @change="applyFilters"
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
          <x-flex v-if="selectedTags.length" class="selected-tags" align="center" :size="12" :wrap="false">
            <span>标签</span>
            <x-flex :size="8" aria-label="已选标签">
              <el-tag
                v-for="tag in selectedTags"
                :key="tag.id"
                closable
                disable-transitions
                size="large"
                effect="dark"
                @close="toggleTag(tag.id)"
              >{{ tag.name }}</el-tag>
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
              <span @click="doProblem(row.problemId)" style="color: var(--ym-link); cursor: pointer;font-weight: bold;">
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
          <el-tag
            v-for="tag in tagsList"
            :key="tag.id"
            class="tag-option"
            size="large"
            :color="tag.color"
            role="button"
            tabindex="0"
            :aria-pressed="query.tagIds.includes(tag.id)"
            @click="toggleTag(tag.id)"
            @keydown.enter.prevent="toggleTag(tag.id)"
            @keydown.space.prevent="toggleTag(tag.id)"
          >
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
import { computed, onMounted, ref, watch } from 'vue';
import { MagicStick, Check, Close, Refresh, Search } from '@element-plus/icons-vue';
import type { Tag, ProblemTableVO } from '@/type/types';
import { getTagsList, getProblemTableList, getRandomProblem } from '@/api/api';
import type { ProblemListQuery } from '@/api/api';

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

const query = ref<ProblemListQuery>({
  pageNo: 1,
  pageSize: 15,
  keyword: '',
  difficulty: -1,
  tagIds: [],
});
const selectedTags = computed(() => query.value.tagIds.map(id =>
  tagsList.value.find(tag => tag.id === id) ?? { id, name: String(id), color: undefined }
));

const totalPage = ref<number>(0);
const problemListLoading = ref<boolean>(false);
const tagListLoading = ref<boolean>(false);
let problemRequestId = 0;

//methods
const init = () => {
  //获取query参数
  const queryParams = route.query;
  query.value.pageNo = Number(queryParams.pageNo) || 1;
  query.value.pageSize = Number(queryParams.pageSize) || 15;
  query.value.keyword = queryParams.keyword as string || '';
  query.value.difficulty = Number(queryParams.difficulty) || -1;
  const tagIds = [queryParams.tagIds].flat().join(',').split(',').map(Number);
  query.value.tagIds = [...new Set(tagIds.filter(id => Number.isSafeInteger(id) && id > 0))];
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
  applyFilters();
};
//筛选变化后从第一页查询
const applyFilters = () => {
  query.value.pageNo = 1;
  pushRouter();
};
//再次点击已选标签即可取消
const toggleTag = (id: number) => {
  query.value.tagIds = query.value.tagIds.includes(id)
    ? query.value.tagIds.filter(tagId => tagId !== id)
    : [...query.value.tagIds, id];
  applyFilters();
};
//查询题目列表
const getProblemTableListData = () => {
  const requestId = ++problemRequestId;
  problemListLoading.value = true;
  getProblemTableList(query.value).then(res => {
    //连续切换筛选时只展示最后一次请求的结果
    if (requestId !== problemRequestId) return;
    tableData.value = res.data.data.records;
    totalPage.value = res.data.data.total;
  }).catch(error => {
    console.log(error);
  }).finally(() => {
    if (requestId === problemRequestId) {
      problemListLoading.value = false;
    }
  })
}
//分页条件查询
const pushRouter = () => {
  router.push({
    name: 'ProblemList',
    query: {
      pageNo: query.value.pageNo,
      pageSize: query.value.pageSize,
      keyword: query.value.keyword || undefined,
      difficulty: query.value.difficulty,
      tagIds: query.value.tagIds.length ? query.value.tagIds.join(',') : undefined,
    },
  });
}
//重置查询值
const reset = () => {
  query.value.pageNo = 1;
  query.value.pageSize = 15;
  query.value.keyword = '';
  query.value.difficulty = -1;
  query.value.tagIds = [];
  router.push({ name: 'ProblemList' });
}
//监听路由变化
watch(
  () => route.query,
  () => {
    init();
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
  --el-input-border-color: var(--ym-search-border);
  height: 48px;
}

.problem-search :deep(.el-input__wrapper) {
  padding: 0 8px 0 16px;
}

.problem-search :deep(.el-input__prefix) {
  margin-right: 4px;
  color: var(--ym-search-icon);
  font-size: 20px;
}

.problem-search :deep(.el-button) {
  min-width: 68px;
  height: 34px;
  border-radius: 8px;
}

.problem-search :deep(.el-input__clear) {
  margin-right: 12px;
}

.difficulty-filter {
  min-width: 0;
  max-width: 100%;
}

.difficulty-filter > span {
  flex-shrink: 0;
}

.selected-tags > span {
  flex-shrink: 0;
}

.tag-option {
  color: white;
  font-size: 15px;
  padding: 16px;
  cursor: pointer;
  transition: transform 0.15s;
}

.tag-option:hover {
  transform: scale(1.05);
}

.tag-option:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
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
