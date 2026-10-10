<template>
  <div class="storage-config-management">
    <n-flex vertical :size="16">
      <el-card>
        <template #header>
          <span class="card-title">存储配置</span>
        </template>
        <el-alert
          title="默认使用本地存储；使用其他服务时先填写访问密钥、测试连接，再设为默认，保存不会自动启用。已有文件仍从原存储读取。"
          type="info"
          :closable="false"
          show-icon
          class="storage-alert"
        />
        <el-alert
          v-if="!userStore.isSuperAdmin"
          title="存储连接和访问密钥由超级管理员维护，当前账户仅可查看配置。"
          type="info"
          :closable="false"
          show-icon
          class="storage-alert"
        />
        <n-flex :size="10" class="toolbar">
          <el-button
            v-if="canOperate('add')"
            type="primary"
            plain
            :icon="Plus"
            :disabled="!!busyKey"
            @click="handleAdd"
          >
            新增配置
          </el-button>
          <el-button :icon="Refresh" :loading="loading" :disabled="!!busyKey" @click="loadConfigs">刷新</el-button>
        </n-flex>
        <p class="storage-description">待配置的行仅是服务示例，填写并保存后才建立配置。</p>
        <el-alert
          v-if="!loaded && !loading"
          title="存储配置加载失败，请重试"
          type="error"
          :closable="false"
          show-icon
          class="storage-alert"
        />
        <el-table :data="orderedConfigs" v-loading="loading" row-key="key">
          <el-table-column label="配置名称" min-width="145" show-overflow-tooltip>
            <template #default="{ row }">{{ configName(row) }}</template>
          </el-table-column>
          <el-table-column label="标识" prop="key" min-width="115" />
          <el-table-column label="服务商" min-width="120">
            <template #default="{ row }">{{ providerLabel(row.provider) }}</template>
          </el-table-column>
          <el-table-column label="连接地址" min-width="210" show-overflow-tooltip>
            <template #default="{ row }">{{ row.provider === 'local' ? '服务器本地目录' : row.endpoint }}</template>
          </el-table-column>
          <el-table-column label="存储桶" min-width="185">
            <template #default="{ row }">
              <div v-if="row.provider !== 'local'">公开文件：{{ row.publicBucket }}</div>
              <div v-if="row.provider !== 'local'">私有文件：{{ row.privateBucket }}</div>
              <span v-else>本地文件目录</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isExample" type="warning">待配置</el-tag>
              <el-tag v-else :type="row.active ? 'success' : 'info'">{{ row.active ? '当前默认' : '备用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="265" align="center" fixed="right">
            <template #default="{ row }">
              <n-flex :size="0" justify="center">
                <el-button
                  v-if="canEdit(row)"
                  type="success"
                  text
                  size="small"
                  :icon="Edit"
                  :disabled="row.provider === 'local' || !!busyKey"
                  @click="handleEdit(row)"
                >
                  {{ row.isExample ? '配置' : '修改' }}
                </el-button>
                <el-button
                  v-if="canOperate('test')"
                  type="primary"
                  text
                  size="small"
                  :loading="busyKey === row.key && busyAction === 'test'"
                  :disabled="row.isExample || !!busyKey"
                  @click="handleTest(row)"
                >
                  测试连接
                </el-button>
                <el-tooltip
                  v-if="canOperate('activate')"
                  content="请先修改配置，填写访问密钥并测试连接。"
                  :disabled="row.provider === 'local' || row.credentialConfigured"
                  placement="top"
                >
                  <span>
                    <el-button
                      type="primary"
                      text
                      size="small"
                      :loading="busyKey === row.key && busyAction === 'activate'"
                      :disabled="!canSetDefault(row) || !!busyKey"
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
                  :disabled="!canDelete(row) || !!busyKey"
                  :loading="busyKey === row.key && busyAction === 'remove'"
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
      class="storage-config-dialog"
      :title="dialogTitle"
      width="min(760px, calc(100vw - 32px))"
      align-center
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      destroy-on-close
      append-to-body
      @closed="clearCredentials"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        :disabled="saving"
        class="storage-config-form"
      >
        <el-tabs v-model="settingsTab" class="config-tabs">
          <el-tab-pane label="基本设置" name="basic">
            <el-scrollbar height="100%" class="config-scrollbar" view-class="config-scrollbar-content">
              <el-alert
                v-if="editingConfig?.referenced"
                title="已有文件使用此配置，连接地址、服务商和存储桶不可更改，仍可更新访问密钥。"
                type="info"
                :closable="false"
                show-icon
                class="storage-alert"
              />
              <div class="config-grid">
                <el-form-item label="配置名称" prop="name">
                  <el-input v-model="form.name" maxlength="64" placeholder="例如 SeaweedFS 存储" />
                </el-form-item>
                <el-form-item label="配置标识" prop="key">
                  <el-input
                    v-model="form.key"
                    maxlength="64"
                    :disabled="!!editingConfig || !!configuringExample"
                    placeholder="小写字母、数字、下划线或短横线"
                  />
                </el-form-item>
                <el-form-item label="服务商" prop="provider">
                  <el-select
                    v-model="form.provider"
                    :disabled="!!editingConfig || !!configuringExample"
                    @change="applyProviderPreset"
                  >
                    <el-option
                      v-for="item in providers"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="连接地址" prop="endpoint">
                  <el-input v-model="form.endpoint" :disabled="locatorReadOnly" :placeholder="endpointPlaceholder" />
                </el-form-item>
                <div class="form-tip grid-wide">{{ providerTip }} 远程连接使用 HTTPS，HTTP 仅用于本机开发。</div>
                <el-form-item label="公开文件桶" prop="publicBucket">
                  <el-input v-model="form.publicBucket" :disabled="locatorReadOnly" placeholder="例如 ymcloud-public" />
                </el-form-item>
                <el-form-item label="私有文件桶" prop="privateBucket">
                  <el-input v-model="form.privateBucket" :disabled="locatorReadOnly" placeholder="例如 ymcloud-private" />
                </el-form-item>
                <div class="form-tip grid-wide">公开与私有文件使用不同的桶，均保持私有，由网站控制访问。</div>
                <el-form-item label="Access Key" prop="accessKey">
                  <template #label>访问标识（Access Key）</template>
                  <el-input
                    v-model="form.accessKey"
                    type="password"
                    show-password
                    autocomplete="new-password"
                    :placeholder="editingConfig?.credentialConfigured ? '已配置，留空保留' : '存储服务提供的 Access Key'"
                  />
                </el-form-item>
                <el-form-item label="Secret Key" prop="secretKey">
                  <template #label>访问密钥（Secret Key）</template>
                  <el-input
                    v-model="form.secretKey"
                    type="password"
                    show-password
                    autocomplete="new-password"
                    :placeholder="editingConfig?.credentialConfigured ? '已配置，留空保留' : '存储服务提供的 Secret Key'"
                  />
                </el-form-item>
                <div class="form-tip grid-wide">
                  访问标识和密钥用于让网站连接存储服务。
                  <template v-if="editingConfig">已保存内容不回显；更新时同时填写两项，均留空保留原值。</template>
                </div>
              </div>
            </el-scrollbar>
          </el-tab-pane>
          <el-tab-pane label="更多设置" name="more">
            <el-scrollbar height="100%" class="config-scrollbar" view-class="config-scrollbar-content">
              <div class="config-grid">
                <el-form-item label="地域" prop="region">
                  <el-input v-model="form.region" :disabled="locatorReadOnly" placeholder="例如 us-east-1 或 cn-hangzhou" />
                  <div class="form-tip">通常使用服务商提供的地域，预设值可以直接使用。</div>
                </el-form-item>
                <el-form-item v-if="form.provider !== 'aliyun'" label="路径式访问">
                  <el-switch v-model="form.pathStyle" :disabled="locatorReadOnly" aria-label="S3 路径式访问" />
                  <div class="form-tip">SeaweedFS、MinIO 通常启用，AWS S3 通常关闭。</div>
                </el-form-item>
                <el-form-item label="备注" class="grid-wide">
                  <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
                </el-form-item>
              </div>
            </el-scrollbar>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import {
  activateStorageConfig,
  addStorageConfig,
  deleteStorageConfig,
  getStorageConfigs,
  testStorageConfig,
  updateStorageConfig,
} from '@/api/storage';
import type { StorageConfigDTO, StorageConfigVO, StorageProvider } from '@/type/storage';

type StorageConfigRow = StorageConfigVO & { isExample?: boolean };

//data
const userStore = useUserStore();
const configs = ref<StorageConfigVO[]>([]);
const loading = ref(false);
const loaded = ref(false);
const dialogVisible = ref(false);
const saving = ref(false);
const editingConfig = ref<StorageConfigVO | null>(null);
const configuringExample = ref<StorageConfigRow | null>(null);
const formRef = ref<FormInstance>();
const settingsTab = ref('basic');
const busyKey = ref('');
const busyAction = ref('');
const providers: { value: Exclude<StorageProvider, 'local'>; label: string }[] = [
  { value: 'minio', label: 'MinIO' },
  { value: 'seaweedfs', label: 'SeaweedFS' },
  { value: 'aliyun', label: '阿里云 OSS' },
  { value: 's3', label: '其他 S3 服务' },
];
const providerPresets = {
  seaweedfs: { endpoint: 'http://127.0.0.1:18333', region: 'us-east-1', pathStyle: true },
  minio: { endpoint: 'http://127.0.0.1:9000', region: 'us-east-1', pathStyle: true },
  aliyun: { endpoint: 'https://oss-cn-hangzhou.aliyuncs.com', region: 'cn-hangzhou', pathStyle: false },
  s3: { endpoint: 'https://s3.us-east-1.amazonaws.com', region: 'us-east-1', pathStyle: false },
};
// 服务示例仅供页面引导，同标识的真实配置始终覆盖示例。
const storageExamples: StorageConfigRow[] = providers.map(item => ({
  key: item.value,
  name: item.label,
  provider: item.value,
  endpoint: 'xxxx',
  publicBucket: 'xxxx',
  privateBucket: 'xxxx',
  region: providerPresets[item.value].region,
  pathStyle: providerPresets[item.value].pathStyle,
  active: false,
  source: 'config',
  referenced: false,
  credentialConfigured: false,
  canDelete: false,
  isExample: true,
}));
const createForm = (provider: Exclude<StorageProvider, 'local'> = 'seaweedfs'): StorageConfigDTO => ({
  key: '',
  name: '',
  provider,
  endpoint: '',
  region: providerPresets[provider].region,
  publicBucket: '',
  privateBucket: '',
  pathStyle: providerPresets[provider].pathStyle,
  accessKey: '',
  secretKey: '',
  remark: '',
});
const form = ref<StorageConfigDTO>(createForm());
const orderedConfigs = computed<StorageConfigRow[]>(() => {
  if (!loaded.value) {
    return [];
  }
  const actualKeys = new Set(configs.value.map(row => row.key));
  const rows = [...configs.value, ...storageExamples.filter(row => !actualKeys.has(row.key))];
  return rows.sort((first, second) => Number(second.provider === 'local') - Number(first.provider === 'local'));
});
const dialogTitle = computed(() => {
  if (editingConfig.value) {
    return '修改存储配置';
  }
  if (configuringExample.value) {
    return `配置 ${configuringExample.value.name}`;
  }
  return '新增存储配置';
});
const endpointPlaceholder = computed(() => form.value.provider === 'local'
  ? '' : providerPresets[form.value.provider].endpoint);
const locatorReadOnly = computed(() => !!editingConfig.value?.referenced);
const providerTip = computed(() => form.value.provider === 'aliyun'
  ? '使用阿里云原生 OSS 连接。请填写应用账户及对应地域 Endpoint。'
  : '使用 S3 兼容接口连接。请填写实际地址，示例仅供参考。');
const rules: FormRules<StorageConfigDTO> = {
  name: [{ required: true, whitespace: true, message: '请输入配置名称', trigger: 'blur' }],
  key: [
    { required: true, message: '请输入配置标识', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_-]{0,63}$/, message: '标识以小写字母开头，仅允许字母、数字、下划线和短横线', trigger: 'blur' },
  ],
  endpoint: [{ required: true, whitespace: true, message: '请输入连接地址', trigger: 'blur' }],
  publicBucket: [{ required: true, whitespace: true, message: '请输入公开文件桶', trigger: 'blur' }],
  privateBucket: [{ required: true, whitespace: true, message: '请输入私有文件桶', trigger: 'blur' }],
  region: [{ required: true, whitespace: true, message: '请输入地域', trigger: 'blur' }],
};
let listRequestId = 0;

//methods
const canOperate = (operation: string) => userStore.isSuperAdmin
  && userStore.hasEveryPermission([`file:storage:${operation}`]);

const providerLabel = (provider: StorageProvider) => provider === 'local'
  ? '本地存储'
  : providers.find(item => item.value === provider)?.label || provider;

const configName = (row: StorageConfigVO) => row.provider === 'local' ? '本地存储' : row.name;

const canEdit = (row: StorageConfigRow) => canOperate(row.isExample ? 'add' : 'edit');

const canDelete = (row: StorageConfigRow) => !row.isExample && row.canDelete && row.provider !== 'local'
  && row.source === 'database' && !row.active && !row.referenced;

const canSetDefault = (row: StorageConfigRow) => !row.isExample && !row.active
  && (row.provider === 'local' || row.credentialConfigured);

const loadConfigs = async () => {
  const requestId = ++listRequestId;
  loading.value = true;
  try {
    const response = await getStorageConfigs();
    if (requestId === listRequestId) {
      configs.value = response.data.data;
      loaded.value = true;
    }
  } catch {
    if (requestId === listRequestId) {
      loaded.value = false;
      configs.value = [];
    }
  } finally {
    if (requestId === listRequestId) {
      loading.value = false;
    }
  }
};

const clearCredentials = () => {
  form.value.accessKey = '';
  form.value.secretKey = '';
};

const handleAdd = () => {
  if (!canOperate('add') || busyKey.value) {
    return;
  }
  editingConfig.value = null;
  configuringExample.value = null;
  form.value = createForm();
  settingsTab.value = 'basic';
  dialogVisible.value = true;
};

const handleEdit = (row: StorageConfigRow) => {
  if (!canEdit(row) || row.provider === 'local' || busyKey.value) {
    return;
  }
  if (row.isExample) {
    editingConfig.value = null;
    configuringExample.value = row;
    form.value = {
      ...createForm(row.provider),
      key: row.key,
      name: row.name,
    };
    settingsTab.value = 'basic';
    dialogVisible.value = true;
    return;
  }
  configuringExample.value = null;
  editingConfig.value = row;
  form.value = {
    key: row.key,
    name: row.name,
    provider: row.provider,
    endpoint: row.endpoint,
    region: row.region,
    publicBucket: row.publicBucket,
    privateBucket: row.privateBucket,
    pathStyle: row.pathStyle,
    accessKey: '',
    secretKey: '',
    remark: row.remark || '',
  };
  settingsTab.value = 'basic';
  dialogVisible.value = true;
};

const applyProviderPreset = (provider: StorageProvider) => {
  if (editingConfig.value || configuringExample.value) {
    return;
  }
  if (provider !== 'local') {
    form.value.endpoint = '';
    form.value.region = providerPresets[provider].region;
    form.value.pathStyle = providerPresets[provider].pathStyle;
    clearCredentials();
  }
};

const handleSave = async () => {
  if (saving.value || !canOperate(editingConfig.value ? 'edit' : 'add')) {
    return;
  }
  try {
    const valid = await formRef.value?.validate().catch((fields: unknown) => {
      settingsTab.value = fields && typeof fields === 'object'
        && Object.keys(fields).length === 1 && 'region' in fields ? 'more' : 'basic';
      return false;
    });
    if (!valid) {
      return;
    }
    const accessKey = form.value.accessKey?.trim() || '';
    const secretKey = form.value.secretKey || '';
    if ((!accessKey || !secretKey) && (!editingConfig.value?.credentialConfigured || accessKey || secretKey)) {
      ElMessage.warning('请同时填写 Access Key 和 Secret Key');
      return;
    }
    if (form.value.publicBucket.trim() === form.value.privateBucket.trim()) {
      ElMessage.warning('公开文件和私有文件需要使用不同的存储桶');
      return;
    }
    saving.value = true;
    const data: StorageConfigDTO = {
      ...form.value,
      key: form.value.key.trim(),
      name: form.value.name.trim(),
      endpoint: form.value.endpoint.trim(),
      region: form.value.region.trim(),
      publicBucket: form.value.publicBucket.trim(),
      privateBucket: form.value.privateBucket.trim(),
      accessKey: accessKey || undefined,
      secretKey: secretKey || undefined,
    };
    if (editingConfig.value) {
      await updateStorageConfig(editingConfig.value.key, data);
    } else {
      await addStorageConfig(data);
    }
    clearCredentials();
    ElMessage.success('已保存，请测试连接后设为默认');
    dialogVisible.value = false;
    await loadConfigs();
  } catch {
    // 请求拦截器统一提示错误，不记录可能包含凭据的请求对象。
  } finally {
    saving.value = false;
  }
};

const handleTest = async (row: StorageConfigRow) => {
  if (!canOperate('test') || row.isExample || busyKey.value) {
    return;
  }
  busyKey.value = row.key;
  busyAction.value = 'test';
  try {
    await testStorageConfig(row.key);
    ElMessage.success('存储连接测试通过');
    await loadConfigs();
  } catch {
    // 请求拦截器显示测试失败信息。
  } finally {
    busyKey.value = '';
    busyAction.value = '';
  }
};

const handleActivate = async (row: StorageConfigRow) => {
  if (!canOperate('activate') || !canSetDefault(row) || busyKey.value) {
    return;
  }
  busyKey.value = row.key;
  busyAction.value = 'activate';
  try {
    await ElMessageBox.confirm(
      `启用“${configName(row)}”作为新上传的默认存储？已有文件继续使用原配置。`,
      '设为默认存储',
      { type: 'warning' },
    );
    await activateStorageConfig(row.key);
    ElMessage.success('默认存储已切换');
    await loadConfigs();
  } catch {
    // 取消无需提示，启用失败由请求拦截器处理。
  } finally {
    busyKey.value = '';
    busyAction.value = '';
  }
};

const handleDelete = async (row: StorageConfigRow) => {
  if (!canOperate('remove') || !canDelete(row) || busyKey.value) {
    return;
  }
  busyKey.value = row.key;
  busyAction.value = 'remove';
  try {
    await ElMessageBox.confirm(`是否删除未使用的存储配置“${row.name}”？`, '删除确认', { type: 'warning' });
    await deleteStorageConfig(row.key);
    ElMessage.success('配置已删除');
    await loadConfigs();
  } catch {
    // 不记录请求对象，避免凭据进入浏览器日志。
  } finally {
    busyKey.value = '';
    busyAction.value = '';
  }
};

onMounted(loadConfigs);
</script>

<style scoped>
.storage-config-management {
  padding: 16px;
}

.card-title {
  color: var(--el-color-primary);
}

.storage-description {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 22px;
}

.toolbar,
.storage-alert {
  margin-bottom: 16px;
}

.form-tip {
  width: 100%;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 22px;
}

.storage-config-form,
.config-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.config-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
}

.config-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.config-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
}

.config-scrollbar :deep(.config-scrollbar-content) {
  padding-right: 12px;
  padding-bottom: 4px;
}

.config-scrollbar :deep(.el-scrollbar__wrap) {
  overscroll-behavior: contain;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  padding-bottom: 4px;
}

.config-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.config-grid > .form-tip {
  margin-top: 0;
}

.grid-wide {
  grid-column: 1 / -1;
}

/* 弹窗挂载到 body，使用独立类限制高度，页签内容由 Element Plus 滚动条承载。 */
:global(.storage-config-dialog) {
  display: flex;
  flex-direction: column;
  height: min(680px, calc(100dvh - 48px));
  margin: 0;
}

:global(.storage-config-dialog .el-dialog__header),
:global(.storage-config-dialog .el-dialog__footer) {
  flex-shrink: 0;
}

:global(.storage-config-dialog .el-dialog__body) {
  display: flex;
  flex: 1;
  min-height: 0;
  padding-top: 12px;
}

@media (max-width: 768px) {
  .storage-config-management {
    padding: 12px;
  }
}

@media (max-width: 600px) {
  .config-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
