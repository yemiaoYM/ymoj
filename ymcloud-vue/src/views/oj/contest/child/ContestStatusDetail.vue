<template>
  <div class="container">
    <el-card v-if="dataLoading">
      <el-skeleton :rows="15" animated />
    </el-card>
    <n-flex vertical v-else>
      <!-- 状态显示 -->
      <n-flex>
        <el-alert :title="submissionData.status" :type="getStatusType(submissionData.status).alter" show-icon
          effect="dark" :closable="false" style="padding: 20px">
          <n-flex style="padding: 10px 0px">
            <n-flex align="center">
              <n-icon :component="TimeOutline" size="25" />
              <span>运行时间 {{ formatSubmissionTime(submissionData.time) }}</span>
            </n-flex>
            <n-flex align="center">
              <n-icon :component="HardwareChipSharp" size="25" />
              <span>运行内存
                {{ formatSubmissionMemory(submissionData.memory) }}</span>
            </n-flex>
          </n-flex>
        </el-alert>
      </n-flex>
      <!-- 错误信息展示 -->
      <el-card v-if="Object.keys(submissionData).length > 0 && submissionData.errorMessage">
        <template #header>
          <div class="card-header">
            <span>错误信息</span>
          </div>
        </template>
        <pre class="error-info">{{ submissionData.errorMessage }}</pre>
      </el-card>
      <!-- 提交详细信息 -->
      <el-card v-if="Object.keys(submissionData).length > 0">
        <el-descriptions title="提交详细信息" :column="4">
          <el-descriptions-item label="提交ID">
            {{submissionData.submissionId }}
          </el-descriptions-item>
          <el-descriptions-item label="题目">
            <span style="color: #409eff;font-weight: bold">
              {{ submissionData.displayId }} - {{ submissionData.problemTitle }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="用户">
            <span style="color: #409eff;font-weight: bold">
              {{ submissionData.username }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(submissionData.status).tag" effect="dark" size="large" disable-transitions>
              {{ submissionData.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="语言">
            {{ submissionData.language }}
          </el-descriptions-item>
          <el-descriptions-item label="运行时间">
            {{ formatSubmissionTime(submissionData.time) }}
          </el-descriptions-item>
          <el-descriptions-item label="运行内存">
            {{ formatSubmissionMemory(submissionData.memory) }}
          </el-descriptions-item>
          <el-descriptions-item label="代码长度">
            {{ formatSubmissionLength(submissionData.codeLength) }}
          </el-descriptions-item>
          <el-descriptions-item label="分数">
            <el-tag :type="getScoreType(submissionData.score)" effect="plain" size="large" disable-transitions>
              {{ submissionData.score !== null ? submissionData.score : "- -" }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ submissionData.createdTime }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
      <!-- 测试点详情 -->
      <el-card v-if="Object.keys(submissionData).length > 0 && submissionData.judgeCases && submissionData.judgeCases.length > 0">
        <template #header>
          <div class="card-header">
            <span>测试点详情</span>
          </div>
        </template>
        <n-flex>
          <div v-for="(judgeCase, index) in submissionData.judgeCases" :key="judgeCase.caseIndex" class="test-case-box"
            :class="getTestCaseClass(judgeCase.status)">
            <n-flex :size="0" justify="center" align="center" vertical>
              <span style="font-weight: bold">Test #{{ judgeCase.caseIndex }}</span>
              <span style="font-size: 20px; font-weight: bold">{{ judgeCase.status }}</span>
              <span style="font-size: 15px; font-weight: bold; color: #666">
                {{ formatSubmissionTime(judgeCase.time) }} / {{ formatSubmissionMemory(judgeCase.memory) }}</span>
              <n-flex align="center">
                <span>{{ judgeCase.score !== null ? judgeCase.score : "- -" }}</span>
                <n-icon v-if="judgeCase.status === 'Accepted'" :component="CheckmarkCircleOutline" size="25" />
                <n-icon v-else :component="CloseCircleOutline" size="25" />
              </n-flex>
            </n-flex>
          </div>
        </n-flex>
      </el-card>
      <!-- 代码展示 -->
      <el-card v-if="Object.keys(submissionData).length > 0 && submissionData.code">
        <template #header>
          <div class="card-header">
            <span>代码展示</span>
          </div>
        </template>
        <CodeHighLight :code="submissionData.code" :language="submissionData.language" />
        <template #footer>
          <el-button size="large" :icon="Clipboard" type="primary" @click="copyCode">复制代码</el-button>
        </template>
      </el-card>
    </n-flex>
  </div>
</template>

<script lang="ts">
import { ref, defineComponent, defineAsyncComponent, onMounted } from 'vue';
import {
  TimeOutline,
  HardwareChipSharp,
  CheckmarkCircleOutline,
  CloseCircleOutline,
  Clipboard,
} from "@vicons/ionicons5";
import {
  getStatusType,
  getScoreType,
  formatSubmissionMemory,
  formatSubmissionTime,
  formatSubmissionLength,
} from "@/utils/utils";
import useClipboard from "vue-clipboard3";
import type { SubmissionVO } from '@/type/types';
import { ElMessage } from 'element-plus';
import { getSubmissionDetail } from '@/api/api';
import { useRoute } from 'vue-router';
const CodeHighLight = defineAsyncComponent(
  () => import("@/components/common/CodeHighLight.vue")
);

export default defineComponent({
  name: 'ContestStatusDetail',
  components: {
    CodeHighLight,
  },
  setup() {
    const dataLoading = ref(true);
    const { toClipboard } = useClipboard();
    const submissionData = ref<SubmissionVO>({} as SubmissionVO);
    const route = useRoute();

    onMounted(() => {
      dataLoading.value = true;
      const submitId = Number(route.params.submitID);
      getSubmissionDetail(submitId).then(res => {
        submissionData.value = res.data.data;
      }).catch(e => {
        console.log(e);
      }).finally(() => {
        dataLoading.value = false;
      });
    })

    //复制代码
    const copyCode = () => {
      toClipboard(submissionData.value.code).then(() => {
        ElMessage.success("复制成功");
      }).catch(e => {
        ElMessage.warning("复制失败,请手动复制");
        console.log(e);
      });
    };
    //获取测试点状态
    const getTestCaseClass = (status: string) => {
      return status === "Accepted" ? "test-case-box AC" : "test-case-box";
    };

    return {
      dataLoading,
      toClipboard,
      submissionData,
      TimeOutline,
      HardwareChipSharp,
      CheckmarkCircleOutline,
      CloseCircleOutline,
      Clipboard,
      copyCode,
      getStatusType,
      getScoreType,
      formatSubmissionMemory,
      formatSubmissionTime,
      formatSubmissionLength,
      getTestCaseClass,
    }
  }
})
</script>

<style scoped>
.test-case-box {
  border: 1px solid #f56c6c;
  border-left: 3px solid #f56c6c;
  border-radius: 5px;
  padding: 15px 50px;
  color: #f56c6c;
  background-color: #fff;
}

.test-case-box.AC {
  border-color: #67c23a;
  color: #67c23a;
}
</style>
