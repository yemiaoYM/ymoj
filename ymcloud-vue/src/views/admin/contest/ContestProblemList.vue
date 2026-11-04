<template>
  <div class="contest-problem-management">
    <x-flex vertical :size="16">
      <el-card>
        <template #header>
          <span style="font-size: 18px; font-weight: 600; color: var(--ym-link);">
            比赛题目管理
          </span>
        </template>

        <!-- 操作区域 -->
        <x-flex align="center" style="padding-bottom: 15px;" :size="10">
          <el-button
            v-permission="['contest:problem:add']"
            plain
            type="primary"
            :icon="Plus"
            :disabled="structureLocked || loading || contestProblemList.length >= 15"
            :loading="problemSearchLoading"
            @click="handleAddProblem"
          >
            添加题目
          </el-button>
          <el-button
            v-permission="['contest:problem:edit']"
            plain
            type="primary"
            :icon="Sort"
            :disabled="structureLocked || loading || contestProblemList.length < 2"
            @click="onOpenOrder"
          >调整顺序</el-button>
          <el-button
            v-permission="['contest:problem:remove']"
            plain
            type="danger"
            :icon="Delete"
            :disabled="structureLocked || contestProblemIds.length === 0 || removeAction.pending.value"
            @click="handleDeleteProblem()"
          >
            移除选中题目
          </el-button>
        </x-flex>

        <el-text v-if="structureLocked" type="info">比赛结构已锁定：可纠正题面及展示信息，不可增删题目、排序或修改题号。</el-text>

        <!-- 题目列表 -->
        <el-table
          :data="contestProblemList"
          v-loading="loading"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="ID" prop="id" width="100" align="center" />
          <el-table-column label="原题显示" min-width="150" align="center" show-overflow-tooltip>
            <template #default="scope">
              <x-flex vertical style="font-family: Consolas;" :size="8">
                <span>展示ID: {{ scope.row.problemDisplayId }}</span>
                <span>标题: {{ scope.row.problemDisplayTitle }}</span>
              </x-flex>
            </template>
          </el-table-column>
          <el-table-column label="比赛显示" min-width="150" align="center" show-overflow-tooltip>
            <template #default="scope">
              <x-flex vertical style="font-family: Consolas;" :size="8">
                <span>展示ID: {{ scope.row.displayId }}</span>
                <span>标题: {{ scope.row.displayTitle }}</span>
                <x-flex align="center" justify="center">
                  气球颜色: <el-icon :size="30" :color="scope.row.color"><component :is="Balloon" /></el-icon>
                </x-flex>
              </x-flex>
            </template>
          </el-table-column>
          <el-table-column label="作者" prop="authorUsername" width="180" align="center" />
          <el-table-column label="操作" width="200" align="center">
            <template #default="scope">
              <x-flex :size="5" justify="center">
                <el-button
                  v-permission="['contest:problem:edit']"
                  class="operation-button"
                  type="primary"
                  size="small"
                  text
                  :icon="Edit"
                  @click="handleEditProblem(scope.row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="['contest:problem:remove']"
                  class="operation-button"
                  type="danger"
                  size="small"
                  text
                  :icon="Delete"
                  :disabled="structureLocked || removeAction.pending.value"
                  @click="handleDeleteProblem(scope.row)"
                >
                  移除
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </x-flex>

    <!-- 添加题目对话框 -->
    <el-dialog
      title="添加题目"
      width="min(1120px, calc(100vw - 32px))"
      top="4vh"
      v-model="addProblemDialogVisible"
      :close-on-click-modal="false"
      :close-on-press-escape="!addAction.pending.value"
      :show-close="!addAction.pending.value"
    >
      <x-flex vertical :size="16">
        <x-flex align="center" :size="12">
          <el-input
            class="problem-keyword"
            clearable
            placeholder="题目标题或题号"
            v-model="problemSearchParams.keyword"
            :prefix-icon="Search"
            @keyup.enter="searchProblems(1)"
          />
          <el-select
            class="problem-difficulty"
            placeholder="全部难度"
            clearable
            aria-label="难度"
            v-model="problemSearchParams.difficulty"
          >
            <el-option
              v-for="item in PROBLEM_DIFFICULTIES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-button type="primary" :icon="Search" @click="searchProblems(1)">搜索</el-button>
        </x-flex>
        <x-flex class="problem-selection" :size="16" :wrap="false">
          <!-- 题目选择表格 -->
          <el-table
            class="available-problems"
            height="var(--problem-table-height)"
            :data="availableProblems"
            v-loading="problemSearchLoading"
          >
            <el-table-column width="46" align="center">
              <template #default="{ row }">
                <el-checkbox
                  :model-value="problemIds.includes(row.id)"
                  :disabled="!problemIds.includes(row.id) && selectedProblems.length >= remainingSlots"
                  :aria-label="`选择 ${row.problemId}`"
                  @change="onSelectProblem(row, $event)"
                />
              </template>
            </el-table-column>
            <el-table-column label="题目ID" prop="problemId" width="120" align="center" />
            <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
            <el-table-column label="难度" width="100" align="center">
              <template #default="scope">
                <el-tag
                  size="large"
                  effect="dark"
                  round
                  :type="PROBLEM_DIFFICULTIES.find(item => item.value === scope.row.difficulty)?.type"
                >{{ PROBLEM_DIFFICULTIES.find(item => item.value === scope.row.difficulty)?.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="作者" prop="authorUsername" width="120" align="center" />
          </el-table>
          <x-flex class="selected-problems" vertical :size="12">
            <el-text>已选题目 ({{ selectedProblems.length }})</el-text>
            <el-scrollbar height="100%">
              <x-flex class="selected-tags" :size="8">
                <el-tag
                  v-for="problem in selectedProblems"
                  :key="problem.id"
                  size="large"
                  effect="dark"
                  closable
                  disable-transitions
                  @close="onSelectProblem(problem, false)"
                >{{ problem.problemId }} · {{ problem.title }}</el-tag>
              </x-flex>
            </el-scrollbar>
          </x-flex>
        </x-flex>
      </x-flex>
      <template #footer>
        <x-flex class="problem-dialog-footer" align="center" justify="space-between" :size="12">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :page-size="problemSearchParams.pageSize"
            :current-page="problemSearchParams.pageNo"
            :total="problemSearchTotal"
            :pager-count="5"
            @update:current-page="searchProblems"
          />
          <x-flex :size="10">
            <el-button :disabled="addAction.pending.value" @click="addProblemDialogVisible = false">取消</el-button>
            <el-button
              v-permission="['contest:problem:add']"
              type="primary"
              :disabled="problemIds.length === 0 || selectedProblems.length > remainingSlots"
              :loading="addAction.pending.value"
              @click="confirmAddProblems"
            >添加</el-button>
          </x-flex>
        </x-flex>
      </template>
    </el-dialog>

    <el-dialog
      title="调整顺序"
      width="min(560px, calc(100vw - 32px))"
      v-model="orderDialogVisible"
      :close-on-click-modal="false"
      :close-on-press-escape="!orderAction.pending.value"
      :show-close="!orderAction.pending.value"
    >
      <el-text type="info">保存后按当前顺序分配 A、B、C…展示 ID</el-text>
      <el-scrollbar class="order-list" max-height="55vh">
        <x-flex
          v-for="(problem, index) in orderedProblems"
          :key="problem.id"
          class="order-row"
          align="center"
          :wrap="false"
        >
          <el-tag size="large" effect="dark">{{ String.fromCharCode(65 + index) }}</el-tag>
          <span class="order-title">{{ problem.displayTitle }}</span>
          <el-button
            aria-label="上移"
            :icon="ArrowUp"
            :disabled="index === 0 || orderAction.pending.value"
            @click="onMoveProblem(index, -1)"
          />
          <el-button
            aria-label="下移"
            :icon="ArrowDown"
            :disabled="index === orderedProblems.length - 1 || orderAction.pending.value"
            @click="onMoveProblem(index, 1)"
          />
        </x-flex>
      </el-scrollbar>
      <template #footer>
        <el-button :disabled="orderAction.pending.value" @click="orderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="orderAction.pending.value" @click="onSaveOrder">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑题目信息对话框 -->
    <el-dialog
      title="编辑比赛题目信息"
      width="min(520px, calc(100vw - 32px))"
      v-model="editProblemDialogVisible"
      :close-on-click-modal="false"
      :close-on-press-escape="!editAction.pending.value"
      :show-close="!editAction.pending.value"
    >
      <el-form ref="editFormRef" label-width="80px" :model="editingProblem" :rules="editProblemRules">
        <el-form-item label="题目标题" prop="displayTitle">
          <el-input placeholder="在比赛中显示的题目标题" v-model="editingProblem.displayTitle" />
        </el-form-item>
        <el-form-item label="气球颜色" prop="color">
          <x-flex align="center" :size="12">
            <el-color-picker v-model="editingProblem.color" />
            <el-input placeholder="#ff4757" style="width: 120px;" v-model="editingProblem.color" />
          </x-flex>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button :disabled="editAction.pending.value" @click="editProblemDialogVisible = false">取消</el-button>
          <el-button
            v-permission="['contest:problem:edit']"
            type="primary"
            :loading="editAction.pending.value"
            @click="confirmEditProblem"
          >保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { PROBLEM_DIFFICULTIES } from '@/constants/problem';
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import type { AdminContestProblemVO, ContestProblemPageQuery } from '@/type/contest';
import type { AdminProblemVO } from '@/type/types';
import { Plus, Edit, Delete, Sort, ArrowUp, ArrowDown, Search } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';
import { useAsyncChange } from '@/composables/useAsyncChange';
import { Balloon } from '@vicons/ionicons5';
import {
  getAdminContestProblems,
  getAdminContestById,
  getAvailableProblemsForContest,
  addProblemsToContest,
  updateContestProblem,
  removeContestProblems,
  reorderContestProblems
} from '@/api/api';

//data
const route = useRoute();
const contestId = ref<number>(0);
const contestProblemList = ref<AdminContestProblemVO[]>([]);
const contestProblemIds = ref<number[]>([]);
const loading = ref<boolean>(false);
const structureLocked = ref(true);
//添加题目相关
const addProblemDialogVisible = ref<boolean>(false);
const availableProblems = ref<AdminProblemVO[]>([]);
const selectedProblems = ref<AdminProblemVO[]>([]);
const problemIds = computed(() => selectedProblems.value.map(problem => problem.id));
const remainingSlots = computed(() => Math.max(0, 15 - contestProblemList.value.length));
const addAction = useAsyncChange();
const orderAction = useAsyncChange();
const removeAction = useAsyncChange();
const orderDialogVisible = ref(false);
const orderedProblems = ref<AdminContestProblemVO[]>([]);
let searchGeneration = 0;
onBeforeUnmount(() => {
  searchGeneration++;
});
const problemSearchLoading = ref<boolean>(false);
const problemSearchTotal = ref<number>(0);
//编辑题目相关
const editProblemDialogVisible = ref<boolean>(false);
const editingProblem = ref<Pick<AdminContestProblemVO, 'id' | 'displayTitle' | 'color'>>({
  id: 0,
  displayTitle: '',
  color: '',
});
const editFormRef = ref<FormInstance>();
const editAction = useAsyncChange();

//题目搜索参数
const problemSearchParams = ref<ContestProblemPageQuery>({
  keyword: '',
  difficulty: undefined,
  pageNo: 1,
  pageSize: 10,
});

//编辑表单验证规则
const editProblemRules: FormRules<typeof editingProblem.value> = {
  displayTitle: [
    { required: true, message: '题目标题不能为空', trigger: 'blur' },
    { max: 100, message: '题目标题长度不能超过100个字符', trigger: 'blur' },
  ],
  color: [
    { required: true, message: '气球颜色不能为空', trigger: 'blur' },
  ],
};

//生命周期
onMounted(() => {
  //获取比赛ID
  contestId.value = Number(route.params.id);
  if (contestId.value) {
    handleQuery();
  }
});
//methods
//查询比赛题目列表
const handleQuery = () => {
  loading.value = true;
  Promise.all([getAdminContestProblems(contestId.value), getAdminContestById(contestId.value)]).then(([res, info]) => {
    contestProblemList.value = res.data.data;
    structureLocked.value = info.data.data.structureLocked;
  }).catch(e => {
    console.log(e);
  }).finally(() => {
    loading.value = false;
  });
};
//选择比赛题目记录
const handleSelectionChange = (selection: AdminContestProblemVO[]) => {
  contestProblemIds.value = selection.map(item => item.id);
};
//添加题目
const handleAddProblem = async () => {
  const loaded = await searchProblems(1);
  if (loaded) {
    selectedProblems.value = [];
    addProblemDialogVisible.value = true;
  }
};
//搜索可用题目
const searchProblems = async (pageNo = problemSearchParams.value.pageNo) => {
  const generation = ++searchGeneration;
  problemSearchLoading.value = true;
  try {
    const res = await getAvailableProblemsForContest(contestId.value, { ...problemSearchParams.value, pageNo });
    if (generation !== searchGeneration) {
      return false;
    }
    availableProblems.value = res.data.data.records;
    problemSearchTotal.value = res.data.data.total;
    problemSearchParams.value.pageNo = pageNo;
    return true;
  } catch {
    return false;
  } finally {
    if (generation === searchGeneration) {
      problemSearchLoading.value = false;
    }
  }
};
const onSelectProblem = (problem: AdminProblemVO, selected: boolean | string | number) => {
  if (selected) {
    selectedProblems.value.push(problem);
  } else {
    selectedProblems.value = selectedProblems.value.filter(item => item.id !== problem.id);
  }
};
const confirmAddProblems = async () => {
  const saved = await addAction.beforeChange(async () => {
    await addProblemsToContest(contestId.value, problemIds.value);
  });
  if (saved) {
    addProblemDialogVisible.value = false;
    selectedProblems.value = [];
    handleQuery();
  }
};
const onOpenOrder = () => {
  orderedProblems.value = [...contestProblemList.value];
  orderDialogVisible.value = true;
};
const onMoveProblem = (index: number, offset: number) => {
  const movedProblems = orderedProblems.value.splice(index, 1);
  orderedProblems.value.splice(index + offset, 0, ...movedProblems);
};
const onSaveOrder = async () => {
  const saved = await orderAction.beforeChange(async () => {
    await reorderContestProblems(
      contestId.value,
      orderedProblems.value.map(item => item.id)
    );
  });
  if (saved) {
    orderDialogVisible.value = false;
    handleQuery();
  }
};
//编辑题目
const handleEditProblem = (row: AdminContestProblemVO) => {
  editingProblem.value = {
    id: row.id,
    displayTitle: row.displayTitle,
    color: row.color,
  };
  editProblemDialogVisible.value = true;
};
//确认编辑题目
const confirmEditProblem = async () => {
  const form = editFormRef.value;
  if (!form) {
    return;
  }
  const saved = await editAction.beforeChange(async () => {
    await form.validate();
    const response = await updateContestProblem(
      contestId.value,
      editingProblem.value.id,
      editingProblem.value.displayTitle,
      editingProblem.value.color
    );
    ElMessage.success(response.data.msg);
  });
  if (saved) {
    editProblemDialogVisible.value = false;
    handleQuery();
  }
};
//删除题目
const handleDeleteProblem = async (row?: AdminContestProblemVO) => {
  const ids = row ? [row.id] : contestProblemIds.value;
  const saved = await removeAction.beforeChange(async () => {
    await ElMessageBox.confirm(
      `确认将题目 "${ids}" 从比赛中移除吗？`,
      '移除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
    const res = await removeContestProblems(contestId.value, ids);
    ElMessage.success(res.data.msg);
  });
  if (saved) {
    handleQuery();
  }
};
</script>

<style scoped lang="css">
.contest-problem-management {
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

:deep(.el-color-picker) {
  height: 32px;
}

:deep(.el-color-picker__trigger) {
  width: 32px;
  height: 32px;
}

.problem-keyword {
  flex: 1;
  min-width: 180px;
}

.problem-difficulty {
  width: 140px;
}

.problem-selection {
  --problem-table-height: min(540px, calc(92dvh - 180px));
  height: var(--problem-table-height);
}

.available-problems {
  flex: 1;
  min-width: 0;
}

.selected-problems {
  width: 280px;
  min-height: 0;
  flex-shrink: 0;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.selected-tags {
  padding-right: 12px;
}

.selected-tags .el-tag {
  max-width: 100%;
  height: auto;
  min-height: 32px;
}

.selected-tags :deep(.el-tag__content) {
  white-space: normal;
  overflow-wrap: anywhere;
}

.problem-dialog-footer .el-button {
  margin-left: 0;
}

@media (max-width: 760px) {
  /* 窄屏覆盖 XFlex 的内联方向，已选区移到表格下方 */
  .problem-selection {
    --problem-table-height: min(540px, calc(92dvh - 350px));
    flex-direction: column !important;
    height: auto;
  }

  .available-problems {
    flex: none;
  }

  .selected-problems {
    width: 100%;
    height: 120px;
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 600px) {
  .problem-keyword {
    flex-basis: 100%;
  }

  .problem-difficulty {
    flex: 1;
  }

  /* 窄屏覆盖 XFlex 的内联对齐，让换行后的操作区靠右 */
  .problem-dialog-footer {
    justify-content: flex-end !important;
  }
}

.order-list {
  margin-top: 12px;
}

.order-row {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.order-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-row .el-button {
  margin-left: 0;
}
</style>
