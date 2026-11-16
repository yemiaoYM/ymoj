<template>
  <div class="problem-management">
    <x-flex vertical :size="16">
      <el-card>
        <!-- 搜索区域 -->
        <el-form :model="queryParams" label-width="auto">
          <x-flex :size="10">
            <el-form-item label="关键词" prop="keyword">
              <el-input v-model="queryParams.keyword" placeholder="请输入题目标题或题号" clearable />
            </el-form-item>
            <el-form-item label="权限" prop="visibility">
              <el-select v-model="queryParams.visibility" placeholder="全部题目" clearable style="width: 120px;">
                <el-option label="全部题目" value="" />
                <el-option label="公开题目" :value="PROBLEM_VISIBILITY.PUBLIC" />
                <el-option label="私有题目" :value="PROBLEM_VISIBILITY.PRIVATE" />
                <el-option label="比赛专用" :value="PROBLEM_VISIBILITY.CONTEST_EXCLUSIVE" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="queryParams.difficulty" placeholder="全部题目" clearable style="width: 120px;">
                <el-option label="全部题目" value="" />
                <el-option
                  v-for="item in PROBLEM_DIFFICULTIES"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </x-flex>
        </el-form>
        
        <!-- 操作区域 -->
        <x-flex :size="10" align="center" style="padding-bottom: 15px;">
          <el-button
            v-permission="['problem:problem:add']"
            plain
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >
            创建
          </el-button>
          <el-button
            v-permission="['problem:problem:add']"
            type="success"
            :icon="Plus"
            @click="onOpenRemoteProblem"
          >添加远程题目</el-button>
          <el-button
            v-permission="['problem:problem:remove']"
            plain
            type="danger"
            :icon="Delete"
            :disabled="problemIds.length === 0"
            @click="handleDelete"
          >
            删除
          </el-button>
        </x-flex>
        
        <!-- 题目列表 -->
        <el-table :data="problemList" v-loading="loading" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="ID" prop="id" width="80" align="center" />
          <el-table-column label="展示ID" prop="problemId" width="120" align="center" />
          <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip align="center"/>
          <el-table-column label="作者" prop="authorUsername" width="120" align="center" />
          <el-table-column label="创建时间" prop="createdTime" width="160" align="center" />
          <el-table-column label="更新时间" prop="updatedTime" width="160" align="center" />
          <el-table-column label="权限" width="150" align="center">
            <template #default="scope">
              <AsyncSelect
                v-model="scope.row.visibility"
                :disabled="scope.row.visibility === PROBLEM_VISIBILITY.CONTEST_EXCLUSIVE || !userStore.hasEveryPermission(['problem:problem:publish'])"
                :before-change="(value: number) => handleVisibilityChange(scope.row, value)"
                style="width: 100px;"
              >
                <el-option :value="PROBLEM_VISIBILITY.PUBLIC" label="公开" />
                <el-option :value="PROBLEM_VISIBILITY.PRIVATE" label="私有" />
                <el-option :value="PROBLEM_VISIBILITY.CONTEST_EXCLUSIVE" label="比赛专属" disabled />
              </AsyncSelect>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center">
            <template #default="scope">
              <x-flex :size="0" justify="center">
                <el-button
                  v-permission="['problem:problem:query', 'problem:problem:edit']"
                  class="operation-button" 
                  type="primary" 
                  size="small" 
                  text 
                  :icon="Edit"
                  @click="handleUpdate(scope.row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="['problem:testcase:query']"
                  class="operation-button" 
                  type="info" 
                  size="small" 
                  text 
                  :icon="DocumentCopy"
                  @click="handleTestCase(scope.row)"
                >
                  测试用例
                </el-button>
                <el-button
                  v-permission="['problem:problem:remove']"
                  class="operation-button" 
                  type="danger" 
                  size="small" 
                  text 
                  :icon="Delete"
                  @click="handleDelete(scope.row)"
                >
                  删除
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <x-flex justify="center" style="margin-top: 20px;">
          <el-pagination 
            :page-size="queryParams.pageSize" 
            :current-page="queryParams.pageNo" 
            :total="total"
            :page-sizes="[15, 25, 50]" 
            background 
            layout="total, sizes, prev, pager, next"
            @update:current-page="handleCurrentChange" 
            @update:page-size="handleSizeChange" 
          />
        </x-flex>
      </el-card>
    </x-flex>

    <el-dialog
      title="添加远程题目"
      width="min(680px, calc(100vw - 32px))"
      align-center
      :model-value="remoteVisible"
      :close-on-click-modal="!remoteLoading"
      :close-on-press-escape="!remoteLoading"
      :show-close="!remoteLoading"
      @update:model-value="remoteVisible = $event"
    >
      <el-form
        ref="remoteFormRef"
        class="remote-problem-form"
        label-position="top"
        hide-required-asterisk
        size="large"
        :model="remoteForm"
        :rules="remoteRules"
        :disabled="remoteLoading"
        @submit.prevent="onFetchRemoteProblem"
      >
        <el-form-item label="平台" prop="platform">
          <el-radio-group v-model="remoteForm.platform" class="remote-platforms" @change="onRemotePlatformChange">
            <el-radio v-for="item in remotePlatforms" :key="item.value" border :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题目标识" prop="problemId">
          <el-input maxlength="128" clearable v-model="remoteForm.problemId" :placeholder="remotePlatform.placeholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="large" :disabled="remoteLoading" @click="remoteVisible = false">取消</el-button>
        <el-button size="large" type="primary" :icon="DocumentCopy" :loading="remoteLoading" @click="onFetchRemoteProblem">
          {{ remoteLoading ? '正在读取' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
//import
import AsyncSelect from '@/components/common/AsyncSelect.vue';
import { PROBLEM_DIFFICULTIES, PROBLEM_VISIBILITY } from '@/constants/problem';
import { useUserStore } from '@/stores/user';
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { fetchRemoteProblem, getAdminProblemList, deleteProblemByIds, updateProblemVisibility } from '@/api/problem';
import type { RemoteProblemRequest, AdminProblemVO } from '@/types/problem';
import { useRemoteProblemStore } from '@/stores/remoteProblem';
import { Search, Refresh, Plus, Edit, Delete, DocumentCopy } from '@element-plus/icons-vue';
import { useRouter, useRoute } from 'vue-router';

//data
const userStore = useUserStore();
const router = useRouter();
const route = useRoute();
const problemList = ref<AdminProblemVO[]>([] as AdminProblemVO[]);
const problemIds = ref<number[]>([]);
const total = ref<number>(0);
const loading = ref<boolean>(false);
const remoteStore = useRemoteProblemStore();
const remoteVisible = ref(false);
const remoteLoading = ref(false);
const remoteFormRef = ref<FormInstance>();
const remoteForm = ref<RemoteProblemRequest>({ platform: 'luogu', problemId: '' });
const remotePlatforms = [
  { value: 'luogu', label: '洛谷', placeholder: '例如 P1002' },
  { value: 'leetcode', label: '力扣', placeholder: '例如 two-sum' },
  { value: 'atcoder', label: 'AtCoder', placeholder: '例如 abc480_a 或 abc480/abc480_a' },
  { value: 'codeforces', label: 'Codeforces', placeholder: '例如 2194A' },
  { value: 'nowcoder', label: '牛客', placeholder: '题库 NC326157，比赛 142115/A' },
];
const remotePlatform = computed(() => remotePlatforms.find(item => item.value === remoteForm.value.platform)!);
const remoteRules: FormRules<RemoteProblemRequest> = {
  platform: [{ required: true, message: '请选择题目平台', trigger: 'change' }],
  problemId: [
    { required: true, message: '请输入题号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_/-]{1,128}$/, message: '请输入题目标识，不支持完整网址', trigger: 'blur' },
  ],
};
let disposed = false;
onBeforeUnmount(() => { disposed = true; });

const onOpenRemoteProblem = () => {
  remoteVisible.value = true;
};

const onRemotePlatformChange = () => {
  remoteForm.value.problemId = '';
  remoteFormRef.value?.clearValidate('problemId');
};

const onFetchRemoteProblem = async () => {
  if (!remoteFormRef.value || remoteLoading.value) return;
  remoteLoading.value = true;
  try {
    remoteForm.value.problemId = remoteForm.value.problemId.trim();
    const valid = await remoteFormRef.value.validate(() => {});
    if (!valid || disposed) return;
    const response = await fetchRemoteProblem({ ...remoteForm.value });
    if (disposed) return;
    remoteStore.draft = response.data.data;
    await router.push({ path: '/admin/problem/add', query: { remote: '1' } });
  } catch {
    // 请求层统一提示失败，保留题号供用户重试
    return;
  } finally {
    if (!disposed) remoteLoading.value = false;
  }
};

//URL同步工具函数
const useUrlSync = () => {
  //默认查询参数
  const defaults = {
    keyword: null as string | null,
    visibility: null as number | null,
    difficulty: null as number | null,
    pageNo: 1,
    pageSize: 15,
  };
  //从URL解析查询参数
  const parseFromUrl = () => {
    const { query } = route;
    return {
      keyword: query.keyword as string || defaults.keyword,
      visibility: query.visibility ? Number(query.visibility) : defaults.visibility,
      difficulty: query.difficulty ? Number(query.difficulty) : defaults.difficulty,
      pageNo: query.pageNo ? Number(query.pageNo) : defaults.pageNo,
      pageSize: query.pageSize ? Number(query.pageSize) : defaults.pageSize,
    };
  };
  //同步URL
  const syncToUrl = (params: typeof defaults) => {
    const query = Object.entries(params).reduce((acc, [key, value]) => {
      if (value !== null && value !== undefined && value !== defaults[key as keyof typeof defaults]) {
        acc[key] = value;
      }
      return acc;
    }, {} as Record<string, any>);

    router.replace({ query: Object.keys(query).length ? query : undefined });
  };
  return { defaults, parseFromUrl, syncToUrl };
};
const { defaults, parseFromUrl, syncToUrl } = useUrlSync();
//查询参数
const queryParams = ref(parseFromUrl());

//生命周期
onMounted(() => {
  handleQuery();
});
//methods
//查询题目列表
const handleQuery = () => {
  loading.value = true;
  syncToUrl(queryParams.value); // 同步到 URL
  getAdminProblemList(queryParams.value).then(res => {
    problemList.value = res.data.data.records;
    total.value = res.data.data.total;
  }).catch(err => {
    console.error('获取题目列表失败:', err);
  }).finally(() => {
    loading.value = false;
  });
};
//重置查询
const resetQuery = () => {
  Object.assign(queryParams.value, defaults);
  handleQuery();
};
//改变页大小
const handleSizeChange = (val: number) => {
  queryParams.value.pageSize = val;
  queryParams.value.pageNo = 1; // 重置到第一页
  handleQuery();
};
//改变当前页
const handleCurrentChange = (val: number) => {
  queryParams.value.pageNo = val;
  handleQuery();
};
//选择题目
const handleSelectionChange = (selection: AdminProblemVO[]) => {
  problemIds.value = selection.map(item => item.id);
};
//获取权限文本
const getVisibilityText = (visibility: number) => {
  switch (visibility) {
    case PROBLEM_VISIBILITY.PUBLIC: return '公开题目';
    case PROBLEM_VISIBILITY.PRIVATE: return '私有题目';
    case PROBLEM_VISIBILITY.CONTEST_EXCLUSIVE: return '比赛专用';
    default: return '未知';
  }
};
//新增题目
const handleAdd = () => {
  router.push('/admin/problem/add');
};
//编辑题目
const handleUpdate = (row: AdminProblemVO) => {
  const id = row.id || problemIds.value[0];
  if (!id) {
    ElMessage.warning('请选择要编辑的题目');
    return;
  }
  // 跳转到编辑页面，传递题目ID
  router.push(`/admin/problem/edit/${id}`);
};
//管理测试用例
const handleTestCase = (row: AdminProblemVO) => {
  const id = row.id;
  if (!id) {
    ElMessage.warning('请选择要管理的题目');
    return;
  }
  // 跳转到测试用例管理页面
  router.push(`/admin/problem/${id}/testcases`);
};
//删除题目
const handleDelete = (row: AdminProblemVO) => {
  const ids = row.id || problemIds.value;
  if (!ids) {
    ElMessage.warning('请选择要删除的题目');
    return;
  }
  ElMessageBox.confirm(
    `是否确认删除题目编号为 "${ids}" 的数据项吗？`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    deleteProblemByIds(ids).then(res => {
      ElMessage.success(res.data.msg);
      handleQuery();
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => { });
};
//权限变更处理
const handleVisibilityChange = async (row: AdminProblemVO, newVisibility: number) => {
  await ElMessageBox.confirm(
    `确认将题目 "${row.title}" 的权限从 "${getVisibilityText(row.visibility)}" 修改为 "${getVisibilityText(newVisibility)}" 吗？`,
    '权限修改确认',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  );
  const response = await updateProblemVisibility(row.id, newVisibility);
  ElMessage.success(response.data.msg);
};

//监听 URL 变化 同步查询参数
watch(
  () => route.query,
  () => {
    const newParams = parseFromUrl();
    //检查是否有变化，避免无限循环
    const hasChanged = Object.keys(newParams).some(
      key => newParams[key as keyof typeof newParams] !== queryParams.value[key as keyof typeof queryParams.value]
    );
    if (hasChanged) {
      Object.assign(queryParams.value, newParams);
      loading.value = true;
      getAdminProblemList(queryParams.value).then(res => {
        problemList.value = res.data.data.records;
        total.value = res.data.data.total;
      }).catch(err => {
        console.log(err);
      }).finally(() => {
        loading.value = false;
      });
    }
  },
  { immediate: false }
);
</script>

<style scoped lang="css">
.remote-problem-form {
  padding-top: 12px;
}
.remote-platforms {
  width: 100%;
  gap: 8px;
}
.remote-platforms :deep(.el-radio) {
  flex: 1 1 100px;
  margin-right: 0;
  padding: 0 12px;
}
.remote-problem-form :deep(.el-form-item:last-child) {
  margin-bottom: 8px;
}
@media (max-width: 600px) {
  .remote-platforms :deep(.el-radio) {
    flex-basis: calc(50% - 4px);
  }
}

.problem-management {
  padding: 24px;
}

.operation-button {
  padding: 0px;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: var(--ym-table-header);
  color: var(--ym-table-heading);
  height: 40px;
  font-size: 13px;
  text-align: center;
}
</style>
