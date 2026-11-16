<template>
  <div class="file-management">
    <x-flex vertical :size="16">
      <el-card>
        <template #header>
          <span class="card-title">文件管理</span>
        </template>
        <el-form :model="queryParams" label-width="auto" @submit.prevent="handleQuery">
          <x-flex :size="10">
            <el-form-item label="文件名称">
              <el-input v-model="queryParams.fileName" maxlength="255" placeholder="请输入文件名称" clearable />
            </el-form-item>
            <el-form-item label="访问范围">
              <el-select
                v-model="queryParams.isPublic"
                placeholder="全部"
                clearable
                class="filter-select"
              >
                <el-option label="公开" :value="1" />
                <el-option label="私有" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="业务类型">
              <el-select
                v-model="queryParams.bizType"
                placeholder="全部"
                clearable
                class="filter-select"
              >
                <el-option
                  v-for="item in businessTypes"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="存储配置">
              <el-input v-model="queryParams.storageProfile" maxlength="64" placeholder="例如 seaweedfs" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" native-type="submit">搜索</el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </x-flex>
        </el-form>
        <x-flex :size="10" class="toolbar">
          <el-button
            v-permission="['file:file:upload']"
            type="primary"
            plain
            :icon="Upload"
            @click="handleUpload"
          >
            上传文件
          </el-button>
          <el-button :icon="Refresh" :loading="loading" @click="loadFiles">刷新</el-button>
        </x-flex>
        <el-alert
          title="测试数据通过题目页面管理版本。这里可按权限下载，不提供删除或修改公开范围的操作。"
          type="info"
          :closable="false"
          show-icon
          class="file-alert"
        />
        <el-alert
          v-if="!loaded && !loading"
          title="文件列表加载失败，请重试"
          type="error"
          :closable="false"
          show-icon
          class="file-alert"
        />
        <el-table :data="files" v-loading="loading" row-key="id">
          <el-table-column label="ID" prop="id" width="80" align="center" />
          <el-table-column label="文件名称" prop="fileName" min-width="220" show-overflow-tooltip />
          <el-table-column label="大小" width="110" align="center">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="业务类型" width="120" align="center">
            <template #default="{ row }">{{ businessLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="访问范围" width="95" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isPublic === 1 ? 'success' : 'info'">
                {{ row.isPublic === 1 ? '公开' : '私有' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="存储配置" min-width="120">
            <template #default="{ row }">{{ row.storageProfile || '旧本地文件' }}</template>
          </el-table-column>
          <el-table-column label="上传者" prop="uploaderUsername" min-width="110" show-overflow-tooltip />
          <el-table-column label="上传时间" prop="createdTime" min-width="170" />
          <el-table-column label="操作" min-width="270" align="center" fixed="right">
            <template #default="{ row }">
              <x-flex :size="0" justify="center">
                <el-button
                  v-if="row.canDownload"
                  type="primary"
                  text
                  size="small"
                  :icon="Download"
                  :loading="downloadingId === row.id"
                  :disabled="downloadingId !== null && downloadingId !== row.id"
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
                <el-button
                  v-if="row.isPublic === 1 && row.url && row.bizType !== 5"
                  type="primary"
                  text
                  size="small"
                  :icon="CopyDocument"
                  @click="handleCopyUrl(row)"
                >
                  复制网址
                </el-button>
                <el-button
                  v-permission="['file:file:remove']"
                  type="danger"
                  text
                  size="small"
                  :icon="Delete"
                  :disabled="!row.canDelete || row.bizType === 5 || deletingId !== null"
                  :loading="deletingId === row.id"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
        </el-table>
        <x-flex justify="center" class="pagination">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :total="total"
            :page-sizes="[15, 25, 50]"
            background
            layout="total, sizes, prev, pager, next"
            @current-change="loadFiles"
            @size-change="handleQuery"
          />
        </x-flex>
      </el-card>
    </x-flex>
    <el-dialog
      v-model="uploadVisible"
      title="上传通用文件"
      width="min(560px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      :close-on-press-escape="!uploading"
      :show-close="!uploading"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="90px" :disabled="uploading">
        <el-form-item label="访问范围">
          <el-radio-group v-model="uploadPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0" :disabled="!canUploadPrivate">私有</el-radio>
          </el-radio-group>
          <div class="form-tip">公开文件可通过网站网址访问；私有文件需要登录并具备下载权限。</div>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            v-model:file-list="uploadFiles"
            :auto-upload="false"
            :limit="1"
            :disabled="uploading"
            :on-exceed="handleUploadExceed"
          >
            <el-button :icon="Upload">选择文件</el-button>
            <template #tip><div class="form-tip">单个文件最大 50MiB。测试数据请在题目页面成对上传。</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="uploading" label="上传进度">
          <el-progress :percentage="uploadProgress" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="uploading" @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!uploadFiles.length" @click="submitUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox, type UploadUserFile } from 'element-plus';
import { Search, Refresh, Upload, Download, Delete, CopyDocument } from '@element-plus/icons-vue';
import useClipboard from 'vue-clipboard3';
import { useUserStore } from '@/stores/user';
import { useAsyncChange } from '@/hooks/useAsyncChange';
import { deleteAdminFile, downloadAdminFile, getAdminFiles, uploadAdminFile } from '@/api/storage';
import type { FilePageQuery, StorageFileVO } from '@/types/storage';

//data
const userStore = useUserStore();
const { toClipboard } = useClipboard();
const queryParams = ref<FilePageQuery>({ pageNum: 1, pageSize: 15 });
const files = ref<StorageFileVO[]>([]);
const total = ref(0);
const loading = ref(false);
const loaded = ref(false);
const downloadingId = ref<number | null>(null);
const { beforeChange: downloadFile } = useAsyncChange();
const deletingId = ref<number | null>(null);
const uploadVisible = ref(false);
const uploading = ref(false);
const uploadPublic = ref<0 | 1>(1);
const uploadFiles = ref<UploadUserFile[]>([]);
const uploadProgress = ref(0);
const canUploadPrivate = computed(() => userStore.hasEveryPermission(['file:file:private']));
const businessTypes = [
  { value: 0, label: '通用文件' },
  { value: 1, label: '题目附件' },
  { value: 2, label: '比赛附件' },
  { value: 3, label: '公告附件' },
  { value: 4, label: '图片' },
  { value: 5, label: '测试数据' },
];
let listRequestId = 0;

//methods
const loadFiles = async () => {
  const requestId = ++listRequestId;
  loading.value = true;
  try {
    const response = await getAdminFiles({
      ...queryParams.value,
      fileName: queryParams.value.fileName?.trim() || undefined,
      storageProfile: queryParams.value.storageProfile?.trim() || undefined,
    });
    if (requestId === listRequestId) {
      files.value = response.data.data.records;
      total.value = response.data.data.total;
      loaded.value = true;
    }
  } catch {
    if (requestId === listRequestId) {
      loaded.value = false;
      files.value = [];
      total.value = 0;
    }
  } finally {
    if (requestId === listRequestId) {
      loading.value = false;
    }
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  loadFiles();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 15 };
  loadFiles();
};

const businessLabel = (value: number | null) => businessTypes.find(item => item.value === value)?.label || '其他';

const formatFileSize = (size: number | null) => {
  if (size === null) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KiB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MiB`;
};

const handleDownload = (row: StorageFileVO) => downloadFile(async () => {
  if (!row.canDownload) {
    return;
  }
  downloadingId.value = row.id;
  try {
    const response = await downloadAdminFile(row.id);
    const blob = response.data;
    if (!response.headers['content-disposition']) {
      ElMessage.error('文件下载失败');
      return;
    }
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = row.fileName.split(/[\\/]/).pop() || 'download';
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
  } finally {
    downloadingId.value = null;
  }
});

const handleCopyUrl = async (row: StorageFileVO) => {
  if (row.isPublic !== 1 || !row.url || row.bizType === 5) {
    return;
  }
  try {
    const url = new URL(row.url, window.location.origin);
    if (!['http:', 'https:'].includes(url.protocol)) {
      throw new Error('invalid public URL');
    }
    await toClipboard(url.href);
    ElMessage.success('公开网址已复制');
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限');
  }
};

const handleDelete = async (row: StorageFileVO) => {
  if (!row.canDelete || row.bizType === 5 || deletingId.value !== null
      || !userStore.hasEveryPermission(['file:file:remove'])) {
    return;
  }
  deletingId.value = row.id;
  try {
    await ElMessageBox.confirm(`是否确认删除文件“${row.fileName}”？`, '删除确认', { type: 'warning' });
    await deleteAdminFile(row.id);
    ElMessage.success('删除成功');
    if (files.value.length === 1 && queryParams.value.pageNum > 1) {
      queryParams.value.pageNum--;
    }
    await loadFiles();
  } catch {
    // 取消确认无需提示，接口错误由请求拦截器处理。
  } finally {
    deletingId.value = null;
  }
};

const handleUpload = () => {
  if (!userStore.hasEveryPermission(['file:file:upload'])) {
    return;
  }
  uploadPublic.value = 1;
  uploadFiles.value = [];
  uploadProgress.value = 0;
  uploadVisible.value = true;
};

const handleUploadExceed = () => ElMessage.warning('一次选择一个文件，请移除已选文件后重新选择');

const submitUpload = async () => {
  const file = uploadFiles.value[0]?.raw;
  if (uploading.value || !file || !userStore.hasEveryPermission(['file:file:upload'])) {
    return;
  }
  if (uploadPublic.value === 0 && !canUploadPrivate.value) {
    ElMessage.warning('没有上传私有文件的权限');
    return;
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('单个文件不能超过 50MiB');
    return;
  }
  uploading.value = true;
  uploadProgress.value = 0;
  try {
    const data = new FormData();
    data.append('file', file);
    data.append('isPublic', String(uploadPublic.value));
    await uploadAdminFile(data, event => {
      uploadProgress.value = Math.min(99, Math.round(event.loaded / Math.max(file.size, 1) * 100));
    });
    uploadProgress.value = 100;
    ElMessage.success('上传成功');
    uploadVisible.value = false;
    await loadFiles();
  } catch {
    // 保留选中文件，用户可处理错误后重新上传。
  } finally {
    uploading.value = false;
  }
};

onMounted(loadFiles);
</script>

<style scoped>
.file-management {
  padding: 16px;
}

.card-title {
  color: var(--el-color-primary);
}

.filter-select {
  width: 140px;
}

.toolbar,
.file-alert {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 20px;
}

.form-tip {
  width: 100%;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 22px;
}

@media (max-width: 768px) {
  .file-management {
    padding: 12px;
  }
}
</style>
