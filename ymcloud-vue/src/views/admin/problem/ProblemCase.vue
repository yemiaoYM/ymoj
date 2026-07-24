<template>
  <div class="problem-case-management">
    <n-flex vertical :size="16">
      <el-card>
        <template #header>
          <!-- 页面标题 -->
          <n-flex justify="space-between">
            <span style="font-size: 22px; font-weight: 600; color: #409eff">
              测试用例管理
            </span>
            <el-button type="primary" :icon="ArrowLeft" @click="handleBack">返回题目列表</el-button>
          </n-flex>
        </template>
        <!-- 操作区域 -->
        <n-flex :size="10" align="center" style="padding-bottom: 15px;">
          <el-upload
            :http-request="uploadFiles"
            :before-upload="beforeUpload"
            :show-file-list="false"
            :disabled="uploading"
            multiple
            accept=".in,.out"
          >
            <el-button type="primary" :icon="Upload" :loading="uploading">
              上传测试用例
            </el-button>
          </el-upload>
          <el-button type="danger" :icon="Delete" :disabled="selectedFiles.length === 0" @click="handleBatchDelete">批量删除</el-button>
          <el-button :icon="Refresh" @click="refreshTestCases">刷新</el-button>
        </n-flex>

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
          <p>单个文件大小不能超过50MB，大批量文件会自动分批上传</p>
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
              <n-flex :size="15" justify="center" align="center">
                {{ scope.row.input }}
                <el-button 
                  type="primary" 
                  :icon="Download"
                  @click="handleDownload(scope.row.input)"
                >
                  下载输入
                </el-button>
              </n-flex>
            </template>
          </el-table-column>
          <el-table-column label="输出文件" prop="output" min-width="200" align="center">
            <template #default="scope">
              <n-flex :size="15" justify="center" align="center">
                {{ scope.row.output }}
                <el-button 
                  type="primary" 
                  :icon="Download"
                  @click="handleDownload(scope.row.output)"
                >
                  下载输出
                </el-button>
              </n-flex>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center">
            <!-- 测试用例的删除操作 -->
            <template #default="scope">
              <el-button 
                type="danger" 
                :icon="Delete"
                @click="handleDeletePair(scope.row)"
              >
                删除测试用例
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </n-flex>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, Upload, Delete, Refresh, Download } from '@element-plus/icons-vue';
import type { ProblemCaseInfoVO } from '@/type/types';
import { 
  getProblemTestCases,
  uploadTestCaseFiles,
  downloadTestCaseFile, 
  deleteTestCaseFiles
} from '@/api/api';

// data
const route = useRoute();
const router = useRouter();
const problemId = ref<number>(Number(route.params.id));
const testCaseList = ref<ProblemCaseInfoVO[]>([]);
const loading = ref<boolean>(false);
const uploading = ref<boolean>(false);
const selectedFiles = ref<ProblemCaseInfoVO[]>([]);
const uploadQueue = ref<File[]>([]);
const uploadProgress = ref<number>(0);
const uploadStatus = ref<string>('');
let uploadTimer: ReturnType<typeof setTimeout> | undefined;

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const MAX_BATCH_SIZE = 20 * 1024 * 1024;
// Tomcat 10.1 默认最多解析 10 个 multipart 部件，每组测试用例包含 2 个文件。
const MAX_PAIRS_PER_BATCH = 5;
const MAX_UPLOAD_ATTEMPTS = 3;


//生命周期
onMounted(() => {
  loadTestCases();
});

//methods
//加载测试用例列表
const loadTestCases = () => {
  loading.value = true;
  getProblemTestCases(problemId.value).then(res => {
    testCaseList.value = res.data.data || [];
  }).catch(e => {
    console.log(e);
  }).finally(() => {
    loading.value = false;
  });
};
//刷新测试用例列表
const refreshTestCases = () => {
  loadTestCases();
};
//批量删除
const handleBatchDelete = () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning("请选择要删除的测试用例");
    return;
  }
  ElMessageBox.confirm(
    `确认删除选中的 ${selectedFiles.value.length} 组测试用例吗？`,
    '批量删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    //收集所有需要删除的文件名
    const fileNames: string[] = [];
    selectedFiles.value.forEach(file => {
      fileNames.push(file.input);
      fileNames.push(file.output);
    });
    //删除测试用例
    deleteTestCaseFiles(problemId.value, fileNames).then(res => {
      ElMessage.success(res.data.msg);
      loadTestCases();
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => { });
};
//选择文件变化
const handleSelectionChange = (selection: ProblemCaseInfoVO[]) => {
  selectedFiles.value = selection;
};
//上传前校验
const beforeUpload = (file: File) => {
  if (!/\.(in|out)$/.test(file.name)) {
    ElMessage.warning("只允许上传.in和.out格式的文件");
    return false;
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.warning("单个文件大小不能超过50MB");
    return false;
  }
  return true;
};
//下载单个文件
const handleDownload = (fileName: string) => {
  downloadTestCaseFile(problemId.value, fileName).then(response=>{
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
//删除配对文件
const handleDeletePair = (row: ProblemCaseInfoVO) => {
  ElMessageBox.confirm(
    `确认删除测试用例配对 "${row.input}" 和 "${row.output}" 吗？`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    deleteTestCaseFiles(problemId.value, [row.input, row.output]).then(res => {
      ElMessage.success(res.data.msg);
      loadTestCases();
    }).catch(e => {
      console.log(e);
    });
  }).catch(() => { });
};
//返回题目列表
const handleBack = () => {
  router.push('/admin/problem/list');
};
//提交上传
const uploadFiles = (options: any) => {
  const { file } = options;
  uploadQueue.value.push(file);
  if (uploadTimer) clearTimeout(uploadTimer);
  // el-upload 会为每个文件分别触发一次自定义请求，等待选择事件收集完成后统一配对。
  uploadTimer = setTimeout(processUploadQueue, 300);
};

const buildUploadBatches = (files: File[]) => {
  const pairs = new Map<string, { in?: File; out?: File }>();
  files.forEach(file => {
    const dotIndex = file.name.lastIndexOf('.');
    const baseName = file.name.slice(0, dotIndex);
    const extension = file.name.slice(dotIndex + 1) as 'in' | 'out';
    const pair = pairs.get(baseName) || {};
    if (pair[extension]) {
      throw new Error(`存在重名文件：${file.name}`);
    }
    pair[extension] = file;
    pairs.set(baseName, pair);
  });

  const orderedPairs = [...pairs.entries()]
    .sort(([left], [right]) => left.localeCompare(right, undefined, { numeric: true }))
    .map(([baseName, pair]) => {
      if (!pair.in || !pair.out) {
        throw new Error(`测试用例 ${baseName} 缺少配对的 .in 或 .out 文件`);
      }
      return [pair.in, pair.out];
    });

  const batches: File[][] = [];
  let currentBatch: File[] = [];
  let currentSize = 0;
  orderedPairs.forEach(pair => {
    const pairSize = pair[0].size + pair[1].size;
    const reachedSizeLimit = currentSize + pairSize > MAX_BATCH_SIZE;
    const reachedCountLimit = currentBatch.length / 2 >= MAX_PAIRS_PER_BATCH;
    if (currentBatch.length > 0 && (reachedSizeLimit || reachedCountLimit)) {
      batches.push(currentBatch);
      currentBatch = [];
      currentSize = 0;
    }
    currentBatch.push(...pair);
    currentSize += pairSize;
  });
  if (currentBatch.length > 0) batches.push(currentBatch);
  return { batches, pairCount: orderedPairs.length };
};

const shouldRetryUpload = (error: any) => {
  if (!error?.isAxiosError) return false;
  return !error.response || error.response.status >= 500;
};

const getUploadErrorMessage = (error: any) => {
  if (error?.data?.msg) return error.data.msg;
  if (error?.response?.data?.msg) return error.response.data.msg;
  if (error?.code === 'ECONNABORTED' || error?.message?.includes('timeout')) return '上传超时';
  if (error?.message === 'Network Error') return '网络连接中断';
  return error?.message || '上传失败';
};

const uploadBatch = async (
  files: File[],
  completedBytes: number,
  totalBytes: number
) => {
  const formData = new FormData();
  files.forEach(file => formData.append('file', file));
  const batchSize = files.reduce((sum, file) => sum + file.size, 0);
  let maxLoaded = 0;

  for (let attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
    try {
      await uploadTestCaseFiles(problemId.value, formData, event => {
        maxLoaded = Math.max(maxLoaded, Math.min(event.loaded, batchSize));
        uploadProgress.value = totalBytes === 0
          ? 100
          : Math.min(99, Math.round((completedBytes + maxLoaded) / totalBytes * 100));
      });
      return;
    } catch (error) {
      if (attempt === MAX_UPLOAD_ATTEMPTS || !shouldRetryUpload(error)) throw error;
      uploadStatus.value = `连接中断，正在进行第 ${attempt + 1} 次尝试...`;
      await new Promise(resolve => setTimeout(resolve, attempt * 1000));
    }
  }
};

//处理上传队列
const processUploadQueue = async () => {
  uploadTimer = undefined;
  if (uploading.value || uploadQueue.value.length === 0) {
    return;
  }
  uploading.value = true;
  uploadProgress.value = 0;
  const filesToUpload = uploadQueue.value.splice(0);
  let uploadedPairs = 0;
  let pairCount = 0;

  try {
    const uploadPlan = buildUploadBatches(filesToUpload);
    pairCount = uploadPlan.pairCount;
    const totalBytes = filesToUpload.reduce((sum, file) => sum + file.size, 0);
    let completedBytes = 0;

    for (let index = 0; index < uploadPlan.batches.length; index++) {
      const batch = uploadPlan.batches[index];
      uploadStatus.value = `正在上传第 ${index + 1}/${uploadPlan.batches.length} 批（已完成 ${uploadedPairs}/${pairCount} 组）`;
      await uploadBatch(batch, completedBytes, totalBytes);
      completedBytes += batch.reduce((sum, file) => sum + file.size, 0);
      uploadedPairs += batch.length / 2;
      uploadProgress.value = Math.round(completedBytes / totalBytes * 100);
    }

    ElMessage.success(`成功上传 ${pairCount} 组测试用例`);
  } catch (error) {
    const completed = uploadedPairs > 0 ? `，已成功上传 ${uploadedPairs}/${pairCount} 组` : '';
    ElMessage.error(`${getUploadErrorMessage(error)}${completed}`);
  } finally {
    if (uploadedPairs > 0) loadTestCases();
    uploading.value = false;
    uploadStatus.value = '';
  }
};
</script>

<style scoped lang="css">
.problem-case-management {
  padding: 24px;
}

:deep(.el-table__header-wrapper th) {
  word-break: break-word;
  background-color: #f8f8f9;
  color: #515a6e;
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
  color: #606266;
  font-size: 13px;
}

:deep(.el-alert__content) {
  padding-left: 8px;
}

:deep(.el-alert__content p) {
  margin: 4px 0;
  font-size: 13px;
}
</style>
