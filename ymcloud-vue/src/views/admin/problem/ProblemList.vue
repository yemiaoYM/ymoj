<template>
  <div class="problem-management">
    <n-flex vertical :size="16">
      <el-card>
        <!-- 搜索区域 -->
        <el-form :model="queryParams" label-width="auto">
          <n-flex :size="10">
            <el-form-item label="题目标题" prop="title">
              <el-input v-model="queryParams.title" placeholder="请输入题目标题" clearable />
            </el-form-item>
            <el-form-item label="作者" prop="authorUsername">
              <el-input v-model="queryParams.authorUsername" placeholder="请输入作者" clearable />
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
          <el-table-column label="最近修改者" prop="updatedBy" width="120" align="center" />
          <el-table-column label="权限" width="100" align="center">
            <template #default="scope">
              <el-tag 
                :type="getVisibilityTagType(scope.row.visibility)"
                size="small"
              >
                {{ getVisibilityText(scope.row.visibility) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Option" width="200" align="center">
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
  deleteProblemByIds
} from '@/api/api';

//data
const router = useRouter();
const problemList = ref<AdminProblemVO[]>([] as AdminProblemVO[]);
const problemIds = ref<number[]>([]);
const total = ref<number>(0);
const loading = ref<boolean>(false);

//查询参数
const queryParams = ref({
  title: null,
  authorUsername: null,
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
  
  // 临时模拟数据，匹配图片中的数据
  setTimeout(() => {
    problemList.value = [
      {
        id: 20189,
        problemId: 'GYM-103388K',
        title: 'Kathmandu',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:21',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20190,
        problemId: 'GYM-103388I',
        title: 'Inverting Everything',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:21',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20188,
        problemId: 'GYM-103388G',
        title: 'Getting in Shape',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:20',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20187,
        problemId: 'GYM-103388F',
        title: 'Freedom from Prison',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:20',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20186,
        problemId: 'GYM-103388E',
        title: 'Escalator',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:19',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20185,
        problemId: 'GYM-103388D',
        title: 'Dividing the Kingdom',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:17',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20183,
        problemId: 'GYM-103388B',
        title: 'Beautiful Words',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:16',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20184,
        problemId: 'GYM-103388C',
        title: 'Creating Multiples',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:16',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20182,
        problemId: 'GYM-103388A',
        title: 'Assigning Prizes',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 16:42:14',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      },
      {
        id: 20181,
        problemId: 'GYM-103389I',
        title: '雪球卡丁车',
        authorUsername: 'zsh',
        createdTime: '2021-11-08 10:51:48',
        updatedBy: 'zsh',
        visibility: 1,
        isRemote: false,
        difficulty: 0
      }
    ];
    total.value = 10;
    loading.value = false;
  }, 500);
  
  // 实际API调用（注释掉以使用模拟数据）
  // getAdminProblemList(queryParams.value).then(res => {
  //   problemList.value = res.data.data.records;
  //   total.value = res.data.data.total;
  // }).finally(() => {
  //   loading.value = false;
  // });
};

//重置查询
const resetQuery = () => {
  queryParams.value = {
    title: null,
    authorUsername: null,
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

//获取权限标签类型
const getVisibilityTagType = (visibility: number) => {
  switch (visibility) {
    case 1: return 'success';
    case 2: return 'warning';
    case 3: return 'info';
    default: return '';
  }
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

//下载题目
const handleDownload = (row: AdminProblemVO) => {
  ElMessage.info('题目下载功能待开发');
};

//删除题目
const handleDelete = (row?: AdminProblemVO) => {
  const ids = row ? row.id : problemIds.value;
  if (!ids || (Array.isArray(ids) && ids.length === 0)) {
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