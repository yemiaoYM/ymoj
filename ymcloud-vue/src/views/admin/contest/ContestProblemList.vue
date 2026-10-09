<template>
  <div class="contest-problem-management">
    <n-flex vertical :size="16">
      <el-card>
        <template #header>
          <span style="font-size: 18px; font-weight: 600; color: #409eff;">
            比赛题目管理
          </span>
        </template>
        
        <!-- 操作区域 -->
        <n-flex :size="10" align="center" style="padding-bottom: 15px;">
          <el-button
            v-permission="['contest:problem:add']"
            plain
            type="primary"
            :icon="Plus"
            @click="handleAddProblem"
          >
            从公开题库添加题目
          </el-button>
          <el-button
            v-permission="['contest:problem:remove']"
            plain
            type="danger"
            :icon="Delete"
            :disabled="contestProblemIds.length === 0"
            @click="handleDeleteProblem"
          >
            移除选中题目
          </el-button>
        </n-flex>
        
        <!-- 题目列表 -->
        <el-table :data="contestProblemList" v-loading="loading" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="ID" prop="id" width="100" align="center" />
          <el-table-column label="原题显示" min-width="150" align="center" show-overflow-tooltip>
            <template #default="scope">
              <n-flex :size="8" vertical style="font-family: Consolas;">
                <span>展示ID: {{ scope.row.problemDisplayId }}</span>
                <span>标题: {{ scope.row.problemDisplayTitle }}</span>
              </n-flex>
            </template>
          </el-table-column>
          <el-table-column label="比赛显示" min-width="150" align="center" show-overflow-tooltip>
            <template #default="scope">
              <n-flex :size="8" vertical style="font-family: Consolas;">
                <span>展示ID: {{ scope.row.displayId }}</span>
                <span>标题: {{ scope.row.displayTitle }}</span>
                <n-flex align="center" justify="center">
                  气球颜色: <n-icon :size="30" :component="Balloon" :color="scope.row.color"></n-icon>
                </n-flex>
              </n-flex>
            </template>
          </el-table-column>
          <el-table-column label="作者" prop="authorUsername" width="180" align="center" />
          <el-table-column label="操作" width="200" align="center">
            <template #default="scope">
              <n-flex :size="5" justify="center">
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
                  @click="handleDeleteProblem(scope.row)"
                >
                  移除
                </el-button>
              </n-flex>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </n-flex>

    <!-- 添加题目对话框 -->
    <el-dialog 
      v-model="addProblemDialogVisible" 
      title="从公开题库添加题目" 
      width="80%" 
      :close-on-click-modal="false"
    >
      <div class="add-problem-content">
        <!-- 搜索区域 -->
        <el-form :model="problemSearchParams" label-width="auto" style="margin-bottom: 20px;">
          <n-flex :size="10">
            <el-form-item label="关键词">
              <el-input 
                v-model="problemSearchParams.keyword" 
                placeholder="请输入题目标题或题号" 
                clearable 
                style="width: 200px;"
              />
            </el-form-item>
            <el-form-item label="难度">
              <el-select v-model="problemSearchParams.difficulty" placeholder="全部难度" clearable style="width: 120px;">
                <el-option label="全部难度" value="" />
                <el-option label="未评定" :value="0" />
                <el-option label="入门" :value="1" />
                <el-option label="普及" :value="2" />
                <el-option label="提高" :value="3" />
                <el-option label="省选及以上" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchProblems">搜索题目</el-button>
            </el-form-item>
          </n-flex>
        </el-form>

        <!-- 题目选择表格 -->
        <el-table 
          :data="availableProblems" 
          v-loading="problemSearchLoading" 
          @selection-change="handleProblemSelectionChange"
          max-height="400"
        >
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="题目ID" prop="problemId" width="120" align="center" />
          <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
          <el-table-column label="难度" width="100" align="center">
            <template #default="scope">
              <el-tag :type="getDifficultyTagType(scope.row.difficulty)" size="small">
                {{ getDifficultyText(scope.row.difficulty) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="作者" prop="authorUsername" width="120" align="center" />
        </el-table>

        <!-- 分页 -->
        <n-flex justify="center" style="margin-top: 15px;">
          <el-pagination 
            :page-size="problemSearchParams.pageSize" 
            :current-page="problemSearchParams.pageNo" 
            :total="problemSearchTotal"
            :page-sizes="[10, 20, 30]" 
            background 
            layout="total, sizes, prev, pager, next"
            @update:current-page="handleProblemSearchPageChange" 
            @update:page-size="handleProblemSearchSizeChange" 
          />
        </n-flex>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addProblemDialogVisible = false">取消</el-button>
          <el-button
            v-permission="['contest:problem:add']"
            type="primary"
            @click="confirmAddProblems"
            :disabled="problemIds.length === 0"
          >
            添加选中题目 ({{ problemIds.length }})
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑题目信息对话框 -->
    <el-dialog 
      v-model="editProblemDialogVisible" 
      title="编辑比赛题目信息" 
      width="600px" 
      :close-on-click-modal="false"
    >
      <el-form :model="editingProblem" label-width="100px" :rules="editProblemRules" ref="editFormRef">
        <el-form-item label="展示ID" prop="displayId">
          <el-input 
            v-model="editingProblem.displayId" 
            placeholder="如: A, B, C"
            style="width: 200px;"
          />
        </el-form-item>
        <el-form-item label="题目标题" prop="displayTitle">
          <el-input 
            v-model="editingProblem.displayTitle" 
            placeholder="在比赛中显示的题目标题"
          />
        </el-form-item>
        <el-form-item label="气球颜色" prop="color">
          <n-flex align="center" :size="12">
            <el-color-picker v-model="editingProblem.color" />
            <el-input 
              v-model="editingProblem.color" 
              placeholder="#ff4757"
              style="width: 120px;"
            />
          </n-flex>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editProblemDialogVisible = false">取消</el-button>
          <el-button v-permission="['contest:problem:edit']" type="primary" @click="confirmEditProblem">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Edit, Delete } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';
import { Balloon } from '@vicons/ionicons5';
import { 
  getAdminContestProblems, 
  getAvailableProblemsForContest, 
  addProblemsToContest, 
  updateContestProblem, 
  removeContestProblems 
} from '@/api/api';

//data
const route = useRoute();
const contestId = ref<number>(0);
const contestProblemList = ref<any[]>([]);
const contestProblemIds = ref<number[]>([]);
const loading = ref<boolean>(false);
//添加题目相关
const addProblemDialogVisible = ref<boolean>(false);
const availableProblems = ref<any[]>([]);
const problemIds = ref<number[]>([]);
const problemSearchLoading = ref<boolean>(false);
const problemSearchTotal = ref<number>(0);
//编辑题目相关
const editProblemDialogVisible = ref<boolean>(false);
const editingProblem = ref<any>({});
const editFormRef = ref();

//题目搜索参数
const problemSearchParams = ref({
  keyword: '',
  difficulty: '',
  pageNo: 1,
  pageSize: 10,
});

//编辑表单验证规则
const editProblemRules = {
  displayId: [
    { required: true, message: '展示ID不能为空', trigger: 'blur' },
    { max: 5, message: '展示ID长度不能超过5个字符', trigger: 'blur' },
  ],
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
  getAdminContestProblems(contestId.value).then(res => {
    contestProblemList.value = res.data.data || [];
  }).catch(e => {
    console.log(e);
  }).finally(() => {
    loading.value = false;
  });
};
//选择比赛题目记录
const handleSelectionChange = (selection: any[]) => {
  contestProblemIds.value = selection.map(item => item.id);
};
//获取难度文本
const getDifficultyText = (difficulty: number) => {
  switch (difficulty) {
    case 0: return '未评定';
    case 1: return '入门';
    case 2: return '普及';
    case 3: return '提高';
    case 4: return '省选及以上';
    default: return '未知';
  }
};
//获取难度标签类型
const getDifficultyTagType = (difficulty: number) => {
  switch (difficulty) {
    case 0: return 'info';
    case 1: return 'success';
    case 2: return 'warning';
    case 3: return 'danger';
    case 4: return 'danger';
    default: return 'info';
  }
};
//添加题目
const handleAddProblem = () => {
  addProblemDialogVisible.value = true;
  searchProblems();
};
//搜索可用题目
const searchProblems = () => {
  problemSearchLoading.value = true;
  getAvailableProblemsForContest(contestId.value, problemSearchParams.value).then(res => {
    availableProblems.value = res.data.data.records || [];
    problemSearchTotal.value = res.data.data.total || 0;
  }).catch(e => {
    console.log(e);
  }).finally(() => {
    problemSearchLoading.value = false;
  });
};
//题目选择变化
const handleProblemSelectionChange = (selection: any[]) => {
  problemIds.value = selection.map(item => item.id);
};
//确认添加题目
const confirmAddProblems = () => {
  if (problemIds.value.length === 0) {
    ElMessage.warning('请先选择要添加的题目');
    return;
  }
  addProblemsToContest(contestId.value, problemIds.value).then(res => {
    ElMessage.success(res.data.msg);
    addProblemDialogVisible.value = false;
    problemIds.value = [];
    handleQuery();
  }).catch(e => {
    console.log(e);
  });
};
//编辑题目
const handleEditProblem = (row: any) => {
  editingProblem.value = { ...row };
  editProblemDialogVisible.value = true;
};
//确认编辑题目
const confirmEditProblem = () => {
  if (!editFormRef.value) return;
  editFormRef.value.validate((valid: boolean) => {
    if (valid) {
      updateContestProblem(
        contestId.value, 
        editingProblem.value.id, 
        editingProblem.value.displayId, 
        editingProblem.value.displayTitle, 
        editingProblem.value.color
      ).then(res => {
        ElMessage.success(res.data.msg);
        editProblemDialogVisible.value = false;
        handleQuery();
      }).catch(e => {
        console.log(e);
      });
    }
  });
};
//删除题目
const handleDeleteProblem = (row: any) => {
  const ids = row.id || contestProblemIds.value;
  if (!ids) {
    ElMessage.warning('请选择要删除的题目');
    return;
  }
  ElMessageBox.confirm(
    `确认将题目 "${ids}" 从比赛中移除吗？`,
    '移除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    removeContestProblems(contestId.value, ids).then(res => {
      ElMessage.success(res.data.msg);
      handleQuery();
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => { });
};
//题目搜索分页
const handleProblemSearchPageChange = (val: number) => {
  problemSearchParams.value.pageNo = val;
  searchProblems();
};
const handleProblemSearchSizeChange = (val: number) => {
  problemSearchParams.value.pageSize = val;
  searchProblems();
};
</script>

<style scoped lang="css">
.contest-problem-management {
  padding: 24px;
}

.operation-button {
  padding: 0px;
}

.add-problem-content {
  max-height: 600px;
  overflow-y: auto;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: #f8f8f9;
  color: #515a6e;
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
</style>
