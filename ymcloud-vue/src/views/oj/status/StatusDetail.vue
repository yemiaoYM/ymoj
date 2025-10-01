<template>
    <div class="container">
        <el-card v-if="dataLoading">
            <el-skeleton :rows="15" animated />
        </el-card>
        <n-flex vertical v-else>
            <!-- 状态显示 -->
            <n-flex>
                <el-alert
                    :title="submissionData.status"
                    :type="getStatusType(submissionData.status).alter"
                    show-icon
                    effect="dark"
                    :closable="false"
                    style="padding: 20px;"
                >
                    <n-flex style="padding: 10px 0px;">
                        <n-flex align="center">
                            <n-icon :component="TimeOutline" size="25"/>
                            <span>运行时间 {{ formatSubmissionTime(submissionData.time) }}</span>
                        </n-flex>
                        <n-flex align="center">
                            <n-icon :component="HardwareChipSharp" size="25"/>
                            <span>运行内存 {{ formatSubmissionMemory(submissionData.memory) }}</span>
                        </n-flex>
                    </n-flex>
                </el-alert>
            </n-flex>
            <!-- 错误信息展示 -->
            <el-card v-if="submissionData.errorMessage">
                <template #header>
                    <div class="card-header">
                        <span>错误信息</span>
                    </div>
                </template>
                <pre class="error-info">{{submissionData.errorMessage}}</pre>
            </el-card>
            <!-- 提交详细信息 -->
            <el-card>
                <el-descriptions title="提交详细信息" :column="4">
                    <el-descriptions-item label="提交ID">{{ submissionData.submissionId }}</el-descriptions-item>
                    <el-descriptions-item label="题目">
                        <span @click="goProblem(submissionData.displayId)" style="color: #409EFF; cursor: pointer;font-weight: bold;">
                            {{ submissionData.displayId +" - "+ submissionData.problemTitle }}
                        </span>
                    </el-descriptions-item>
                    <el-descriptions-item label="用户">
                        <span @click="goUser(submissionData.username)" style="color: #409EFF; cursor: pointer;font-weight: bold;">
                            {{ submissionData.username }}
                        </span>
                    </el-descriptions-item>
                    <el-descriptions-item label="状态">
                        <el-tag :type="getStatusType(submissionData.status).tag" effect="dark" size="large" disable-transitions>
                            {{ submissionData.status }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="语言">{{ submissionData.language }}</el-descriptions-item>
                    <el-descriptions-item label="运行时间">{{ formatSubmissionTime(submissionData.time) }}</el-descriptions-item>
                    <el-descriptions-item label="运行内存">{{ formatSubmissionMemory(submissionData.memory) }}</el-descriptions-item>
                    <el-descriptions-item label="代码长度">{{ formatSubmissionLength(submissionData.codeLength) }}</el-descriptions-item>
                    <el-descriptions-item label="分数">
                        <el-tag :type="getScoreType(submissionData.score)" effect="plain" size="large" disable-transitions>
                            {{ submissionData.score !== null ? submissionData.score : '- -' }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="提交时间">{{ submissionData.createdTime }}</el-descriptions-item>
                </el-descriptions>
            </el-card>
            <!-- 测试点详情 -->
            <el-card>
                <template #header>
                    <div class="card-header">
                        <span>测试点详情</span>
                    </div>
                </template>
                <n-flex>
                    <div
                        v-for="(judgeCase, index) in submissionData.judgeCases" 
                        :key="judgeCase.caseIndex" 
                        class="test-case-box" 
                        :class="getTestCaseClass(judgeCase.status)"
                    >
                        <n-flex :size="0" justify="center" align="center" vertical>
                            <span style="font-weight: bold;">Test #{{ judgeCase.caseIndex }}</span>
                            <span style="font-size: 20px;font-weight: bold;">{{ judgeCase.status }}</span>
                            <span style="font-size: 15px;font-weight: bold;color: #666;">{{ formatSubmissionTime(judgeCase.time) }} / {{ formatSubmissionMemory(judgeCase.memory) }}</span>
                            <n-flex align="center">
                                <span>{{ judgeCase.score !== null ? judgeCase.score : '- -' }}</span>
                                <n-icon v-if="judgeCase.status === 'Accepted'" :component="CheckmarkCircleOutline" size="25" />
                                <n-icon v-else :component="CloseCircleOutline" size="25" />
                            </n-flex>
                        </n-flex>
                    </div>
                </n-flex>
            </el-card>
            <!-- 代码展示 -->
            <el-card v-if="submissionData.code">
                <template #header>
                    <div class="card-header">
                        <span>代码展示</span>
                    </div>
                </template>
                <div style="overflow: auto;">
                    <CodeHighLight :code="submissionData.code" :language="submissionData.language" />
                </div>
                <template #footer>
                    <el-button size="large" :icon="Clipboard" type="primary" @click="copyCode">复制代码</el-button>
                    <template v-if="userStore.user.username==submissionData.username">
                        <el-button :loading="shareLoading" size="large" v-if="submissionData.share" :icon="CloseCircleOutline" type="warning" @click="handleUpdateShareCode">
                            取消分享
                        </el-button>
                        <el-button :loading="shareLoading" size="large" v-else :icon="ShareSocialSharp" type="primary" @click="handleUpdateShareCode">
                            分享代码
                        </el-button>
                    </template>
                </template>
            </el-card>
        </n-flex>
    </div>
</template>

<script setup lang="ts">
//import
import { ref, onMounted, defineAsyncComponent } from 'vue';
import { useRoute,useRouter } from 'vue-router';
import { 
    TimeOutline,
    HardwareChipSharp,
    CheckmarkCircleOutline,
    CloseCircleOutline,
    ShareSocialSharp,
    Clipboard
 } from '@vicons/ionicons5';
import { getSubmissionDetail,updateShareCode } from '@/api/api';
import { getStatusType,getScoreType,formatSubmissionMemory,formatSubmissionTime,formatSubmissionLength } from '@/utils/utils';
import type { SubmissionVO } from '@/type/types';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import useClipboard from 'vue-clipboard3';
const CodeHighLight = defineAsyncComponent(() => import('@/components/common/CodeHighLight.vue'));

//data
const route=useRoute();
const router=useRouter();
const userStore=useUserStore();
const dataLoading = ref(true);
const shareLoading = ref(false);
const { toClipboard } = useClipboard();
const submissionData = ref<SubmissionVO>({} as SubmissionVO);

//methods
onMounted(()=>{
    const rawId = route.params.submitID;
    //检查ID是否存在
    if (!rawId) {
        ElMessage.warning('提交 ID 不能为空');
        return;
    }
    //转换为数字
    const submitId = Number(rawId);
    if (Number.isNaN(submitId) || submitId <= 0) {
        ElMessage.warning('提交 ID 格式错误');
        return;
    }
    //dataLoading.value=false;
    //获取提交详情
    initSubmissionInfo(submitId);
})
//获取提交详情
const initSubmissionInfo=(submitId:number)=>{
    dataLoading.value=true;
    getSubmissionDetail(submitId).then((res)=>{
        submissionData.value=res.data.data;
    }).catch(err=>{
        console.log(err.message);
    }).finally(()=>{
        dataLoading.value=false;
    })
}
//跳转题目
const goProblem=(problemId:string)=>{
    router.push({ name: 'ProblemDetail', params: { problemID: problemId } });
}
//跳转用户主页
const goUser=(username:string)=>{
    router.push({name:'UserHome', query: { username: username } });
}
//获取测试点状态
const getTestCaseClass = (status: string) => {
  return status === "Accepted" ? "test-case-box AC" : "test-case-box";
};
//更新分享代码权限
const handleUpdateShareCode=()=>{
    shareLoading.value=true;
    updateShareCode(submissionData.value.submissionId).then((res)=>{
        ElMessage({
            message:"分享成功",
            grouping: true,
            type: 'success',
            plain: true
        })
        submissionData.value.share=!submissionData.value.share;
    }).catch(err=>{
        console.log(err.message);
    }).finally(()=>{
        shareLoading.value=false;
    })
}
//复制代码
const copyCode=async()=>{
    try{
        await toClipboard(submissionData.value.code);
        ElMessage.success("代码已复制到剪贴板");
    }catch(err){
        ElMessage.warning("复制失败,请手动复制");
        console.log(err);
    }
}
</script>


<style lang="css" scoped>
.container {
    width: 100%;
    height: 100%;
    padding: 15px 60px;
}
:deep(.el-descriptions__content) {
    font-size: 15px;
    font-weight: bold;
}
:deep(.el-tag__content) {
    font-size: 14px;
    font-weight: bold;
}
:deep(.el-alert__title) {
  font-size: 22px;
  font-weight: bold;
}
:deep(.el-alert__content){
    margin-left: 10px;
}
.error-info{
    font-size: 15px;
    font-family: Monaco, Consolas, monospace;
    white-space: pre-wrap;
    color: #333;
    line-height: 1.5;
}
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