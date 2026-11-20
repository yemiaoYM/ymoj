<template>
    <x-flex class="container" :size="[0,0]">
        <el-splitter :layout="layout" style="height: 100%;width: 100%;">
            <el-splitter-panel collapsible min="25%">
                <!-- 题目详细 -->
                <el-tabs class="problem-tabs" v-model="activeTabName" type="border-card" @tab-click="handleTabClick" style="height: 100%;width: 100%;">
                    <el-tab-pane label="题目详细" name="problemDetail" style="height: 100%;">
                        <el-scrollbar always style="height: 100%;">
                            <article class="problem-statement">
                                <header class="problem-heading">
                                    <h1>{{ problem.title }}</h1>
                                    <x-flex vertical :size="12" class="problem-meta">
                                        <p>题目 ID：{{ problem.problemId }}</p>
                                        <p>
                                            出题人：
                                            <el-link @click="handToUserInfo(problem.authorUsername)" underline="never">
                                                {{ problem.authorUsername }}
                                            </el-link>
                                        </p>
                                        <p>
                                            <el-tag type="warning">时间限制</el-tag>
                                            C/C++ {{ formatSubmissionTime(problem.timeLimit) }}，其他语言 {{ formatSubmissionTime(2 * problem.timeLimit) }}
                                        </p>
                                        <p>
                                            <el-tag>空间限制</el-tag>
                                            C/C++ {{ formatBinaryBytes(problem.memoryLimit, 'KiB') }}，其他语言 {{ formatBinaryBytes(2 * problem.memoryLimit, 'KiB') }}
                                        </p>
                                    </x-flex>
                                </header>
                                <section class="statement-section">
                                    <h2>题目描述</h2>
                                    <MarkdownPreview :contest-id="isContest ? contestId : undefined" :content="problem.content" />
                                </section>
                                <section class="statement-section">
                                    <h2>输入描述</h2>
                                    <MarkdownPreview :contest-id="isContest ? contestId : undefined" :content="problem.input" />
                                </section>
                                <section class="statement-section">
                                    <h2>输出描述</h2>
                                    <MarkdownPreview :contest-id="isContest ? contestId : undefined" :content="problem.output" />
                                </section>
                                <section class="statement-section">
                                    <h2>测试用例</h2>
                                    <section v-for="(test, index) in problem.problemCaseList" :key="index" class="sample-case">
                                        <h3>样例 {{ index + 1 }}</h3>
                                        <x-flex vertical :size="16">
                                            <div class="sample-block">
                                                <x-flex align="center" justify="space-between" class="sample-label">
                                                    <h4>输入</h4>
                                                    <el-button text :icon="DocumentCopy" @click="copyText(test.input)">复制</el-button>
                                                </x-flex>
                                                <el-scrollbar max-height="360px">
                                                    <pre class="sample-data" v-text="test.input" />
                                                </el-scrollbar>
                                            </div>
                                            <div class="sample-block">
                                                <x-flex align="center" justify="space-between" class="sample-label">
                                                    <h4>输出</h4>
                                                    <el-button text :icon="DocumentCopy" @click="copyText(test.output)">复制</el-button>
                                                </x-flex>
                                                <el-scrollbar max-height="360px">
                                                    <pre class="sample-data" v-text="test.output" />
                                                </el-scrollbar>
                                            </div>
                                            <div v-if="test.note" class="sample-note">
                                                <h4>说明</h4>
                                                <p v-text="test.note" />
                                            </div>
                                        </x-flex>
                                    </section>
                                </section>
                                <footer v-if="problem.source" class="problem-source">来源：{{ problem.source }}</footer>
                            </article>
                        </el-scrollbar> 
                    </el-tab-pane>
                    <!-- 用户当前题目提交记录 -->
                    <el-tab-pane label="提交记录" name="submissionTable">
                        <el-table
                            :data="submissionData" 
                            stripe
                            empty-text="暂无提交记录"
                            highlight-current-row
                            highlight-hover-row
                            class="submission-table"
                        >
                            <el-table-column prop="createdTime" label="提交时间" width="200" align="center">
                                <template #default="{ row }">
                                    {{ formatRelativeTime(row.createdTime) }}
                                </template>
                            </el-table-column>
                            <el-table-column prop="status" label="状态" width="200" align="center">
                                <template #default="{ row }">
                                <el-tag 
                                    :type="getStatusType(row.status).tag" 
                                    effect="dark"
                                    size="large"
                                    disable-transitions
                                    round
                                    >
                                    {{ row.status }}
                                </el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column prop="time" label="运行时间" align="center">
                                <template #default="{ row }">
                                {{ formatSubmissionTime(row.time) }}
                                </template>
                            </el-table-column>
                            <el-table-column prop="memory" label="运行内存" align="center">
                                <template #default="{ row }">
                                {{ formatBinaryBytes(row.memory, 'KiB') }}
                                </template>
                            </el-table-column>
                            <el-table-column prop="language" label="语言" align="center">
                                <template #default="{ row }">
                                    <el-tooltip content="查看提交详情" placement="top" >
                                    <span style="color: var(--ym-link); cursor: pointer;" @click="handSubmissionDetail(row.submissionId)">{{row.language}}</span>
                                    </el-tooltip>
                                </template>
                            </el-table-column>
                        </el-table>

                        <el-pagination 
                            style="margin-top: 15px; display: flex;justify-content:right;"
                            background layout="prev, pager, next" 
                            :current-page="pageNo"
                            :total="total" 
                            @update:current-page="handleCurrentChange"
                            />
                    </el-tab-pane>
                </el-tabs>
            </el-splitter-panel>
            <el-splitter-panel collapsible min="40%">
                <!-- 编辑器 -->
                <CodeMirror
                    v-if="!problemLoading && Object.keys(problem).length>0"
                    v-model:code="code"
                    :language="language"
                    :is-contest="isContest"
                    :submission-allowed="submissionAllowed"
                    :contest-id="contestId"
                    :problem-id="problem.problemId"
                    :problem-case-list="problem.problemCaseList ?? []"
                    :languages="problem.languageList"
                    :accepted="problem.accepted"
                />
            </el-splitter-panel>
        </el-splitter>
    </x-flex>
</template>

<script setup lang="ts">
//import
import { onMounted, ref, onBeforeUnmount, defineAsyncComponent, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DocumentCopy } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { ProblemVO, EditorCache } from '@/types/problem';
import type { SubmissionTableVO } from '@/types/submission';
import { getProblemDetail } from '@/api/problem';
import { getProblemSubmissionList } from '@/api/submission';
import { getContestProblemInfo, getContestProblemSubmissionList } from '@/api/contest';
import Storage from '@/utils/storage';
import { formatBinaryBytes, getStatusType, formatSubmissionTime, formatRelativeTime } from '@/utils/utils';
import useClipboard from 'vue-clipboard3';
const MarkdownPreview = defineAsyncComponent(() => import('@/components/common/markdown/MarkdownPreview.vue'));
const CodeMirror = defineAsyncComponent(() => import('@/components/common/Codemirror.vue'));

//data
const route=useRoute();
const router=useRouter();
const { toClipboard }=useClipboard();
const isMobile=ref<boolean>(false);
const layout=ref<'horizontal' | 'vertical'>("horizontal");
const activeTabName=ref<string>("problemDetail");
const problemId=ref<string>('');
const isContest=ref<boolean>(false);
const contestId=ref<number>(0);
const clockNow = ref(Date.now());
const serverOffset = ref(0);
let clockTimer: ReturnType<typeof setInterval> | undefined;
const submissionAllowed = computed(() => {
    if (!isContest.value) {
        return true;
    }
    const start = problem.value.submissionStartTime;
    const end = problem.value.submissionEndTime;
    const now = clockNow.value + serverOffset.value;
    return start !== null && end !== null && now >= new Date(start).getTime() && now < new Date(end).getTime();
});
const code=ref<string>(`//C语言模板(C99)
#include<stdio.h>
int main(){
  printf("hello");
  return 0;
}
//Java必须要以Main为类名(Java21)
import java.util.Scanner; 
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("hello");
    }
}
//python代码 输入要注意格式(Python3)
a, b = map(int, input().split())
print(a+b)
//c++模板代码(C++17)
#include<iostream>
using namespace std;
int main(){
  int a,b;
  cin>>a>>b;
  cout<<a+b;
  return 0;
}`);
const problem=ref<ProblemVO>({} as ProblemVO);
const submissionData=ref<SubmissionTableVO[]>([] as SubmissionTableVO[]);

const pageNo=ref<number>(1);
const pageSize=ref<number>(10);
const total=ref<number>(0);
const problemLoading=ref<boolean>(true);
const language=ref<string>('C++');

//methods
//监听屏幕变化
const checkMobile = () => {
  isMobile.value = window.innerWidth <= 1024;
  if(isMobile.value){
    layout.value="vertical";
  }else{
    layout.value="horizontal";
  }
}
onMounted(()=>{
    //获取路由信息
    const routerName=route.name;
    const routerParams=route.params;
    console.log(routerName);
    if(routerName==="ProblemDetail"){
        problemId.value=routerParams.problemID as string;
        isContest.value=false;
    }else if(routerName==="ContestProblemDetail"){
        isContest.value=true;
        contestId.value=Number(routerParams.contestID);
        problemId.value=routerParams.displayID as string;
        clockTimer = setInterval(() => { clockNow.value = Date.now(); }, 1000);
    }
    //获取题目信息
    initProblemInfo();
    //自适应变化
    checkMobile()
    window.addEventListener('resize', checkMobile);
})
onBeforeUnmount(() => {
    clearInterval(clockTimer);
    window.removeEventListener('resize', checkMobile)
})

//获取题目
const initProblemInfo=()=>{
    problemLoading.value=true;
    if(isContest.value&&contestId.value>0){
        //查询比赛题目
        getContestProblemInfo(contestId.value,problemId.value).then(res=>{
            problem.value=res.data.data;
            serverOffset.value = new Date(problem.value.serverTime as string).getTime() - Date.now();
            if(problem.value.languageList.length>0){
                language.value=problem.value.languageList[0].name;
            }
        }).catch(e=>{
            console.log(e);
        }).finally(()=>{
            handleGetCache();
            problemLoading.value=false;
        })
    }else{
        //查询普通题目
        getProblemDetail(problemId.value).then(res=>{
            problem.value=res.data.data;
            if(problem.value.languageList.length>0){
                language.value=problem.value.languageList[0].name;
            }
        }).catch(e=>{
            console.log(e);
        }).finally(()=>{
            handleGetCache();
            problemLoading.value=false;
        })
    }
}
//获取提交记录
const page=()=>{
    if(isContest.value&&contestId.value>0){
        //查询比赛题目提交记录
        getContestProblemSubmissionList({
            contestId:contestId.value,
            displayId:problemId.value,
            pageSize:pageSize.value,
        }).then(res=>{
            submissionData.value=res.data.data.records;
            total.value=res.data.data.total;
        }).catch(e=>{
            console.log(e);
        })
    }else{
        //查询普通题目提交记录
        getProblemSubmissionList({
            problemId:problemId.value,
            pageNo:pageNo.value,
            pageSize:pageSize.value,
        }).then(res=>{
            submissionData.value=res.data.data.records;
            total.value=res.data.data.total;
        }).catch(e=>{
            console.log(e);
        })
    }
}
//提取编辑器缓存
const handleGetCache=()=>{
    let key=null;
    if(isContest.value){
        key="Problem_"+contestId.value+"_"+problemId.value;
    }else{
        key="Problem_"+problemId.value;
    }
    const param=Storage.get<EditorCache>(key);
    if(param){
        code.value=param.code;
        language.value=param.language;
    }
}
const handleCurrentChange=(val:number)=>{
    pageNo.value=val;
    page();
}
const handSubmissionDetail=(submissionId:number)=>{
    router.push({name:'SubmissionDetail',params:{submitID:submissionId}});
}
//切换标签
const handleTabClick=(tab:any)=>{
    console.log(tab.paneName);
    if(tab.paneName==="submissionTable"){
        page();
    }
}
const handToUserInfo=(username:string)=>{
    router.push({name:'UserHome',query:{username:username}})
}
//复制方式
const copyText = async (text: string) => {
    try{
        await toClipboard(text);
        ElMessage.success("复制成功!");
    }catch(err){
        ElMessage.warning("复制失败,请手动复制");
        console.log(err);
    }
}
</script>

<style scoped>
.container {
    display: flex;
    width: 100%;
    height: calc(100vh - 60px);
}
.problem-tabs :deep(.el-tabs__content) {
    padding: 10px 0 0 15px;
}
.problem-statement {
    padding: 20px;
    color: var(--ym-problem-text);
    font-size: 16px;
    line-height: 1.8;
}
.problem-statement,
.sample-data,
.submission-table {
    font-family: Consolas, "PingFang SC", "Microsoft YaHei", monospace;
}
.problem-heading h1 {
    margin-bottom: 20px;
    color: var(--ym-problem-link-hover);
    font-size: 24px;
    font-weight: 600;
    line-height: 1.5;
}
.problem-meta {
    padding: 16px;
    border: 1px solid var(--ym-sample-border);
    border-left: 3px solid var(--ym-sample-accent);
    border-radius: 8px;
    background: var(--ym-code-surface);
    color: var(--ym-problem-text);
}
.problem-meta :deep(.el-tag) {
    margin-right: 8px;
    font-size: 14px;
}
.problem-meta :deep(.el-link) {
    font-size: inherit;
}
.statement-section {
    margin-top: 32px;
}
.statement-section h2 {
    margin-bottom: 16px;
    color: var(--ym-problem-link-hover);
    font-size: 20px;
    font-weight: 600;
}
.statement-section :deep(.md-editor-preview) {
    padding: 0;
    font-size: 16px;
    line-height: 1.8;
}
.statement-section :deep(.md-editor-preview img) {
    max-width: 100%;
    height: auto;
}
.sample-case + .sample-case {
    margin-top: 28px;
    padding-top: 24px;
    border-top: 1px solid var(--ym-problem-border);
}
.sample-case h3 {
    margin-bottom: 12px;
    font-size: 18px;
    font-weight: 500;
}
.sample-block {
    min-width: 0;
    background: var(--ym-code-surface);
}
.sample-label {
    padding: 8px 16px;
    border-bottom: 1px solid var(--ym-sample-border);
}
.sample-label h4,
.sample-note h4 {
    font-size: 16px;
    font-weight: 500;
}
.sample-data {
    width: max-content;
    min-width: 100%;
    padding: 12px 16px;
    font-size: 16px;
    line-height: 1.7;
    white-space: pre;
    tab-size: 4;
}
.sample-note p {
    margin-top: 8px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}
.problem-source {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid var(--ym-problem-border);
    color: var(--el-text-color-secondary);
    font-size: 14px;
}
@media (max-width: 640px) {
    .problem-statement {
        padding: 20px 16px;
    }
}
</style>
