<template>
  <div class="role-management">
    <x-flex vertical :size="16">
      <el-card>
        <el-form :model="queryParams" label-width="auto">
          <x-flex :size="10">
            <el-form-item label="角色名称">
              <el-input v-model="queryParams.name" placeholder="请输入角色名称" clearable />
            </el-form-item>
            <el-form-item label="角色编码">
              <el-input v-model="queryParams.code" placeholder="请输入角色编码" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="queryParams.status"
                placeholder="请选择状态"
                clearable
                style="width: 120px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </x-flex>
        </el-form>
        <x-flex :size="10" style="padding-bottom: 15px">
          <el-button
            v-if="userStore.isSuperAdmin"
            v-permission="['system:role:add']"
            plain
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >
            新增
          </el-button>
        </x-flex>
        <el-table :data="roleList" v-loading="loading">
          <el-table-column label="ID" prop="id" width="80" align="center" />
          <el-table-column label="角色名称" prop="name" min-width="130" />
          <el-table-column label="角色编码" prop="code" min-width="160" />
          <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.builtIn ? 'info' : 'primary'">
                {{ row.builtIn ? '系统内置' : '自定义' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="230" align="center">
            <template #default="{ row }">
              <x-flex v-if="userStore.isSuperAdmin" :size="0" justify="center">
                <el-button
                  v-permission="['system:role:query', 'system:role:edit']"
                  type="success"
                  size="small"
                  text
                  :icon="Edit"
                  @click="handleUpdate(row)"
                >
                  修改
                </el-button>
                <el-button
                  v-permission="['system:role:auth']"
                  type="primary"
                  size="small"
                  text
                  :icon="Setting"
                  @click="handleAuth(row)"
                >
                  分配权限
                </el-button>
                <el-button
                  v-permission="['system:role:remove']"
                  type="danger"
                  size="small"
                  text
                  :icon="Delete"
                  :disabled="row.builtIn"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
        </el-table>
        <x-flex justify="center" style="margin-top: 20px">
          <el-pagination
            v-model:current-page="queryParams.pageNo"
            v-model:page-size="queryParams.pageSize"
            :total="total"
            :page-sizes="[15, 25, 50]"
            background
            layout="total, sizes, prev, pager, next"
            @current-change="loadRoles"
            @size-change="handleQuery"
          />
        </x-flex>
      </el-card>
    </x-flex>
    <el-dialog
      v-model="dialogVisible"
      :title="editingRole ? '编辑角色' : '新增角色'"
      width="min(600px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      destroy-on-close
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" :disabled="saving">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="!!editingRole"
            maxlength="64"
            placeholder="例如 CONTENT_EDITOR"
          />
        </el-form-item>
        <el-form-item label="角色描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status" :disabled="!!editingRole?.builtIn">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button
          v-if="userStore.isSuperAdmin"
          v-permission="editingRole ? ['system:role:edit'] : ['system:role:add']"
          type="primary"
          :loading="saving"
          @click="handleSubmit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="authDialogVisible"
      :title="`分配权限：${authRole?.name || ''}`"
      width="min(650px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      destroy-on-close
      append-to-body
    >
      <el-alert
        title="勾选“进入管理后台”后可访问后台。勾选或取消父菜单会同步操作下级权限，也可单独取消不需要的权限。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-input
        v-model="authKeyword"
        placeholder="请输入菜单名称或权限编码"
        clearable
        style="margin-bottom: 16px"
      />
      <div v-loading="authLoading">
        <el-scrollbar max-height="400px">
          <el-tree
            ref="treeRef"
            :data="authTree"
            node-key="id"
            show-checkbox
            check-strictly
            default-expand-all
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            :filter-node-method="filterAuth"
            @check="handleAuthCheck"
          >
            <template #default="{ data }">
              <span>
                {{ data.name }}
                <el-text v-if="data.code" type="info" size="small" style="margin-left: 8px">
                  {{ data.code }}
                </el-text>
                <el-tag
                  v-if="data.status === 0"
                  type="danger"
                  size="small"
                  style="margin-left: 8px"
                >
                  停用
                </el-tag>
              </span>
            </template>
          </el-tree>
        </el-scrollbar>
        <el-empty v-if="!authLoading && !authLoaded" description="权限加载失败，请关闭后重试" />
      </div>
      <template #footer>
        <el-button :disabled="authSaving" @click="authDialogVisible = false">取消</el-button>
        <el-button
          v-if="userStore.isSuperAdmin"
          v-permission="['system:role:auth']"
          type="primary"
          :loading="authSaving"
          :disabled="!authLoaded"
          @click="handleSaveAuth"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, onMounted, nextTick, watch } from 'vue';
import { ElMessage, ElMessageBox, ElTree, type FormInstance } from 'element-plus';
import { Search, Refresh, Plus, Edit, Delete, Setting } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import {
  getRoleList,
  getRoleById,
  addRole,
  updateRole,
  deleteRole,
  getRoleAuthOptions,
  getRoleAuthIds,
  updateRoleAuths,
} from '@/api/system/rbac';
import type { RoleVO, RoleDTO, RolePageQuery, AuthTreeNode } from '@/types/system/rbac';
import { buildAuthTree } from '@/utils/rbac';

//data
const userStore = useUserStore();
const queryParams = ref<RolePageQuery>({
  name: '',
  code: '',
  status: null,
  pageNo: 1,
  pageSize: 15,
});
const roleList = ref<RoleVO[]>([]);
const total = ref(0);
const loading = ref(false);
const dialogVisible = ref(false);
const saving = ref(false);
const editingRole = ref<RoleVO | null>(null);
const formRef = ref<FormInstance>();
const form = ref<RoleDTO>({
  name: '',
  code: '',
  description: '',
  status: 1,
});
const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '只能包含大写字母、数字和下划线', trigger: 'blur' },
  ],
};
const authRole = ref<RoleVO | null>(null);
const authDialogVisible = ref(false);
const authLoading = ref(false);
const authLoaded = ref(false);
const authSaving = ref(false);
const authKeyword = ref('');
const authTree = ref<AuthTreeNode[]>([]);
const treeRef = ref<InstanceType<typeof ElTree>>();

//methods
//查询角色列表
const loadRoles = async () => {
  loading.value = true;
  try {
    const res = await getRoleList(queryParams.value);
    roleList.value = res.data.data.records;
    total.value = res.data.data.total;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

//搜索角色
const handleQuery = () => {
  queryParams.value.pageNo = 1;
  loadRoles();
};

//重置查询条件
const resetQuery = () => {
  queryParams.value = {
    name: '',
    code: '',
    status: null,
    pageNo: 1,
    pageSize: 15,
  };
  loadRoles();
};

//新增角色
const handleAdd = () => {
  editingRole.value = null;
  form.value = {
    name: '',
    code: '',
    description: '',
    status: 1,
  };
  dialogVisible.value = true;
};

//编辑角色
const handleUpdate = async (row: RoleVO) => {
  try {
    const res = await getRoleById(row.id);
    editingRole.value = res.data.data;
    const role = res.data.data as RoleVO;
    form.value = {
      name: role.name,
      code: role.code,
      description: role.description,
      status: role.status,
    };
    dialogVisible.value = true;
  } catch (e) {
    console.error(e);
  }
};

//提交角色表单
const handleSubmit = async () => {
  if (saving.value) {
    return;
  }
  try {
    if (!(await formRef.value?.validate())) {
      return;
    }
    saving.value = true;
    if (editingRole.value) {
      await updateRole(editingRole.value.id, form.value);
    } else {
      await addRole(form.value);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadRoles();
  } catch (e) {
    console.error(e);
  } finally {
    saving.value = false;
  }
};

//删除角色
const handleDelete = async (row: RoleVO) => {
  try {
    await ElMessageBox.confirm(`是否确认删除角色“${row.name}”？`, '删除确认', { type: 'warning' });
    await deleteRole(row.id);
    ElMessage.success('删除成功');
    await loadRoles();
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error(e);
    }
  }
};
//根据名称或编码过滤权限树
const filterAuth = (keyword: string, node: Record<string, unknown>) => {
  return !keyword
    || String(node.name || '').includes(keyword)
    || String(node.code || '').includes(keyword);
};
watch(authKeyword, value => treeRef.value?.filter(value));

//加载角色权限
const handleAuth = async (row: RoleVO) => {
  authRole.value = row;
  authTree.value = [];
  authKeyword.value = '';
  authLoaded.value = false;
  authLoading.value = true;
  authDialogVisible.value = true;
  try {
    const [options, checked] = await Promise.all([getRoleAuthOptions(), getRoleAuthIds(row.id)]);
    authTree.value = buildAuthTree(options.data.data);
    await nextTick();
    treeRef.value?.setCheckedKeys(checked.data.data);
    authLoaded.value = true;
  } catch (e) {
    console.error(e);
  } finally {
    authLoading.value = false;
  }
};

//同步勾选或取消下级权限
const setAuthChildrenChecked = (nodes: AuthTreeNode[], checked: boolean) => {
  nodes.forEach(node => {
    treeRef.value?.setChecked(node.id, checked, false);
    setAuthChildrenChecked(node.children, checked);
  });
};

//父菜单联动下级，单独取消子项时不反向勾选其他权限
const handleAuthCheck = (node: AuthTreeNode) => {
  const checked = treeRef.value?.getCheckedKeys().includes(node.id) || false;
  setAuthChildrenChecked(node.children, checked);
};

//保存角色权限
const handleSaveAuth = async () => {
  if (!authLoaded.value || !authRole.value || authSaving.value) {
    return;
  }
  authSaving.value = true;
  try {
    await updateRoleAuths(authRole.value.id, (treeRef.value?.getCheckedKeys() || []).map(Number));
    ElMessage.success('分配权限成功');
    authDialogVisible.value = false;
  } catch (e) {
    console.error(e);
  } finally {
    authSaving.value = false;
  }
};
//生命周期
onMounted(() => {
  loadRoles();
});
</script>

<style scoped>
.role-management {
  padding: 16px;
}

@media (max-width: 768px) {
  .role-management {
    padding: 12px;
  }
}
</style>
