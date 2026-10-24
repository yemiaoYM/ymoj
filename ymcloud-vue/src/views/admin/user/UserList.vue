<template>
  <div class="user-management">
    <x-flex vertical :size="16">
      <el-card>
        <!-- 搜索区域 -->
        <el-form :model="queryParams" ref="queryFormRef" label-width="auto">
          <x-flex :size="10">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="queryParams.email" placeholder="请输入邮箱" clearable />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px;">
                <el-option label="正常" :value="1" />
                <el-option label="停用" :value="0" />
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
            v-permission="['user:user:add']"
            plain
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >
            新增
          </el-button>
          <el-button
            v-permission="['user:user:query', 'user:user:edit']"
            plain
            type="success"
            :icon="Edit"
            :disabled="userIds.length !== 1"
            @click="handleUpdate"
          >
            修改
          </el-button>
          <el-button
            v-permission="['user:user:remove']"
            plain
            type="danger"
            :icon="Delete"
            :disabled="userIds.length === 0"
            @click="handleDelete"
          >
            删除
          </el-button>
        </x-flex>
        <!-- 用户列表 -->
        <el-table :data="userList" v-loading="loading" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="用户ID" prop="id" width="80" align="center" />
          <el-table-column label="头像" width="80" align="center">
            <template #default="scope">
              <Avatar :src="scope.row.avatar" :size="40" :text="scope.row.username" />
            </template>
          </el-table-column>
          <el-table-column label="用户名" prop="username" min-width="120" show-overflow-tooltip align="center" />
          <el-table-column label="邮箱" prop="email" min-width="180" show-overflow-tooltip align="center" />
          <el-table-column label="学校" prop="school" min-width="150" show-overflow-tooltip align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="scope">
              <el-switch
                v-model="scope.row.status"
                :disabled="scope.row.id === 1 || !userStore.hasEveryPermission(['user:user:edit'])"
                active-value="正常"
                inactive-value="停用"
                @change="handleStatusChange(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="创建时间" prop="createdTime" width="160" align="center" />
          <el-table-column label="操作" min-width="200" align="center">
            <template #default="scope">
              <x-flex :size="0" justify="center" v-if="scope.row.id !== 1">
                <el-button
                  v-permission="['user:user:query', 'user:user:edit']"
                  class="operation-button"
                  type="success"
                  size="small"
                  text
                  :icon="Edit"
                  @click="handleUpdate(scope.row)"
                >
                  修改
                </el-button>
                <el-button
                  v-permission="['user:user:edit']"
                  class="operation-button"
                  type="primary"
                  size="small"
                  text
                  :icon="Edit"
                  @click="handleResetPwd(scope.row)"
                >
                  重置密码
                </el-button>
                <el-button
                  v-permission="['user:user:role']"
                  class="operation-button"
                  type="primary"
                  size="small"
                  text
                  :icon="Setting"
                  :disabled="scope.row.id === userStore.user.id"
                  @click="handleAssignRoles(scope.row)"
                >
                  分配角色
                </el-button>
                <el-button
                  v-permission="['user:user:remove']"
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
          <el-pagination :page-size="queryParams.pageSize" :current-page="queryParams.pageNo" :total="total"
            :page-sizes="[15, 25, 50]" background layout="total, sizes, prev, pager, next"
            @update:current-page="handleCurrentChange" @update:page-size="handleSizeChange" />
        </x-flex>
      </el-card>
    </x-flex>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" :close-on-click-modal="false" destroy-on-close
      top="5vh" append-to-body>
      <el-form :model="userForm" :rules="userRules" ref="userFormRef" label-width="auto">
        <x-flex vertical :size="16">
          <x-flex :size="16">
            <el-form-item label="用户名" prop="username" style="flex: 1;">
              <el-input v-model="userForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname" style="flex: 1;">
              <el-input v-model="userForm.nickname" placeholder="请输入昵称" />
            </el-form-item>
          </x-flex>

          <x-flex :size="16">
            <el-form-item label="邮箱" prop="email" style="flex: 1;">
              <el-input v-model="userForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="性别" prop="sex" style="flex: 1;">
              <el-radio-group v-model="userForm.sex">
                <el-radio label="保密" value="保密">保密</el-radio>
                <el-radio label="男" value="男">男</el-radio>
                <el-radio label="女" value="女">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </x-flex>

          <el-form-item label="学校" prop="school">
            <el-input v-model="userForm.school" placeholder="请输入学校" />
          </el-form-item>

          <el-form-item label="个性签名" prop="signature">
            <el-input v-model="userForm.signature" type="textarea" :rows="3" placeholder="请输入个性签名" maxlength="65535"
              show-word-limit />
          </el-form-item>

          <el-form-item label="博客链接" prop="blog">
            <el-input v-model="userForm.blog" placeholder="请输入博客链接" />
          </el-form-item>

          <el-form-item v-if="userForm.id !== null" label="头像" prop="avatar">
            <x-flex style="width: 100%;">
              <Avatar :src="userForm.avatar" :text="userForm.username" :size="80" />
              <el-input v-model="userForm.avatar" disabled></el-input>
              <!-- 重置头像 -->
              <el-button type="primary" @click="userForm.avatar = null">重置头像</el-button>
            </x-flex>
          </el-form-item>

          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="userForm.status">
              <el-radio label="正常" value="正常">正常</el-radio>
              <el-radio label="停用" value="停用">停用</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="userForm.id === null" label="密码" prop="password">
            <el-input v-model="userForm.password" type="password" placeholder="请输入密码" show-password
              autocomplete="new-password" />
          </el-form-item>
        </x-flex>
      </el-form>

      <template #footer>
        <x-flex justify="end" :size="12">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            v-permission="userForm.id === null ? ['user:user:add'] : ['user:user:edit']"
            type="primary"
            @click="handleSubmit"
            :loading="submitLoading"
          >
            确定
          </el-button>
        </x-flex>
      </template>
    </el-dialog>

    <!-- 用户角色分配 -->
    <el-dialog
      v-model="roleDialogVisible"
      :title="`分配角色：${roleUser?.username || ''}`"
      width="min(600px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      destroy-on-close
      append-to-body
    >
      <el-form v-loading="roleLoading" label-width="80px" :disabled="roleSaving || !roleLoaded">
        <el-form-item label="用户角色">
          <el-select v-model="selectedRoleIds" multiple filterable clearable placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="`${role.name} (${role.code})${role.status === 0 ? '（已停用）' : ''}`"
              :value="role.id"
              :disabled="isRoleOptionDisabled(role)"
            />
          </el-select>
        </el-form-item>
        <el-text type="info">角色权限将在用户下一次请求时生效。清空角色后，该用户将失去对应权限。</el-text>
      </el-form>
      <template #footer>
        <el-button :disabled="roleSaving" @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" :disabled="!roleLoaded" @click="handleSaveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
//import
import { ref, reactive, onMounted, nextTick, defineAsyncComponent } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus';
import { Search, Refresh, Plus, Edit, Delete, Setting } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { getUserRoleOptions, getUserRoleIds, updateUserRoles } from '@/api/rbac';
import type { RoleVO } from '@/type/rbac';
import type { User, UserDTO } from '@/type/types';
import { copyProperties } from '@/utils/common';
import {
  getUserTableList,
  getUserById,
  deleteUserByIds,
  changeUserStatus,
  resetUserPassword,
  addUser,
  updateUser
} from '@/api/api';

//异步导入头像组件
const Avatar = defineAsyncComponent(() => import('@/components/common/Avatar.vue'));

//data
const userStore = useUserStore();
const roleDialogVisible = ref(false);
const roleLoading = ref(false);
const roleLoaded = ref(false);
const roleSaving = ref(false);
const roleUser = ref<User | null>(null);
const roleOptions = ref<RoleVO[]>([]);
const selectedRoleIds = ref<number[]>([]);
const queryFormRef = ref<FormInstance>();
const userFormRef = ref<FormInstance>();
const userList = ref<User[]>([] as User[]);
const userIds = ref<number[]>([]);
const total = ref<number>(0);
const loading = ref<boolean>(false);
//查询参数
const queryParams = ref({
  username: null,
  email: null,
  status: null,
  pageNo: 1,
  pageSize: 15,
});

//弹窗相关
const dialogVisible = ref(false);
const dialogTitle = ref('');
const submitLoading = ref(false);
//用户表单
const userForm = reactive<UserDTO>({
  id: null,
  username: '',
  nickname: null,
  password: '',
  sex: '保密',
  email: '',
  avatar: null,
  school: null,
  signature: '<p>这个家伙很懒，什么也没写…<p>',
  blog: null,
  status: '正常',
});
//表单验证规则
const userRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  nickname: [
    { required: false, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度在 5 到 20 个字符', trigger: 'blur' }
  ],
  sex: [
    { required: true, message: '请选择性别', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
};

//生命周期
onMounted(() => {
  handleQuery();
});

//methods
//已分配的停用角色允许移除，移除后不可重新选择
const isRoleOptionDisabled = (role: RoleVO) => {
  return (role.status === 0 && !selectedRoleIds.value.includes(role.id))
    || (role.code === 'ADMIN' && userStore.user.id !== 1);
};

//分配用户角色
const handleAssignRoles = async (row: User) => {
  roleUser.value = row;
  roleLoaded.value = false;
  roleLoading.value = true;
  selectedRoleIds.value = [];
  roleDialogVisible.value = true;
  try {
    const [options, selected] = await Promise.all([getUserRoleOptions(row.id), getUserRoleIds(row.id)]);
    if (roleUser.value?.id !== row.id) {
      return;
    }
    roleOptions.value = options.data.data;
    selectedRoleIds.value = selected.data.data;
    roleLoaded.value = true;
  } catch (e) {
    console.error(e);
  } finally {
    roleLoading.value = false;
  }
};

//保存用户角色
const handleSaveRoles = async () => {
  if (!roleLoaded.value || !roleUser.value || roleSaving.value) {
    return;
  }
  roleSaving.value = true;
  try {
    await updateUserRoles(roleUser.value.id, [...selectedRoleIds.value]);
    ElMessage.success('分配角色成功');
    roleDialogVisible.value = false;
  } catch (e) {
    console.error(e);
  } finally {
    roleSaving.value = false;
  }
};
//查询用户列表
const handleQuery = () => {
  loading.value = true;
  getUserTableList(queryParams.value).then(res => {
    userList.value = res.data.data.records;
    total.value = res.data.data.total;
  }).finally(() => {
    loading.value = false;
  });
};
//重置查询
const resetQuery = () => {
  queryParams.value = {
    username: null,
    email: null,
    status: null,
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
//选择用户
const handleSelectionChange = (selection: User[]) => {
  userIds.value = selection.map(item => item.id);
};
//修改用户状态
const handleStatusChange = (row: User) => {
  ElMessageBox.confirm(
    `确定要将用户 "${row.username}" 的状态改为 "${row.status}" 吗？`,
    '状态修改确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    changeUserStatus(row.id, row.status).then(res => {
      ElMessage.success(res.data.msg);
    }).catch(e => {
      row.status = row.status === '正常' ? '停用' : '正常';
      console.log(e);
    });
  }).catch(() => {
    row.status = row.status === '正常' ? '停用' : '正常';
  })
};
//新增用户
const handleAdd = () => {
  reset();
  dialogTitle.value = '新增用户';
  dialogVisible.value = true;
  userForm.password = '12345';
};
//编辑用户
const handleUpdate = (row: User) => {
  const id = row.id || userIds.value[0];
  if (!id) {
    ElMessage.warning('请选择要编辑的用户');
    return;
  }
  reset();
  getUserById(id).then(res => {
    copyProperties(userForm, res.data.data);
    dialogTitle.value = '编辑用户';
    dialogVisible.value = true;
  }).catch(e => {
    console.log(e);
  });
};
//重置密码
const handleResetPwd = (row: User) => {
  ElMessageBox.prompt(
    `请输入 "${row.username}" 的新密码`,
    "提示",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      closeOnClickModal: false,
      inputPattern: /^.{5,20}$/,
      inputErrorMessage: "用户密码长度必须介于 5 和 20 之间",
      inputValidator: (value) => {
        if (/<|>|"|'|\||\\/.test(value)) {
          return "不能包含非法字符：< > \" ' \\\ |"
        }
        return true;
      }
    }
  ).then(({ value }) => {
    resetUserPassword(row.id, value).then(res => {
      ElMessage.success(res.data.msg);
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => { });
}
//删除用户
const handleDelete = (row: User) => {
  const ids = row.id || userIds.value;
  if (!ids) {
    ElMessage.warning('请选择要删除的用户');
    return;
  }
  ElMessageBox.confirm(
    `是否确认删除用户编号为 "${ids}" 的数据项吗？`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    deleteUserByIds(ids).then(res => {
      ElMessage.success(res.data.msg);
      handleQuery();
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => { });
};
//重置表单
const reset = () => {
  Object.assign(userForm, {
    id: null,
    username: '',
    nickname: null,
    password: '',
    sex: '保密',
    email: '',
    avatar: null,
    school: null,
    signature: '<p>这个家伙很懒，什么也没写…<p>',
    blog: null,
    status: '正常',
  });
  nextTick(() => {
    if (userFormRef.value) {
      userFormRef.value.resetFields();
    }
  });
};
//提交表单
const handleSubmit = () => {
  nextTick(() => {
    userFormRef.value?.validate(valid => {
      if (valid) {
        submitLoading.value = true;
        if (userForm.id === null) {
          addUser(userForm).then(res => {
            ElMessage.success(res.data.msg);
            handleQuery();
          }).catch(e => {
            console.log(e);
          });
        } else {
          updateUser(userForm).then(res => {
            ElMessage.success(res.data.msg);
            handleQuery();
          }).catch(e => {
            console.log(e);
          });
        }
        submitLoading.value = false;
        dialogVisible.value = false;
      } else {
        ElMessage.warning('请填写完整的用户信息');
      }
    });
  });
};
</script>

<style scoped lang="css">
.user-management {
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
