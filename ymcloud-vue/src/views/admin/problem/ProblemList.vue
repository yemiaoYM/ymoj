<template>
  <div class="problem-management">
    <n-flex vertical :size="16">
      <el-card>
        <!-- 搜索区域 -->
        <el-form :model="queryParams" label-width="auto">
          <n-flex :size="10">
            <el-form-item label="关键词" prop="keyword">
              <el-input v-model="queryParams.keyword" placeholder="请输入题目标题或题号" clearable />
            </el-form-item>
            <el-form-item label="权限" prop="visibility">
              <el-select v-model="queryParams.visibility" placeholder="全部题目" clearable style="width: 120px;">
                <el-option label="全部题目" value="" />
                <el-option label="公开题目" :value="1" />
                <el-option label="私有题目" :value="2" />
                <el-option label="比赛专用" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="queryParams.difficulty" placeholder="全部题目" clearable style="width: 120px;">
                <el-option label="全部题目" value="" />
                <el-option label="未评定" :value="0" />
                <el-option label="入门" :value="1" />
                <el-option label="普及" :value="2" />
                <el-option label="提高" :value="3" />
                <el-option label="省选及以上" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </n-flex>
        </el-form>
        
        <!-- 操作区域 -->
        <n-flex :size="10" align="center" style="padding-bottom: 15px;">
          <el-button plain type="primary" :icon="Plus" @click="handleAdd">创建</el-button>
          <el-button plain type="success" :icon="Plus" @click="handleAddRemote">添加远程OJ题目</el-button>
          <el-button plain type="danger" :icon="Delete" :disabled="problemIds.length === 0" @click="handleDelete">
            删除
          </el-button>
        </n-flex>
        
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
              <el-select
                :model-value="scope.row.visibility"
                @change="(value: number) => handleVisibilityChange(scope.row, value)"
                style="width: 100px;"
              >
                <el-option :value="1" label="公开" />
                <el-option :value="2" label="私有" />
                <el-option :value="3" label="比赛专用" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center">
            <template #default="scope">
              <n-flex :size="0" justify="center">
                <el-button 
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
                  class="operation-button" 
                  type="danger" 
                  size="small" 
                  text 
                  :icon="Delete"
                  @click="handleDelete(scope.row)"
                >
                  删除
                </el-button>
              </n-flex>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <n-flex justify="center" style="margin-top: 20px;">
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
        </n-flex>
      </el-card>
    </n-flex>

  </div>
</template>

<script lang="ts" setup>
//import
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Refresh, Plus, Edit, Delete, Download } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import type { AdminProblemVO } from '@/type/types';
import {
  getAdminProblemList,
  deleteProblemByIds,
  updateProblemVisibility
} from '@/api/api';

//data
const router = useRouter();
const problemList = ref<AdminProblemVO[]>([] as AdminProblemVO[]);
const problemIds = ref<number[]>([]);
const total = ref<number>(0);
const loading = ref<boolean>(false);

//查询参数
const queryParams = ref({
  keyword: null,
  visibility: null,
  difficulty: null,
  pageNo: 1,
  pageSize: 15,
});


//生命周期
onMounted(() => {
  handleQuery();
});

//methods
//查询题目列表
const handleQuery = () => {
  loading.value = true;
  
  // 调用真实API
  getAdminProblemList(queryParams.value).then(res => {
    problemList.value = res.data.data.records;
    total.value = res.data.data.total;
  }).catch(err => {
    console.error('获取题目列表失败:', err);
    ElMessage.error('获取题目列表失败，请稍后重试');
  }).finally(() => {
    loading.value = false;
  });
};

//重置查询
const resetQuery = () => {
  queryParams.value = {
    keyword: null,
    visibility: null,
    difficulty: null,
    pageNo: 1,
    pageSize: 15,
  };
  handleQuery();
};

//改变页大小
const handleSizeChange = (val: number) => {
  queryParams.value.pageSize = val;
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
    case 1: return '公开题目';
    case 2: return '私有题目';
    case 3: return '比赛专用';
    default: return '未知';
  }
};

//新增题目
const handleAdd = () => {
  router.push('/admin/problem/add');
};

//添加远程OJ题目
const handleAddRemote = () => {
  ElMessage.info('远程OJ题目添加功能待开发');
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
const handleVisibilityChange = (row: AdminProblemVO, newVisibility: number) => {
  //如果值没有变化，直接返回
  if (row.visibility === newVisibility) {
    return;
  }
  const visibilityText = getVisibilityText(newVisibility);
  const currentText = getVisibilityText(row.visibility);
  
  ElMessageBox.confirm(
    `确认将题目 "${row.title}" 的权限从 "${currentText}" 修改为 "${visibilityText}" 吗？`,
    '权限修改确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    updateProblemVisibility(row.id, newVisibility).then(res => {
      ElMessage.success(res.data.msg);
      row.visibility = newVisibility;
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => {  });
};

</script>

<style scoped lang="css">
.problem-management {
  padding: 24px;
}

.operation-button {
  padding: 0px;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: #f8f8f9;
  color: #515a6e;
  height: 40px;
  font-size: 13px;
  text-align: center;
}
</style>