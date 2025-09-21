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

submissionData.value={
        "submissionId": 10113,
        "problemTitle": "第K最近",
        "displayId": "YMOJ-1006",
        "username": "admin",
        "language": "C++",
        "code": "#include <bits/stdc++.h> \nusing namespace std;\n/*---define constant---*/\n\nusing ll =long long;\n//const int mod = 1e9 + 7;\nconst int mod = 998244353;\nconst double eps = 1e-8;\nconst double pi = acos(-1.0);\nconst int int_max = 2147483647;\nconst ll ie18 = 1000000000000000000;\nconst int ie9 = 1000000000;\n\n/*---diy namespace---*/\n\nnamespace Math {\n    const int math_N=2e5+5;\n    ll gcd(ll a, ll b) { return b ? gcd(b, a % b) : a; }\n    ll qpow(ll a, ll b) { ll ans = 1; a %= mod; while (b) { if (b & 1)ans = ans * a % mod; a = a * a % mod; b >>= 1; }return ans; }\n    ll inv(ll a) { return qpow(a, mod - 2); }\n    ll lcm(ll a, ll b) { return a / gcd(a, b)* b; }\n    ll lowbit(ll x) { return x & (-x); }\n    ll exgcd(ll a, ll b, ll& x, ll& y) {\n        if (b == 0) { x = 1, y = 0; return a; }\n        ll x1, y1, d;\n        d = exgcd(b, a % b, x1, y1);\n        x = y1, y = x1 - a / b * y1;\n        return d;\n    }\n    ll jc[math_N] = { 1,1 }, iv[math_N] = { 1,1 };\n    ll C(ll n, ll m) {return jc[n] * iv[m] % mod * iv[n - m] % mod;}\n    void init() {for (ll i = 2; i <= math_N-5; i++){jc[i] = jc[i - 1] * i % mod;iv[i] = (iv[i - 1] * inv(i) % mod) % mod;}}\n}\n\n//using namespace Math;\n\n/*---code---*/\nconst int N = 2e5 + 5;\nint n,q;\nint a[N],b,k;\nint check(int x){\n    //>=b-x\n    int l=lower_bound(a+1,a+1+n,b-x)-a;\n    //>b+x\n    int r=upper_bound(a+1,a+1+n,b+x)-a;\n    return r-l;\n}\nvoid solve(){\n    cin>>n>>q;\n    for(int i=1;i<=n;i++)cin>>a[i];\n    sort(a+1,a+1+n);\n    while(q--){\n        cin>>b>>k;\n        int l=-1,r=2e8+1;\n        while(l+1!=r){\n            int mid=(l+r)>>1;\n            if(check(mid)<k)l=mid;\n            else r=mid;\n        }\n        cout<<r<<\"\\n\";\n    }\n}\n\n/*---main---*/\n\nint main() {\n    ios::sync_with_stdio(0), cin.tie(0), cout.tie(0);\n    //freopen(\"a.txt\",\"r\",stdin);\n    //freopen(\"cout.txt\",\"w\",stdout);\n    int T = 1;\n    // cin >> T;\n    while(T--)\n        solve();\n    return 0;\n}",
        "codeLength": 1916,
        "status": "Accepted",
        "score": 100,
        "time": 510,
        "memory": 3788,
        "errorMessage": null,
        "share": false,
        "judgeServer": "LocalJudgeServer",
        "createdTime": "2025-08-31 01:05:01",
        "judgeCases": [
            {
                "caseIndex": 1,
                "status": "Accepted",
                "time": 20,
                "memory": 3320,
                "score": null
            },
            {
                "caseIndex": 2,
                "status": "Accepted",
                "time": 10,
                "memory": 3232,
                "score": null
            },
            {
                "caseIndex": 3,
                "status": "Accepted",
                "time": 20,
                "memory": 3388,
                "score": null
            },
            {
                "caseIndex": 4,
                "status": "Accepted",
                "time": 130,
                "memory": 3332,
                "score": null
            },
            {
                "caseIndex": 5,
                "status": "Accepted",
                "time": 450,
                "memory": 3656,
                "score": null
            },
            {
                "caseIndex": 6,
                "status": "Accepted",
                "time": 250,
                "memory": 3320,
                "score": null
            },
            {
                "caseIndex": 7,
                "status": "Accepted",
                "time": 440,
                "memory": 3672,
                "score": null
            },
            {
                "caseIndex": 8,
                "status": "Accepted",
                "time": 330,
                "memory": 3568,
                "score": null
            },
            {
                "caseIndex": 9,
                "status": "Accepted",
                "time": 440,
                "memory": 3708,
                "score": null
            },
            {
                "caseIndex": 10,
                "status": "Accepted",
                "time": 80,
                "memory": 3376,
                "score": null
            },
            {
                "caseIndex": 11,
                "status": "Accepted",
                "time": 440,
                "memory": 3656,
                "score": null
            },
            {
                "caseIndex": 12,
                "status": "Accepted",
                "time": 320,
                "memory": 3636,
                "score": null
            },
            {
                "caseIndex": 13,
                "status": "Accepted",
                "time": 440,
                "memory": 3600,
                "score": null
            },
            {
                "caseIndex": 14,
                "status": "Accepted",
                "time": 70,
                "memory": 3664,
                "score": null
            },
            {
                "caseIndex": 15,
                "status": "Accepted",
                "time": 430,
                "memory": 3600,
                "score": null
            },
            {
                "caseIndex": 16,
                "status": "Accepted",
                "time": 430,
                "memory": 3540,
                "score": null
            },
            {
                "caseIndex": 17,
                "status": "Accepted",
                "time": 510,
                "memory": 3672,
                "score": null
            },
            {
                "caseIndex": 18,
                "status": "Accepted",
                "time": 180,
                "memory": 3436,
                "score": null
            },
            {
                "caseIndex": 19,
                "status": "Accepted",
                "time": 280,
                "memory": 3788,
                "score": null
            },
            {
                "caseIndex": 20,
                "status": "Accepted",
                "time": 10,
                "memory": 3196,
                "score": null
            },
            {
                "caseIndex": 21,
                "status": "Accepted",
                "time": 160,
                "memory": 3632,
                "score": null
            }
        ]
    }

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