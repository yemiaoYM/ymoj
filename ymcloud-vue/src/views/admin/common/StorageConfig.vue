<template>
  <div class="storage-config-management">
    <x-flex vertical :size="16">
      <el-card>
        <template #header>
          <x-flex align="center" :size="8">
            <span class="card-title">存储配置</span>
            <el-tag v-if="!userStore.isSuperAdmin" type="info">只读</el-tag>
          </x-flex>
        </template>
        <x-flex align="center" justify="space-between" :size="12" class="toolbar">
          <el-form :model="filters" class="storage-filters" @submit.prevent="searchConfigs">
            <x-flex align="center" :size="12">
              <el-form-item label="配置标识">
                <el-input v-model="filters.configKey" clearable />
              </el-form-item>
              <el-form-item label="存储桶">
                <el-input v-model="filters.bucketName" clearable />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="filters.status" placeholder="全部" clearable>
                  <el-option label="当前默认" value="Y" />
                  <el-option label="备用" value="N" />
                </el-select>
              </el-form-item>
              <el-button type="primary" :icon="Search" native-type="submit" :disabled="loading">搜索</el-button>
            </x-flex>
          </el-form>
          <x-flex :size="10" class="toolbar-actions">
            <el-button
              v-if="canOperate('add')"
              type="primary"
              plain
              :icon="Plus"
              :disabled="buttonLoading"
              @click="handleAdd"
            >
              新增配置
            </el-button>
            <el-button :icon="Refresh" :loading="loading" :disabled="buttonLoading" @click="getList">刷新</el-button>
          </x-flex>
        </x-flex>
        <el-alert
          v-if="!loaded && !loading"
          title="存储配置加载失败，请重试"
          type="error"
          :closable="false"
          show-icon
          class="storage-alert"
        />
        <el-table :data="configs" v-loading="loading || (buttonLoading && !dialog.visible)" row-key="id">
          <el-table-column label="配置标识" prop="configKey" min-width="115" show-overflow-tooltip />
          <el-table-column label="连接地址" min-width="210" show-overflow-tooltip>
            <template #default="{ row }">{{ row.isHttps === 'Y' ? 'https' : 'http' }}://{{ row.endpoint }}</template>
          </el-table-column>
          <el-table-column label="存储桶" prop="bucketName" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Y' ? 'success' : 'info'">{{ row.status === 'Y' ? '当前默认' : '备用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="userStore.isSuperAdmin" label="操作" width="250" align="center">
            <template #default="{ row }">
              <x-flex class="storage-actions" align="center" justify="center" :size="4" :wrap="false">
                <el-button
                  v-if="canEdit()"
                  type="success"
                  text
                  size="small"
                  :icon="Edit"
                  :disabled="buttonLoading"
                  @click="handleEdit(row)"
                >
                  修改
                </el-button>
                <el-tooltip
                  v-if="canOperate('activate')"
                  content="请先修改配置，填写访问密钥。"
                  :disabled="row.credentialConfigured"
                  placement="top"
                >
                  <span>
                    <el-button
                      type="primary"
                      text
                      size="small"
                      :disabled="!canSetDefault(row) || buttonLoading"
                      @click="handleActivate(row)"
                    >
                      设为默认
                    </el-button>
                  </span>
                </el-tooltip>
                <el-button
                  v-if="canOperate('remove')"
                  type="danger"
                  text
                  size="small"
                  :icon="Delete"
                  :disabled="!canDelete(row) || buttonLoading"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="pageNo"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="getList"
        />
      </el-card>
    </x-flex>
    <el-dialog
      v-model="dialog.visible"
      class="storage-config-dialog"
      :title="dialog.title"
      width="min(760px, calc(100vw - 32px))"
      align-center
      :close-on-click-modal="false"
      :close-on-press-escape="!buttonLoading"
      :show-close="!buttonLoading"
      destroy-on-close
      append-to-body
      @closed="clearCredentials"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        :disabled="buttonLoading"
        class="storage-config-form"
      >
        <el-alert
          v-if="locatorReadOnly"
          title="已有文件使用此配置，存储位置已锁定。"
          type="info"
          :closable="false"
          show-icon
          class="storage-alert"
        />
        <el-form-item label="配置标识" prop="configKey">
          <el-input v-model="form.configKey" maxlength="64" :disabled="locatorReadOnly" placeholder="例如 silo" />
        </el-form-item>
        <el-form-item label="连接地址" prop="endpoint">
          <el-input v-model="form.endpoint" :disabled="locatorReadOnly" placeholder="主机名或主机名:端口">
            <template #prefix>{{ form.isHttps === 'Y' ? 'https://' : 'http://' }}</template>
          </el-input>
        </el-form-item>
        <el-form-item label="自定义域名">
          <el-input v-model="form.domain" maxlength="255" placeholder="例如 cdn.example.com">
            <template #prefix>{{ form.isHttps === 'Y' ? 'https://' : 'http://' }}</template>
          </el-input>
        </el-form-item>
        <el-form-item
          label="Access Key"
          prop="accessKey"
          :rules="{ required: !editingConfig?.credentialConfigured, message: '请输入 Access Key', trigger: 'blur' }"
        >
          <el-input
            v-model="form.accessKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editingConfig?.credentialConfigured ? '已配置，留空保留' : '请输入 Access Key'"
          />
        </el-form-item>
        <el-form-item
          label="Secret Key"
          prop="secretKey"
          :rules="{ required: !editingConfig?.credentialConfigured, message: '请输入 Secret Key', trigger: 'blur' }"
        >
          <el-input
            v-model="form.secretKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editingConfig?.credentialConfigured ? '已配置，留空保留' : '请输入 Secret Key'"
          />
        </el-form-item>
        <el-form-item label="存储桶" prop="bucketName">
          <el-input v-model="form.bucketName" :disabled="locatorReadOnly" placeholder="例如 ymcloud" />
        </el-form-item>
        <el-form-item label="对象键前缀">
          <el-input v-model="form.prefix" maxlength="255" />
        </el-form-item>
        <el-form-item label="使用 HTTPS">
          <el-radio-group v-model="form.isHttps" :disabled="locatorReadOnly">
            <el-radio value="Y">是</el-radio>
            <el-radio value="N">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="桶策略">
          <el-radio-group v-model="form.accessPolicy" :disabled="locatorReadOnly">
            <el-radio value="0">private</el-radio>
            <el-radio value="1">public</el-radio>
            <el-radio value="2">custom</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="地域" prop="region">
          <el-input v-model="form.region" maxlength="64" placeholder="例如 us-east-1" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="buttonLoading" @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="buttonLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { useAsyncChange } from '@/hooks/async/useAsyncChange';
import modal from '@/plugins/modal';
import { useLoading } from '@/hooks/async/useLoading';
import { useFormDialog } from '@/hooks/dialog/useFormDialog';
import { computed, onMounted, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { Plus, Refresh, Edit, Delete, Search } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import {
  activateStorageConfig,
  addStorageConfig,
  deleteStorageConfig,
  getStorageConfigs,
  getStorageConfig,
  updateStorageConfig,
} from '@/api/storage';
import type { StorageConfigDTO, StorageConfigVO } from '@/types/storage';

//data
const userStore = useUserStore();
const configs = ref<StorageConfigVO[]>([]);
const { loading, withLoading } = useLoading();
const loaded = ref(false);
const pageNo = ref(1);
const pageSize = 10;
const total = ref(0);
const filters = ref({ configKey: '', bucketName: '', status: '' });
const appliedFilters = ref({ ...filters.value });
const { pending: buttonLoading, beforeChange: runAction } = useAsyncChange();
const editingConfig = ref<StorageConfigVO | null>(null);
const formRef = ref<FormInstance>();
const initialFormData: StorageConfigDTO = {
  configKey: '',
  endpoint: '',
  isHttps: 'Y',
  region: 'us-east-1',
  bucketName: '',
  prefix: null,
  domain: null,
  accessPolicy: '0',
  accessKey: '',
  secretKey: '',
  remark: '',
};
const form = ref<StorageConfigDTO>({ ...initialFormData });
const { dialog, openDialog, showDialog, closeDialog } = useFormDialog(form, formRef, initialFormData);
const locatorReadOnly = computed(() => !!editingConfig.value?.referenced);
const rules: FormRules<StorageConfigDTO> = {
  configKey: [
    { required: true, message: '请输入配置标识', trigger: 'blur' },
  ],

  endpoint: [{ required: true, whitespace: true, message: '请输入连接地址', trigger: 'blur' }],
  bucketName: [{ required: true, whitespace: true, message: '请输入存储桶', trigger: 'blur' }],
};

//methods
const canOperate = (operation: string) => userStore.isSuperAdmin
  && userStore.hasEveryPermission([`file:storage:${operation}`]);

const canEdit = () => canOperate('edit');

const canDelete = (row: StorageConfigVO) => row.canDelete;

const canSetDefault = (row: StorageConfigVO) => row.status !== 'Y'
  && row.credentialConfigured;

const getList = () => withLoading(async () => {
  try {
    const response = await getStorageConfigs({ pageNo: pageNo.value, pageSize, ...appliedFilters.value });
    configs.value = response.data.data.records;
    total.value = response.data.data.total;
    loaded.value = true;
  } catch {
    loaded.value = false;
    configs.value = [];
  }
});

const searchConfigs = () => {
  appliedFilters.value = { ...filters.value };
  pageNo.value = 1;
  return getList();
};

const clearCredentials = () => {
  form.value.accessKey = '';
  form.value.secretKey = '';
};

const handleAdd = () => {
  if (!canOperate('add') || buttonLoading.value) {
    return;
  }
  editingConfig.value = null;
  openDialog('新增存储配置');
};

const handleEdit = (row: StorageConfigVO) => runAction(async () => {
  if (!canEdit()) {
    return;
  }
  const { data } = await getStorageConfig(row.id);
  const config = data.data;
  editingConfig.value = config;
  form.value = {
    id: config.id,
    configKey: config.configKey,
    endpoint: config.endpoint,
    isHttps: config.isHttps,
    region: config.region,
    bucketName: config.bucketName,
    prefix: config.prefix,
    domain: config.domain,
    accessPolicy: config.accessPolicy,
    accessKey: '',
    secretKey: '',
    remark: config.remark || '',
  };
  showDialog('修改存储配置');
});

const handleSave = () => runAction(async () => {
  if (!canOperate(editingConfig.value ? 'edit' : 'add')) {
    return;
  }
  const valid = await formRef.value?.validate();
  if (!valid) {
    return;
  }
  const accessKey = form.value.accessKey?.trim() || '';
  const secretKey = form.value.secretKey || '';
  if ((!accessKey || !secretKey) && (!editingConfig.value?.credentialConfigured || accessKey || secretKey)) {
    ElMessage.warning('请同时填写 Access Key 和 Secret Key');
    return;
  }
  const data: StorageConfigDTO = {
    ...form.value,
    configKey: form.value.configKey.trim(),
    endpoint: form.value.endpoint.trim(),
    region: form.value.region?.trim() || '',
    bucketName: form.value.bucketName.trim(),
    accessKey: accessKey || undefined,
    secretKey: secretKey || undefined,
  };
  if (editingConfig.value) {
    await updateStorageConfig(data);
  } else {
    await addStorageConfig(data);
  }
  clearCredentials();
  ElMessage.success('存储配置已保存');
  closeDialog();
  await getList();
});

const handleActivate = (row: StorageConfigVO) => runAction(async () => {
  if (!canOperate('activate') || !canSetDefault(row)) {
    return;
  }
  await modal.confirm(
    `启用“${row.configKey}”作为新上传的默认存储？已有文件继续使用原配置。`,
    '设为默认存储',
  );
  await activateStorageConfig(row.id);
  ElMessage.success('默认存储已切换');
  await getList();
});

const handleDelete = (row: StorageConfigVO) => runAction(async () => {
  if (!canOperate('remove') || !canDelete(row)) {
    return;
  }
  await modal.confirm(`是否删除未使用的存储配置“${row.configKey}”？`, '删除确认');
  await deleteStorageConfig(row.id);
  if (configs.value.length === 1 && pageNo.value > 1) {
    pageNo.value--;
  }
  ElMessage.success('配置已删除');
  await getList();
});

onMounted(getList);
</script>

<style scoped>
.storage-config-management {
  padding: 16px;
}

.card-title {
  color: var(--el-color-primary);
}

.storage-actions .el-button {
  margin: 0;
}

.storage-actions > span {
  display: flex;
}

.storage-filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.storage-filters :deep(.el-input) {
  width: 160px;
}

.storage-filters :deep(.el-select) {
  width: 112px;
}

.toolbar-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.toolbar,
.storage-alert {
  margin-bottom: 16px;
}

.storage-config-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

:global(.storage-config-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 32px);
  margin: 0;
}

:global(.storage-config-dialog .el-dialog__body) {
  overflow-y: auto;
  padding: 12px 4px;
}

:global(.storage-config-dialog .el-dialog__footer) {
  border-top: 1px solid var(--el-border-color-lighter);
}

@media (max-width: 768px) {
  .storage-config-management {
    padding: 12px;
  }
}

@media (max-width: 600px) {
  .storage-config-form :deep(.el-radio) {
    margin-right: 12px;
  }
}
</style>
