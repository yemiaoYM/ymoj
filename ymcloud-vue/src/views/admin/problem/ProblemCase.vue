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
            ref="uploadRef"
            :action="uploadAction"
            :headers="uploadHeaders"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :before-upload="beforeUpload"
            :show-file-list="false"
            multiple
            accept=".in,.out,.txt"
          >
            <el-button type="primary" :icon="Upload" :loading="uploading">
              上传测试用例
            </el-button>
          </el-upload>
          <el-button 
            type="danger" 
            :icon="Delete" 
            :disabled="selectedFiles.length === 0" 
            @click="handleBatchDelete"
          >
            批量删除
          </el-button>
          <el-button :icon="Refresh" @click="refreshTestCases">刷新</el-button>
        </n-flex>

        <!-- 上传提示信息 -->
        <el-alert 
          title="上传说明" 
          type="info" 
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <template #default>
            <p>• 支持上传 .in 和 .out 文件，建议成对上传（如：1.in 和 1.out）</p>
            <p>• 支持同时选择多个文件进行批量上传</p>
            <p>• 文件名建议使用数字编号，如：1.in, 1.out, 2.in, 2.out 等</p>
          </template>
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
              <el-link 
                type="primary" 
                :underline="false"
                @click="handleDownload(scope.row.input)"
              >
                {{ scope.row.input }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="输出文件" prop="output" min-width="200" align="center">
            <template #default="scope">
              <el-link 
                type="primary" 
                :underline="false"
                @click="handleDownload(scope.row.output)"
              >
                {{ scope.row.output }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center">
            <template #default="scope">
              <n-flex :size="0" justify="center">
                <el-button 
                  class="operation-button" 
                  type="primary" 
                  size="small" 
                  text 
                  :icon="Download"
                  @click="handleDownloadPair(scope.row)"
                >
                  下载配对
                </el-button>
                <el-button 
                  class="operation-button" 
                  type="danger" 
                  size="small" 
                  text 
                  :icon="Delete"
                  @click="handleDeletePair(scope.row)"
                >
                  删除配对
                </el-button>
              </n-flex>
            </template>
          </el-table-column>
        </el-table>

        <!-- 空状态 -->
        <el-empty 
          v-if="!loading && testCaseList.length === 0" 
          description="暂无测试用例，请先上传文件"
          :image-size="120"
        />
      </el-card>
    </n-flex>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus';
import { ArrowLeft, Upload, Delete, Refresh, Download } from '@element-plus/icons-vue';
import type { ProblemCaseInfoVO } from '@/type/types';
import { useUserStore } from '@/stores/user';
import { 
  getProblemTestCases, 
  uploadTestCaseFiles, 
  downloadTestCaseFile, 
  deleteTestCaseFile
} from '@/api/api';

// data
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const problemId = ref<number>(Number(route.params.id));
const testCaseList = ref<ProblemCaseInfoVO[]>([]);
const selectedFiles = ref<ProblemCaseInfoVO[]>([]);
const loading = ref<boolean>(false);
const uploading = ref<boolean>(false);
const uploadRef = ref();

//上传配置
const uploadAction = computed(() => `/api/admin/problem/${problemId.value}/testcases/upload`);
const uploadHeaders = computed(() => ({
  'Authorization': userStore.token
}));

//生命周期
onMounted(() => {
  //loadTestCases();
});

//methods
//加载测试用例列表
const loadTestCases = async () => {
  loading.value = true;
  try {
    const res = await getProblemTestCases(problemId.value);
    testCaseList.value = res.data.data || [];
  } catch (e) {
    console.log(e);
  } finally {
    loading.value = false;
  }
};
//刷新测试用例列表
const refreshTestCases = () => {
  loadTestCases();
};
//选择文件变化
const handleSelectionChange = (selection: ProblemCaseInfoVO[]) => {
  selectedFiles.value = selection;
};
//上传前校验
const beforeUpload = (file: File) => {
  const allowedTypes = ['.in', '.out', '.txt'];
  const fileExtension = file.name.substring(file.name.lastIndexOf('.'));
  if (!allowedTypes.includes(fileExtension)) {
    ElMessage.error(`只允许上传 ${allowedTypes.join(', ')} 格式的文件`);
    return false;
  }
  const maxSize = 10 * 1024 * 1024; // 10MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 10MB');
    return false;
  }
  uploading.value = true;
  return true;
};
//上传成功回调
const handleUploadSuccess = (response: any) => {
  ElNotification({
    title: '上传成功',
    message: '测试用例文件上传成功',
    type: 'success'
  });
  //重新加载测试用例列表
  loadTestCases();
  uploading.value = false;
};
//上传失败回调
const handleUploadError = (error: any) => {
  console.log(error);
  ElMessage.error("上传失败");
  uploading.value = false;
};
//下载单个文件
const handleDownload = async (fileName: string) => {
  try {
    const response = await downloadTestCaseFile(problemId.value, fileName);
    //创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    
    ElMessage.success("下载成功");
  } catch (error) {
    console.log(error);
    ElMessage.error("下载失败");
  }
};
//下载配对文件
const handleDownloadPair = async (row: ProblemCaseInfoVO) => {
  try {
    await handleDownload(row.input);
    await handleDownload(row.output);
  } catch (error) {
    console.log(error);
  }
};
//删除配对文件
const handleDeletePair = async (row: ProblemCaseInfoVO) => {
  try {
    await ElMessageBox.confirm(
      `确认删除测试用例配对 "${row.input}" 和 "${row.output}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );

    await deleteTestCaseFile(problemId.value, row.input);
    await deleteTestCaseFile(problemId.value, row.output);
    
    ElMessage.success("删除成功");
    loadTestCases();
  } catch (error) {
    if (error !== 'cancel') {
      console.log(error);
      ElMessage.error("删除失败");
    }
  }
};
// 批量删除
const handleBatchDelete = async () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning("请选择要删除的测试用例");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selectedFiles.value.length} 组测试用例吗？`,
      '批量删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
    //批量删除所有选中的文件
    const deletePromises: Promise<any>[] = [];
    selectedFiles.value.forEach(file => {
      deletePromises.push(deleteTestCaseFile(problemId.value, file.input));
      deletePromises.push(deleteTestCaseFile(problemId.value, file.output));
    });

    await Promise.all(deletePromises);
    ElMessage.success("批量删除成功");
    loadTestCases();
  } catch (error) {
    if (error !== 'cancel') {
      console.log(error);
      ElMessage.error("批量删除失败");
    }
  }
};
//返回题目列表
const handleBack = () => {
  router.push('/admin/problem/list');
};
</script>

<style scoped lang="css">
.problem-case-management {
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

:deep(.el-upload) {
  display: inline-block;
}

:deep(.el-alert__content) {
  padding-left: 8px;
}

:deep(.el-alert__content p) {
  margin: 4px 0;
  font-size: 13px;
}
</style>