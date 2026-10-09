<template>
  <div class="auth-management">
    <n-flex vertical :size="16">
      <el-card>
        <el-form label-width="auto">
          <n-flex :size="10">
            <el-form-item label="菜单名称">
              <el-input v-model="keyword" placeholder="请输入名称或权限编码" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="searchKeyword = keyword">
                搜索
              </el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </n-flex>
        </el-form>
        <n-flex :size="10" style="padding-bottom: 15px">
          <el-button
            v-if="isSuperAdmin"
            v-permission="['system:auth:add']"
            plain
            type="primary"
            :icon="Plus"
            @click="handleAdd()"
          >
            新增
          </el-button>
          <el-button :icon="Refresh" @click="loadAuths">刷新</el-button>
        </n-flex>
        <el-alert
          v-if="!isSuperAdmin"
          title="菜单权限定义由超级管理员维护，角色授权请在角色管理中配置。"
          type="info"
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-table :data="filteredTree" v-loading="loading" row-key="id" default-expand-all>
          <el-table-column label="菜单名称" prop="name" min-width="210" />
          <el-table-column label="类型" width="85" align="center">
            <template #default="{ row }">
              <el-tag :type="row.type === 'F' ? 'info' : 'primary'">
                {{ typeNames[row.type as keyof typeof typeNames] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权限编码" prop="code" min-width="210" show-overflow-tooltip />
          <el-table-column label="路由路径" prop="path" min-width="140" show-overflow-tooltip />
          <el-table-column label="组件路径" prop="component" min-width="210" show-overflow-tooltip />
          <el-table-column label="状态" width="85" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="200" align="center">
            <template #default="{ row }">
              <n-flex v-if="isSuperAdmin" :size="0" justify="center">
                <el-button
                  v-permission="['system:auth:edit']"
                  type="success"
                  size="small"
                  text
                  :icon="Edit"
                  @click="handleUpdate(row)"
                >
                  修改
                </el-button>
                <el-button
                  v-if="row.type !== 'F'"
                  v-permission="['system:auth:add']"
                  type="primary"
                  size="small"
                  text
                  :icon="Plus"
                  @click="handleAdd(row)"
                >
                  新增
                </el-button>
                <el-button
                  v-permission="['system:auth:remove']"
                  type="danger"
                  size="small"
                  text
                  :icon="Delete"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </n-flex>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </n-flex>
    <el-dialog
      v-model="dialogVisible"
      :title="editingAuth ? '编辑菜单权限' : '新增菜单权限'"
      width="min(650px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      destroy-on-close
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="saving">
        <el-form-item label="权限类型" prop="type">
          <el-radio-group v-model="form.type" :disabled="!!editingAuth" @change="handleTypeChange">
            <el-radio value="M">目录</el-radio>
            <el-radio value="C">页面</el-radio>
            <el-radio value="F">操作</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="上级菜单" prop="parentId">
          <el-select
            v-model="form.parentId"
            style="width: 100%"
            filterable
          >
            <el-option
              v-if="form.type === 'M' || editingAuth?.parentId === 0"
              label="顶级"
              :value="0"
            />
            <el-option
              v-for="parent in parentOptions"
              :key="parent.id"
              :label="parent.name"
              :value="parent.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" maxlength="128" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'M'" label="权限编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="!!editingAuth"
            maxlength="128"
            placeholder="例如 problem:problem:edit"
          />
        </el-form-item>
        <template v-if="form.type !== 'F'">
          <el-form-item label="路由路径" prop="path">
            <el-input
              v-model="form.path"
              :placeholder="form.type === 'M' ? '例如 /admin/example' : '例如 list'"
            />
          </el-form-item>
          <el-form-item label="路由名称" prop="routeName">
            <el-input
              v-model="form.routeName"
              placeholder="例如 AdminExampleList"
            />
          </el-form-item>
          <el-form-item v-if="form.type === 'C'" label="页面组件" prop="component">
            <el-select
              v-model="form.component"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="component in componentOptions"
                :key="component"
                :label="component"
                :value="component"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="菜单图标">
            <el-input v-model="form.icon" placeholder="例如 Setting" maxlength="64" />
          </el-form-item>
        </template>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus';
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { getAuthList, addAuth, updateAuth, deleteAuth } from '@/api/rbac';
import { buildAuthTree } from '@/utils/rbac';
import type { AuthVO, AuthDTO, AuthTreeNode } from '@/type/rbac';

//data
const userStore = useUserStore();
const isSuperAdmin = computed(() => userStore.isSuperAdmin);
const authList = ref<AuthVO[]>([]);
const loading = ref(false);
const keyword = ref('');
const searchKeyword = ref('');
const typeNames = { M: '目录', C: '页面', F: '操作' };
const dialogVisible = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const editingAuth = ref<AuthVO | null>(null);
const emptyForm = (): AuthDTO => ({
  code: '',
  name: '',
  parentId: 0,
  path: '',
  routeName: '',
  component: '',
  icon: '',
  type: 'M',
  status: 1,
});
const form = ref<AuthDTO>(emptyForm());
const rules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }],
  code: [
    { required: true, message: '请输入权限编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]+$/, message: '权限编码格式不正确', trigger: 'blur' },
  ],
  path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }],
  routeName: [{ required: true, message: '请输入路由名称', trigger: 'blur' }],
  component: [{ required: true, message: '请选择页面组件', trigger: 'change' }],
};
const componentOptions = Object.keys(import.meta.glob('../**/*.vue'))
  .map(path => `admin/${path.slice(3, -4)}`)
  .filter(path => !path.endsWith('/AdminLogin'))
  .sort();
const parentOptions = computed(() => {
  return authList.value.filter(auth => {
    return auth.id !== editingAuth.value?.id
      && auth.type === (form.value.type === 'C' ? 'M' : 'C');
  });
});

const filterTree = (nodes: AuthTreeNode[]): AuthTreeNode[] => nodes.flatMap(node => {
  if (!searchKeyword.value || node.name.includes(searchKeyword.value) || node.code?.includes(searchKeyword.value)) {
    return [node];
  }
  const children = filterTree(node.children);
  return children.length ? [{ ...node, children }] : [];
});
const filteredTree = computed(() => filterTree(buildAuthTree(authList.value)));

//methods

//切换权限类型
const handleTypeChange = () => {
  form.value.parentId = form.value.type === 'M' ? 0 : (parentOptions.value[0]?.id || 0);
  form.value.code = '';
};

//查询菜单权限
const loadAuths = async () => {
  loading.value = true;
  try {
    const res = await getAuthList();
    authList.value = res.data.data;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

//重置查询条件
const resetQuery = () => {
  keyword.value = '';
  searchKeyword.value = '';
  loadAuths();
};

//新增菜单权限
const handleAdd = (parent?: AuthVO) => {
  editingAuth.value = null;
  form.value = {
    ...emptyForm(),
    type: parent ? (parent.type === 'M' ? 'C' : 'F') : 'M',
    parentId: parent?.id || 0,
  };
  dialogVisible.value = true;
};

//编辑菜单权限
const handleUpdate = (row: AuthVO) => {
  editingAuth.value = row;
  form.value = {
    code: row.code,
    name: row.name,
    parentId: row.parentId,
    path: row.path,
    routeName: row.routeName,
    component: row.component,
    icon: row.icon,
    type: row.type,
    status: row.status,
  };
  dialogVisible.value = true;
};

//提交菜单权限表单
const handleSubmit = async () => {
  if (saving.value) {
    return;
  }
  try {
    if (!(await formRef.value?.validate())) {
      return;
    }
    saving.value = true;
    if (editingAuth.value) {
      await updateAuth(editingAuth.value.id, form.value);
    } else {
      await addAuth(form.value);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadAuths();
  } catch (e) {
    console.error(e);
  } finally {
    saving.value = false;
  }
};

//删除菜单权限
const handleDelete = async (row: AuthVO) => {
  try {
    await ElMessageBox.confirm(`是否确认删除菜单权限“${row.name}”？`, '删除确认', { type: 'warning' });
    await deleteAuth(row.id);
    ElMessage.success('删除成功');
    await loadAuths();
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error(e);
    }
  }
};
//生命周期
onMounted(() => {
  loadAuths();
});
</script>

<style scoped>
.auth-management {
  padding: 16px;
}

@media (max-width: 768px) {
  .auth-management {
    padding: 12px;
  }
}
</style>
