<template>
  <div class="problem-case-management">
    <x-flex vertical :size="16">
      <el-card>
        <template #header>
          <!-- 页面标题 -->
          <x-flex justify="space-between">
            <span style="font-size: 22px; font-weight: 600; color: var(--ym-link)">
              测试用例管理
            </span>
            <el-button type="primary" :icon="ArrowLeft" @click="handleBack">返回题目列表</el-button>
          </x-flex>
        </template>
        <!-- 操作区域 -->
        <x-flex :size="10" align="center" style="padding-bottom: 15px;">
          <input ref="filePicker" type="file" multiple hidden accept=".in,.out" @change="uploadSelection" />
          <el-button
            v-permission="['problem:testcase:add']"
            type="primary"
            :icon="Upload"
            :loading="uploading"
            :disabled="loading || !loaded"
            @click="filePicker?.click()"
          >
            上传测试用例
          </el-button>
          <el-button
            v-permission="['problem:testcase:remove']"
            type="danger"
            :icon="Delete"
            :disabled="multiple || !loaded || loading || uploading"
            @click="deleteCases(selectedNames)"
          >
            批量删除
          </el-button>
          <el-button :icon="Refresh" :disabled="loading || uploading" @click="getList">刷新</el-button>
        </x-flex>

        <div v-if="uploading" class="upload-progress">
          <div class="upload-progress__status">{{ uploadStatus }}</div>
          <el-progress :percentage="uploadProgress" :stroke-width="20" :text-inside="true" striped />
        </div>

        <!-- 上传提示信息 -->
        <el-alert
          title="上传提示"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 15px;"
        >
          <p>暂仅支持上传.in和.out文件，必须成对上传（如：1.in 和 1.out）</p>
          <p>支持同时选择多个文件进行批量上传</p>
          <p>文件名建议使用数字编号，如：1.in, 1.out, 2.in, 2.out 等</p>
          <p>文件名也可以是其他命名，比如test01.in, test01.out, test02.in, test02.out 等</p>
          <p>单个文件大小不能超过50MB，所选文件一起发布</p>
        </el-alert>

        <!-- 测试用例列表 -->
        <el-table
          :data="testCaseList"
          v-loading="loading"
          @selection-change="handleSelectionChange"
          style="width: 100%"
        >
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="序号" type="index" width="80" align="center" />
          <el-table-column label="输入文件" prop="input" min-width="200" align="center">
            <template #default="scope">
              <x-flex :size="15" justify="center" align="center">
                {{ scope.row.name }}.in
                <el-button
                  type="primary"
                  :icon="Download"
                  @click="handleDownload(scope.row.input.fileId, scope.row.name + '.in')"
                >
                  下载输入
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
          <el-table-column label="输出文件" prop="output" min-width="200" align="center">
            <template #default="scope">
              <x-flex :size="15" justify="center" align="center">
                {{ scope.row.name }}.out
                <el-button
                  type="primary"
                  :icon="Download"
                  @click="handleDownload(scope.row.output.fileId, scope.row.name + '.out')"
                >
                  下载输出
                </el-button>
              </x-flex>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" align="center">
            <!-- 测试用例的删除操作 -->
            <template #default="scope">
              <el-button
                type="primary"
                plain
                :icon="View"
                @click="handlePreview(scope.row)"
              >
                查看
              </el-button>
              <el-button
                v-permission="['problem:testcase:remove']"
                type="danger"
                :icon="Delete"
                :disabled="loading || uploading || !loaded"
                @click="deleteCases([scope.row.name])"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </x-flex>
    <el-dialog
      v-model="previewVisible"
      width="85%"
      top="5vh"
      destroy-on-close
      @closed="handlePreviewClosed"
      :show-close="false"
    >
      <template #header>
        <div class="dialog-header">
          <h4 style="font-weight: bold;font-size: large">{{previewCaseName}}测试用例内容</h4>
          <el-button type="danger" @click="previewVisible = false">
            <el-icon class="el-icon--left"><CircleCloseFilled /></el-icon>
            Close
          </el-button>
        </div>
      </template>
      <div v-loading="previewLoading" class="case-preview-grid">
        <el-card
          v-for="preview in previewFiles"
          :key="preview.kind"
          shadow="never"
          class="case-preview-card"
        >
          <template #header>
            <x-flex justify="space-between" align="center" :wrap="false">
              <x-flex align="center" :size="10" :wrap="false" class="case-preview-card__title">
                <el-tag :type="preview.kind === 'input' ? 'primary' : 'success'" effect="dark">
                  {{ preview.kind === 'input' ? '输入' : '输出' }}
                </el-tag>
                <el-text truncated>{{ getDisplayFileName(preview.fileName) }}</el-text>
              </x-flex>
              <el-text v-if="preview.size > 0" type="info" size="small">
                {{ formatBinaryBytes(preview.size) }}
              </el-text>
            </x-flex>
          </template>

          <div class="case-preview-card__content">
            <el-empty v-if="preview.error" :description="preview.error" :image-size="64" />
            <PlainTextViewer
              v-else
              :content="preview.content"
              :aria-label="`${preview.kind === 'input' ? '输入' : '输出'}文件内容`"
            />
          </div>

          <div v-if="preview.truncated && !preview.error" class="case-preview-card__notice">
            <el-text type="warning" size="small">
              当前显示前 {{ formatBinaryBytes(PREVIEW_SIZE_LIMIT) }}
            </el-text>
            <el-button
              type="primary"
              size="small"
              link
              :loading="preview.fullLoading"
              @click="loadFullPreview(preview)"
            >
              加载完整内容
            </el-button>
          </div>
        </el-card>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
//import
import { useLoading } from '@/hooks/async/useLoading';
import modal from '@/plugins/modal';
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Upload, Delete, Refresh, Download, View, CircleCloseFilled } from '@element-plus/icons-vue';
import { formatBinaryBytes } from '@/utils/utils';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import PlainTextViewer from '@/components/common/PlainTextViewer.vue';
import type { ProblemCaseInfoVO } from '@/types/problem';
import { getProblemTestCases, uploadTestCaseFiles, downloadTestCaseFile, deleteTestCaseFiles } from '@/api/problem';

// data
const route = useRoute();
const router = useRouter();
const problemId = ref<number>(Number(route.params.id));
const testCaseList = ref<ProblemCaseInfoVO[]>([]);
const { loading, withLoading } = useLoading();
const uploading = ref<boolean>(false);
const { ids: selectedNames, multiple, handleSelectionChange, clearSelection } =
  useTableSelection((row: ProblemCaseInfoVO) => row.name);
const filePicker = ref<HTMLInputElement>();
const version = ref<number | null>(null);
const loaded = ref(false);
const uploadProgress = ref<number>(0);
const uploadStatus = ref<string>('');
const previewVisible = ref<boolean>(false);
const previewLoading = ref<boolean>(false);
const previewCaseName = ref<string>('');
let previewRequestId = 0;

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const PREVIEW_SIZE_LIMIT = 2 * 1024 * 1024;

type PreviewKind = 'input' | 'output';

interface PreviewFileState {
  kind: PreviewKind;
  fileName: string;
  fileId: string;
  content: string;
  size: number;
  error: string;
  truncated: boolean;
  fullLoading: boolean;
  blob?: Blob;
}

const createPreviewFile = (kind: PreviewKind, fileName = '', fileId = ''): PreviewFileState => ({
  kind,
  fileName,
  fileId,
  content: '',
  size: 0,
  error: '',
  truncated: false,
  fullLoading: false
});

const previewFiles = ref<PreviewFileState[]>([
  createPreviewFile('input'),
  createPreviewFile('output')
]);

//生命周期
onMounted(() => {
  getList();
});

//methods
//加载测试用例列表
const getList = () => withLoading(async () => {
  loaded.value = false;
  try {
    const { data } = await getProblemTestCases(problemId.value);
    testCaseList.value = data.data.cases;
    version.value = data.data.version;
    clearSelection();
    loaded.value = true;
  } catch (error) {
    ElMessage.error(getFileErrorMessage(error, '读取测试点失败'));
  }
});

const deleteCases = async (names: string[]) => {
  const baseline = version.value;
  if (baseline === null || names.length === 0) return;
  const confirmed = await modal.confirm(
    names.length === 1 ? '确认删除测试点 ' + names[0] + ' 吗？' : '确认删除所选的 ' + names.length + ' 组测试点吗？',
    '删除确认',
  ).then(() => true, () => false);
  if (!confirmed) return;
  loading.value = true;
  try {
    await deleteTestCaseFiles(problemId.value, baseline, names);
    ElMessage.success('测试点已删除');
  } catch (error) {
    ElMessage.error(getFileErrorMessage(error, '删除测试点失败'));
  } finally {
    await getList();
  }
};
//下载单个文件
const handleDownload = (fileId: string, fileName: string) => {
  downloadTestCaseFile(problemId.value, fileId).then(response=>{
    const disposition = response.headers["content-disposition"];
    if (!disposition) {
      ElMessage.error("文件下载失败");
      return;
    }
    const blob = new Blob([response.data], {
      type: "text/plain"
    });
    //创建下载链接
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }).catch((error) => {
    console.log(error);
  });
};

const getDisplayFileName = (fileName: string) => fileName.split('/').pop() || fileName;

const loadPreviewFile = async (preview: PreviewFileState) => {
  try {
    const response = await downloadTestCaseFile(problemId.value, preview.fileId, true);
    const blob = response.data;
    preview.blob = blob;
    preview.size = blob.size;
    preview.truncated = blob.size > PREVIEW_SIZE_LIMIT;
    preview.content = await blob.slice(0, PREVIEW_SIZE_LIMIT).text();
  } catch (error) {
    preview.error = getFileErrorMessage(error, '文件内容读取失败');
  }
};

const handlePreview = async (row: ProblemCaseInfoVO) => {
  const requestId = ++previewRequestId;
  previewCaseName.value = row.name;
  previewFiles.value = [
    createPreviewFile('input', row.name + '.in', row.input.fileId),
    createPreviewFile('output', row.name + '.out', row.output.fileId)
  ];
  previewVisible.value = true;
  previewLoading.value = true;

  try {
    await Promise.all(previewFiles.value.map(loadPreviewFile));
  } finally {
    if (requestId === previewRequestId) previewLoading.value = false;
  }
};

const handlePreviewClosed = () => {
  previewRequestId++;
  previewLoading.value = false;
};

const loadFullPreview = async (preview: PreviewFileState) => {
  if (!preview.blob) return;
  preview.fullLoading = true;
  try {
    preview.content = await preview.blob.text();
    preview.truncated = false;
  } catch (error) {
    console.error(error);
    ElMessage.error('完整内容加载失败');
  } finally {
    preview.fullLoading = false;
  }
};

//返回题目列表
const handleBack = () => {
  router.push('/admin/problem/list');
};
const getFileErrorMessage = (error: any, fallbackMessage: string, timeoutMessage = '请求超时') => {
  if (error?.data?.msg) return error.data.msg;
  if (error?.response?.data?.msg) return error.response.data.msg;
  if (error?.code === 'ETIMEDOUT') return timeoutMessage;
  if (error?.code === 'ECONNABORTED') return '请求已中断';
  if (error?.code === 'ERR_NETWORK') return '网络连接中断';
  if (error?.code === 'ERR_BAD_RESPONSE' && error?.response?.status === 200) return '服务响应格式错误';
  return error?.message || fallbackMessage;
};

const uploadSelection = async (event: Event) => {
  const picker = event.target as HTMLInputElement;
  const files = Array.from(picker.files ?? []);
  picker.value = '';
  if (!files.length || !loaded.value || uploading.value) return;
  if (files.some(file => !/\.(in|out)$/.test(file.name) || file.size > MAX_FILE_SIZE)) {
    ElMessage.warning('请选择 50 MB 以内的 .in 和 .out 文件');
    return;
  }
  const form = new FormData();
  files.forEach(file => form.append('file', file));
  if (version.value !== null) form.append('version', String(version.value));
  uploading.value = true;
  uploadProgress.value = 0;
  uploadStatus.value = '正在上传并发布测试点';
  try {
    await uploadTestCaseFiles(problemId.value, form, event => {
      if (event.total) uploadProgress.value = Math.min(99, Math.round(event.loaded / event.total * 100));
    });
    uploadProgress.value = 100;
    ElMessage.success('测试点已发布');
  } catch (error) {
    ElMessage.error(getFileErrorMessage(error, '上传失败', '上传超时'));
  } finally {
    await getList();
    uploading.value = false;
  }
};
</script>

<style scoped lang="css">
.problem-case-management {
  padding: 24px;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: var(--ym-table-header);
  color: var(--ym-table-heading);
  height: 40px;
  font-size: 13px;
  text-align: center;
}

:deep(.el-upload) {
  display: inline-block;
}

.upload-progress {
  margin-bottom: 15px;
}

.upload-progress__status {
  margin-bottom: 6px;
  color: var(--ym-regular-text);
  font-size: 13px;
}

.dialog-header {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  gap: 16px;
}

.case-preview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  height: min(68vh, 720px);
  min-height: 420px;
}

.case-preview-card {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

:deep(.case-preview-card .el-card__header) {
  padding: 12px 14px;
}

:deep(.case-preview-card .el-card__body) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  padding: 0;
}

.case-preview-card__title {
  min-width: 0;
  overflow: hidden;
}

.case-preview-card__title :deep(.el-text) {
  min-width: 0;
}

.case-preview-card__content {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.case-preview-card__content :deep(.el-empty) {
  width: 100%;
  height: 100%;
}

.case-preview-card__notice {
  display: flex;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-top: 1px solid var(--ym-panel-border);
  background: var(--ym-warning-panel);
}

@media (max-width: 900px) {
  .case-preview-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .case-preview-card {
    height: 380px;
  }
}

:deep(.el-alert__content) {
  padding-left: 8px;
}

:deep(.el-alert__content p) {
  margin: 4px 0;
  font-size: 13px;
}
</style>
