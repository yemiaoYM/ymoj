<template>
  <x-flex class="admin-list-page file-management" vertical :size="16">
    <el-card class="filter-card">
      <template #header>
        <el-text tag="b" type="primary" size="large">筛选条件</el-text>
      </template>
      <el-form class="filter-form" :model="queryParams" label-width="auto" @submit.prevent="handleQuery">
        <x-flex align="center" :size="[20, 12]">
          <el-form-item label="原名">
            <el-input v-model="queryParams.fileName" maxlength="255" placeholder="请输入原文件名" clearable />
          </el-form-item>
          <el-form-item label="业务类型">
            <el-select
              v-model="queryParams.bizType"
              placeholder="全部"
              clearable
              class="filter-select"
            >
              <el-option
                v-for="item in FILE_BIZ_TYPES"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="存储配置">
            <el-input v-model="queryParams.storageKey" maxlength="64" placeholder="例如 silo" clearable />
          </el-form-item>
          <x-flex class="filter-actions" :size="8" :wrap="false">
            <el-button type="primary" icon="Search" native-type="submit">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </x-flex>
        </x-flex>
      </el-form>
    </el-card>
    <el-card class="list-card">
      <template #header>
        <x-flex align="center" justify="space-between">
          <el-text tag="b" type="primary" size="large">文件列表</el-text>
          <x-flex class="list-actions" align="center" justify="flex-end" :size="8">
            <el-button v-if="canUpload" type="primary" plain icon="Upload" :disabled="pending" @click="handleUpload">
              上传文件
            </el-button>
            <el-button
              v-if="canRemove"
              type="danger"
              plain
              icon="Delete"
              :disabled="!canDeleteSelection || loading || pending"
              @click="handleDelete()"
            >
              删除
            </el-button>
            <el-tooltip content="刷新" placement="top">
              <el-button circle icon="Refresh" :disabled="loading" @click="getList">刷新</el-button>
            </el-tooltip>
          </x-flex>
        </x-flex>
      </template>
      <el-table
        v-loading="loading"
        :data="fileList"
        height="100%"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column label="ID" prop="id" min-width="188" show-overflow-tooltip />
        <el-table-column label="文件名" prop="fileName" min-width="220" show-overflow-tooltip />
        <el-table-column label="原名" prop="originalName" min-width="180" show-overflow-tooltip />
        <el-table-column label="文件后缀" min-width="92" align="center">
          <template #default="{ row }">{{ row.fileSuffix || '—' }}</template>
        </el-table-column>
        <el-table-column label="大小" min-width="104" align="right">
          <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="业务类型" min-width="102" align="center">
          <template #default="{ row }: { row: StorageFileVO }">
            <el-tag
              v-if="businessTypesByCode[row.bizType]"
              :type="businessTypesByCode[row.bizType].type"
              effect="dark"
            >
              {{ businessTypesByCode[row.bizType].label }}
            </el-tag>
            <span v-else>{{ row.bizType ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务 ID" min-width="116" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizId ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="存储配置" prop="service" min-width="104" show-overflow-tooltip />
        <el-table-column label="创建者" prop="createdBy" min-width="88" show-overflow-tooltip />
        <el-table-column label="上传时间" prop="createdTime" min-width="184" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" width="128" align="center">
          <template #default="{ row }">
            <x-flex class="row-actions" :wrap="false" :size="8" justify="center">
              <el-tooltip v-if="row.canDownload" content="下载" placement="top">
                <el-button
                  v-if="row.bizType === FILE_BIZ_TYPES.GENERAL.value"
                  tag="a"
                  :href="`/api/files/${row.id}`"
                  :download="row.originalName"
                  link
                  type="primary"
                  icon="Download"
                >
                  下载
                </el-button>
                <el-button
                  v-else
                  link
                  type="primary"
                  icon="Download"
                  :disabled="pending"
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="row.bizType === FILE_BIZ_TYPES.GENERAL.value" content="复制网址" placement="top">
                <el-button
                  link
                  type="primary"
                  icon="CopyDocument"
                  @click="handleCopyUrl(row)"
                >
                  复制网址
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="canRemove && row.canDelete" content="删除" placement="top">
                <el-button
                  link
                  type="primary"
                  icon="Delete"
                  :disabled="pending || loading"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </el-tooltip>
            </x-flex>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          class="pagination"
          :total="total"
          :page-sizes="[15, 25, 50]"
          :pager-count="5"
          background
          layout="total, sizes, prev, pager, next"
          @current-change="getList"
          @size-change="handleQuery"
        />
      </template>
    </el-card>
    <el-dialog
      v-model="uploadVisible"
      title="上传通用文件"
      width="min(560px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      :close-on-press-escape="!pending"
      :show-close="!pending"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="90px" :disabled="pending">
        <el-form-item label="选择文件">
          <el-upload
            v-model:file-list="uploadFiles"
            :auto-upload="false"
            :limit="1"
            :disabled="pending"
            :on-exceed="handleUploadExceed"
          >
            <el-button icon="Upload">选择文件</el-button>
            <template #tip><div class="form-tip">单个文件最大 50MiB。测试数据请在题目页面成对上传。</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="pending" label="上传进度">
          <el-progress :percentage="uploadProgress" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="pending" @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :disabled="pending || !uploadFiles.length" @click="submitUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>
  </x-flex>
</template>

<script setup lang="ts">
//import
import modal from '@/plugins/modal';
import { computed, onMounted, ref } from 'vue';
import { ElMessage, type UploadUserFile } from 'element-plus';
import { formatBytes } from '@/utils/utils';
import useClipboard from 'vue-clipboard3';
import { useUserStore } from '@/stores/user';
import { useAsyncChange } from '@/hooks/async/useAsyncChange';
import { useLoading } from '@/hooks/async/useLoading';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import { deleteAdminFiles, downloadAdminFile, getAdminFiles, uploadAdminFile } from '@/api/storage';
import { FILE_BIZ_TYPES } from '@/constants/file';
import type { FilePageQuery, StorageFileVO } from '@/types/storage';

//data
const userStore = useUserStore();
const { toClipboard } = useClipboard();
const queryParams = ref<FilePageQuery>({
  pageNum: 1,
  pageSize: 15,
  fileName: null,
  bizType: null,
  storageKey: null
});
const fileList = ref<StorageFileVO[]>([]);
const total = ref(0);
const { loading, withLoading } = useLoading();
const { ids, selectedRows: selectedFiles, handleSelectionChange } = useTableSelection((file: StorageFileVO) => file.id);
const canDeleteSelection = computed(() =>
  selectedFiles.value.length > 0 && selectedFiles.value.every(file => file.canDelete),
);
const { pending, beforeChange: runAction } = useAsyncChange();
const uploadVisible = ref(false);
const uploadFiles = ref<UploadUserFile[]>([]);
const uploadProgress = ref(0);
const canUpload = computed(() => userStore.hasEveryPermission(['file:file:upload']));
const canRemove = computed(() => userStore.hasPermission('file:file:remove'));
const businessTypesByCode = Object.fromEntries(
  Object.values(FILE_BIZ_TYPES).map(item => [item.value, item]),
);

//methods
const getList = () => withLoading(async () => {
    const response = await getAdminFiles(queryParams.value);
    fileList.value = response.data.data.records;
    total.value = response.data.data.total;
});

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 15 };
  getList();
};

const handleDownload = (row: StorageFileVO) => runAction(async () => {
  if (!row.canDownload) {
    return;
  }
  const response = await downloadAdminFile(row.id);
  const blob = response.data;
  if (!response.headers['content-disposition']) {
    ElMessage.error('文件下载失败');
    return;
  }
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = row.originalName.split(/[\\/]/).pop() || 'download';
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
});

const handleCopyUrl = async (row: StorageFileVO) => {
  try {
    const url = new URL(`/api/files/${row.id}`, window.location.origin);
    await toClipboard(url.href);
    ElMessage.success('公开网址已复制');
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限');
  }
};

const handleDelete = (row?: StorageFileVO) => runAction(async () => {
  const fileIds = row?.id || ids.value;
  if (!canRemove.value || (row ? !row.canDelete : !canDeleteSelection.value)) {
    return false;
  }
  await modal.confirm('是否确认删除选中的" '+ fileIds +' "文件?' , '删除确认', '删除');
  await deleteAdminFiles(fileIds);
  ElMessage.success('删除成功');
  await getList();
});

const handleUpload = () => {
  if (!canUpload.value) {
    return;
  }
  uploadFiles.value = [];
  uploadProgress.value = 0;
  uploadVisible.value = true;
};

const handleUploadExceed = () => ElMessage.warning('一次选择一个文件，请移除已选文件后重新选择');

const submitUpload = () => runAction(async () => {
  const file = uploadFiles.value[0]?.raw;
  if (!file || !canUpload.value) {
    return;
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('单个文件不能超过 50MiB');
    return;
  }
  uploadProgress.value = 0;
  const data = new FormData();
  data.append('file', file);
  await uploadAdminFile(data, event => {
    uploadProgress.value = Math.min(99, Math.round(event.loaded / Math.max(file.size, 1) * 100));
  });
  uploadProgress.value = 100;
  ElMessage.success('上传成功');
  uploadVisible.value = false;
  await getList();
});

onMounted(()=>{
  getList();
});
</script>

<style scoped>
.file-management {
  height: 100%;
  box-sizing: border-box;
  padding: 16px;
}

.filter-card {
  flex: none;
}

.list-card {
  flex: 1;
  display: grid;
  grid-template-rows: auto minmax(8rem, 1fr) auto;
  min-height: min-content;
}

.list-card :deep(.el-card__body) {
  contain: size;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.filter-select {
  width: 140px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-actions,
.list-actions {
  margin-inline-start: auto;
}

.file-management .x-flex > .el-button + .el-button {
  margin-left: 0;
}

.row-actions :deep(.el-button > span),
.list-actions :deep(.el-button.is-circle > span) {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.pagination {
  justify-content: safe flex-end;
  flex-wrap: wrap;
  row-gap: 8px;
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

  .filter-form :deep(.el-form-item) {
    width: 100%;
  }

  .filter-select {
    width: 100%;
  }
}
</style>
