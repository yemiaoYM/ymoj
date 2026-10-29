<template>
  <div class="contest-management">
    <x-flex vertical :size="16">
      <el-card>
        <!-- 搜索区域 -->
        <el-form :model="queryParams" label-width="auto">
          <x-flex :size="10">
            <el-form-item label="关键词" prop="keyword">
              <el-input v-model="queryParams.keyword" placeholder="请输入比赛标题或创建者" clearable />
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
            v-permission="['contest:contest:add']"
            plain
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >
            创建比赛
          </el-button>
          <el-button
            v-permission="['contest:contest:remove']"
            plain
            type="danger"
            :icon="Delete"
            :disabled="contestIds.length === 0"
            @click="handleDelete"
          >
            删除
          </el-button>
        </x-flex>
        
        <!-- 比赛列表 -->
        <el-table :data="contestList" v-loading="loading" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="ID" prop="id" width="60" align="center" />
          <el-table-column label="比赛标题" prop="title" min-width="150" show-overflow-tooltip align="center"/>
          <el-table-column label="创建者" prop="creatorUsername" width="120" align="center" />
          <el-table-column label="赛制模式" width="80" align="center">
            <template #default="scope">
              <el-tag :type="getModeTagType(scope.row.mode)">
                {{ getModeText(scope.row.mode) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="比赛状态" width="80" align="center">
            <template #default="scope">
              <el-tag :type="getStatusTagType(scope.row)">
                {{ getContestStatus(scope.row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" prop="startTime" width="160" align="center" />
          <el-table-column label="结束时间" prop="endTime" width="160" align="center" />
          <el-table-column label="时长(分钟)" prop="duration" width="100" align="center" />
          <el-table-column label="访问权限" width="120" align="center">
            <template #default="scope">
              <AsyncSelect
                v-model="scope.row.visibility"
                :disabled="!userStore.hasEveryPermission(['contest:contest:edit'])"
                :before-change="(value: number) => handleVisibilityChange(scope.row, value)"
                style="width: 80px;"
              >
                <el-option :value="0" label="公开" />
                <el-option :value="1" label="私有" />
              </AsyncSelect>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" align="center">
            <template #default="scope">
              <x-flex :size="0" justify="center">
                <el-button
                  v-permission="['contest:contest:query', 'contest:contest:edit']"
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
                  v-permission="['contest:problem:list']"
                  class="operation-button" 
                  type="info" 
                  size="small" 
                  text 
                  :icon="List"
                  @click="handleManageProblems(scope.row)"
                >
                  管理题目
                </el-button>
                <el-button
                  v-permission="['contest:contest:add']"
                  class="operation-button" 
                  type="success" 
                  size="small" 
                  text 
                  :icon="CopyDocument"
                  @click="handleClone(scope.row)"
                >
                  克隆
                </el-button>
                <el-button
                  v-permission="['contest:contest:remove']"
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
  </div>
</template>

<script lang="ts" setup>
//import
import AsyncSelect from '@/components/common/AsyncSelect.vue';
import { useUserStore } from '@/stores/user';
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Refresh, Plus, Edit, Delete, CopyDocument, List } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import {
  getAdminContestList,
  deleteContestByIds,
  updateContestVisibility,
  cloneAdminContest
} from '@/api/api';

//data
const userStore = useUserStore();
const router = useRouter();
const contestList = ref<any[]>([]);
const contestIds = ref<number[]>([]);
const total = ref<number>(0);
const loading = ref<boolean>(false);

//查询参数
const queryParams = ref({
  keyword: null,
  pageNo: 1,
  pageSize: 15,
});

//生命周期
onMounted(() => {
  handleQuery();
});

//methods
//查询比赛列表
const handleQuery = () => {
  loading.value = true;
  getAdminContestList(queryParams.value).then(res => {
    contestList.value = res.data.data.records;
    total.value = res.data.data.total;
  }).catch(e => {
    console.log(e);
  }).finally(() => {
    loading.value = false;
  });
};
//重置查询
const resetQuery = () => {
  queryParams.value = {
    keyword: null,
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
//选择比赛
const handleSelectionChange = (selection: any[]) => {
  contestIds.value = selection.map(item => item.id);
};

//获取赛制模式文本
const getModeText = (mode: number) => {
  switch (mode) {
    case 0: return 'ACM';
    case 1: return 'OI';
    case 2: return 'IOI';
    default: return '未知';
  }
};
//获取赛制模式标签类型
const getModeTagType = (mode: number) => {
  switch (mode) {
    case 0: return 'primary';
    case 1: return 'success';
    case 2: return 'warning';
    default: return 'primary';
  }
};
//获取比赛状态
const getContestStatus = (contest: any) => {
  const now = new Date();
  const startTime = new Date(contest.startTime);
  const endTime = new Date(contest.endTime);
  if (now < startTime) {
    return '未开始';
  } else if (now >= startTime && now <= endTime) {
    return '进行中';
  } else {
    return '已结束';
  }
};
//获取状态标签类型
const getStatusTagType = (contest: any) => {
  const status = getContestStatus(contest);
  switch (status) {
    case '未开始': return 'info';
    case '进行中': return 'success';
    case '已结束': return 'danger';
    default: return 'info';
  }
};

//获取可见性文本
const getVisibilityText = (visibility: number) => {
  switch (visibility) {
    case 0: return '公开';
    case 1: return '私有';
    default: return '未知';
  }
};
//新增比赛
const handleAdd = () => {
  router.push('/admin/contest/add');
};
//编辑比赛
const handleUpdate = (row: any) => {
  const id = row.id || contestIds.value[0];
  if (!id) {
    ElMessage.warning('请选择要编辑的比赛');
    return;
  }
  router.push(`/admin/contest/edit/${id}`);
};
//管理比赛题目
const handleManageProblems = (row: any) => {
  const id = row.id;
  if (!id) {
    ElMessage.warning('比赛ID不存在');
    return;
  }
  router.push(`/admin/contest/${id}/problems`);
};
//克隆比赛
const handleClone = (row: any) => {
  handleCloneWithDialog(row);
};

//克隆比赛对话框
const handleCloneWithDialog = (row: any) => {
  // 默认标题
  const defaultTitle = `${row.title} (克隆)`;
  
  ElMessageBox.prompt(
    `请输入新比赛标题：`,
    '克隆比赛设置',
    {
      confirmButtonText: '下一步',
      cancelButtonText: '取消',
      inputValue: defaultTitle,
      inputPlaceholder: '请输入比赛标题',
      inputValidator: (value: string) => {
        if (!value || value.trim() === '') {
          return '比赛标题不能为空';
        }
        if (value.length > 100) {
          return '比赛标题长度不能超过100个字符';
        }
        return true;
      },
    }
  ).then(({ value: title }) => {
    // 弹出权限设置对话框
    handleCloneVisibilityDialog(row, title);
  }).catch(() => { });
};

//克隆比赛权限设置对话框
const handleCloneVisibilityDialog = (row: any, title: string) => {
  ElMessageBox.confirm(
    `比赛 "${title}" 的访问权限设置：`,
    '选择访问权限',
    {
      confirmButtonText: '私有（需设置密码）',
      cancelButtonText: '公开',
      distinguishCancelAndClose: true,
      type: 'info',
    }
  ).then(() => {
    // 用户选择私有，需要设置密码
    handleClonePasswordDialog(row, title, 1);
  }).catch((action: string) => {
    if (action === 'cancel') {
      // 用户选择公开
      executeClone(row.id, title, 0, null);
    }
    // close 表示关闭对话框，不执行任何操作
  });
};

//克隆比赛密码设置对话框
const handleClonePasswordDialog = (row: any, title: string, visibility: number) => {
  ElMessageBox.prompt(
    `私有比赛 "${title}" 需要设置访问密码：`,
    '设置比赛密码',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPlaceholder: '请输入5-20位密码',
      inputValidator: (value: string) => {
        if (!value || value.trim() === '') {
          return '密码不能为空';
        }
        if (value.length < 5) {
          return '密码长度至少5位';
        }
        if (value.length > 20) {
          return '密码长度不能超过20位';
        }
        return true;
      },
    }
  ).then(({ value: password }) => {
    executeClone(row.id, title, visibility, password);
  }).catch(() => { });
};

//执行克隆操作
const executeClone = (id: number, title: string, visibility: number, password: string | null) => {
  cloneAdminContest(id, title, visibility, password).then(res => {
    ElMessage.success(res.data.msg);
    handleQuery();
  }).catch(e => {
    console.log(e);
    ElMessage.error('克隆失败');
  });
};
//删除比赛
const handleDelete = (row: any) => {
  const ids = row.id || contestIds.value;
  if (!ids) {
    ElMessage.warning('请选择要删除的比赛');
    return;
  }
  ElMessageBox.confirm(
    `是否确认删除比赛编号为 "${ids}" 的数据项吗？`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    deleteContestByIds(ids).then(res => {
      ElMessage.success(res.data.msg);
      handleQuery();
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => { });
};
//请求成功后由异步选择器更新权限
const handleVisibilityChange = async (row: { id: number; title: string; visibility: number }, newVisibility: number) => {
  let password: string | null = null;
  if (newVisibility === 1) {
    const result = await ElMessageBox.prompt(
      `将比赛 "${row.title}" 设置为私有需要设置访问密码：`,
      '设置比赛密码',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入5-20位密码',
        inputValidator: (value: string) => {
          if (!value || value.trim() === '') return '密码不能为空';
          if (value.length < 5 || value.length > 20) return '密码长度应为5-20位';
          return true;
        },
      }
    );
    password = result.value;
  } else {
    await ElMessageBox.confirm(
      `确认将比赛 "${row.title}" 的访问权限从 "${getVisibilityText(row.visibility)}" 修改为 "${getVisibilityText(newVisibility)}" 吗？`,
      '权限修改确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    );
  }
  const response = await updateContestVisibility(row.id, newVisibility, password);
  ElMessage.success(response.data.msg);
};
</script>

<style scoped lang="css">
.contest-management {
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
