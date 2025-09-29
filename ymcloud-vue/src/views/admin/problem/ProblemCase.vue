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
          <p>单个文件大小不能超过5MB</p>
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
const uploadRef = ref();
const selectedFiles = ref<ProblemCaseInfoVO[]>([]);
const uploadQueue = ref<File[]>([]);
const isProcessingUpload = ref<boolean>(false);


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
  const maxSize = 5 * 1024 * 1024;
  if (file.size > maxSize) {
    ElMessage.warning("文件大小不能超过5MB");
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
  //将文件添加到上传队列
  uploadQueue.value.push(file);
  //如果没有在处理上传，延迟一段时间后开始处理队列
  if (!isProcessingUpload.value) {
    setTimeout(() => {
      processUploadQueue();
    }, 500);
  }
};
//处理上传队列
const processUploadQueue = () => {
  if (isProcessingUpload.value || uploadQueue.value.length === 0) {
    return;
  }
  isProcessingUpload.value = true;
  uploading.value = true;
  //获取队列中的所有文件
  const filesToUpload = [...uploadQueue.value];
  //清空队列
  uploadQueue.value = []; 
  //创建FormData
  const formData = new FormData();
  filesToUpload.forEach(file => {
    formData.append('file', file);
  });
  //执行上传
  uploadTestCaseFiles(problemId.value, formData).then(response=>{
    ElMessage.success(response.data.msg);
    loadTestCases();
  }).catch(e=>{
    console.log(e);
  }).finally(()=>{
    isProcessingUpload.value = false;
    uploading.value = false;
  });
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

:deep(.el-alert__content) {
  padding-left: 8px;
}

:deep(.el-alert__content p) {
  margin: 4px 0;
  font-size: 13px;
}
</style>