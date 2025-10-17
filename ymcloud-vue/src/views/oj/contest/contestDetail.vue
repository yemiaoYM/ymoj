<template>
  <div class="contest-container">
    <!-- 加载中 -->
    <el-card v-if="isLoading">
      <el-skeleton style="width: 100%;" :rows="15" animated/>
    </el-card>
    <!-- 比赛信息 -->
    <n-flex v-else vertical>
      <el-card>
        <!-- 标题 -->
        <template #header>
          <n-flex justify="center" align="center">
            <h1 style="font-weight: bold;font-size: 30px;">{{ contestData.title }}</h1>
            <!-- 创建者或主办方 -->
            <el-tag v-if="contestData.creatorUsername" 
                    @click="toUserInfo(contestData.creatorUsername)"
                    size="large" 
                    disable-transitions 
                    effect="dark" 
                    style="margin-left: 10px;cursor:pointer;" 
                    color="#21c1c6">
                <span style="font-weight: bold;">{{ contestData.creatorUsername }}</span>
            </el-tag>
          </n-flex>
        </template>
        <!-- 信息 -->
        <n-flex vertical>
          <!-- 标签 -->
          <n-flex :size="10" justify="center">
            <el-tag size="large" disable-transitions color="#ffc171" style="color: white;">
                <span style="display: flex;align-items: center;">
                    <el-icon><Trophy /></el-icon>{{formatContestMode(contestData.mode)}}
                </span>
            </el-tag>
            <el-tag size="large" disable-transitions color="#409eff" style="color: white;">{{formatContestStyle(contestData.style)}}</el-tag>
            <el-tag size="large" disable-transitions color="#25bb9b" style="color: white;">
                {{ contestData.sourceId === 0 ? '原创' : '重建赛' }}
            </el-tag>
            <el-tag
                size="large"
                v-show="contestData.rated" 
                disable-transitions
                color="#fe8053" 
                style="color: white;">
                <span style="display: flex;align-items: center;">
                    <el-icon><Flag /></el-icon>Rated
                </span>
            </el-tag>
          </n-flex>

          <!-- 时间信息：开始时间靠左，结束时间靠右 -->
          <n-flex justify="space-between">
            <n-flex>
              <n-icon :size="25" :component="Alarm" color="#67c23a"></n-icon>
              <span>开始时间 {{ contestData.startTime }}</span>
            </n-flex>
            <n-flex>
              <n-icon :size="25" :component="Alarm" color="#f56c6c"></n-icon>
              <span>结束时间 {{ contestData.endTime }}</span>
            </n-flex>
          </n-flex>

          <!-- 倒计时 -->
          <n-flex justify="center" align="center">
            <!-- 开始倒计时 -->
            <n-flex v-if="active" align="center" style="font-size: 25px;font-weight: 600;">
              <n-icon size="30" :component="HourglassOutline"></n-icon>
              <n-countdown 
                :duration="new Date(contestData.endTime).getTime() - new Date(contestData.now).getTime()"
                :active="active"
                />
            </n-flex>
            <!-- 比赛状态 -->
            <n-flex v-else>
              <n-icon :size="25" :component="InfiniteSharp"></n-icon>
              <span>{{ contestData.status }}</span>
            </n-flex>
          </n-flex>

          <!-- 参与人数 -->
          <n-flex v-if="contestData.count" justify="center" align="center">
            <n-icon size="25" :component="People" color="#55a8ff"></n-icon>
            <span>{{ contestData.count }} 人参与</span>
          </n-flex>
        </n-flex>
      </el-card>

      <!-- 选项卡 -->
      <el-tabs v-if="Object.keys(contestData).length > 0" @tab-click="handleTabClick" v-model="routeName">
          <!-- 简介 -->
          <el-tab-pane 
            name="ContestDetail"
            lazy
            :disabled="contestMenuDisabled"
            >
            <template #label>
              <el-icon style="margin-right: 5px;"><Notebook /></el-icon>
                <span>简介</span>
            </template>
            <el-card v-if="!contestData.access" 
              style="text-align: center;width: 500px;margin: 0 auto;">
              <el-result
                icon="warning"
                title="需要密码才能加入比赛"
                sub-title="请输入密码"
              >
                <template #extra>
                  <el-input
                  v-model="contestPassword"
                  type="password"
                  placeholder="请输入比赛密码"
                  @keydown.enter="handleRegisterContest"
                  style="width:70%;margin-right: 10px;"
                />
                <el-button
                  type="primary"
                  @click="handleRegisterContest"
                  >进入</el-button>
                </template>
              </el-result>
            </el-card>
            <el-card v-else>
              <MarkdownPreview :content="contestData.description" />
            </el-card>
          </el-tab-pane>
          <!-- 题目 -->
          <el-tab-pane 
            name="ContestProblemList" 
            lazy
            :disabled="contestMenuDisabled"
            >
            <template #label>
              <el-icon style="margin-right: 5px;"><Operation /></el-icon>
                <span>题目</span>
            </template>
          </el-tab-pane>
          <!-- 提交 -->
          <el-tab-pane 
            name="ContestSubmissionList" 
            lazy
            :disabled="contestMenuDisabled"
            >
            <template #label>
                <el-icon style="margin-right: 5px;"><Menu /></el-icon>
                <span>提交</span>
            </template>
          </el-tab-pane>
          <!-- 排名 -->
          <el-tab-pane 
            name="ContestRank" 
            lazy
            :disabled="contestMenuDisabled"
            >
            <template #label>
                <el-icon style="margin-right: 5px;"><Histogram /></el-icon>
                <span>排名</span>
            </template>
          </el-tab-pane>
      </el-tabs>
      <!-- 路由视图 -->
      <router-view v-if="contestData.access && !contestMenuDisabled"></router-view>
    </n-flex>
</div>
</template>

<script setup lang="ts">
//import
import { onMounted, ref,watch,defineAsyncComponent } from 'vue';
import { useRouter,useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Alarm,HourglassOutline,InfiniteSharp,People } from '@vicons/ionicons5';
import type { ContestVO } from '@/type/types';
import { formatContestMode, formatContestStyle } from '@/utils/utils';
import { getContestInfo, registerContest } from '@/api/api';
const MarkdownPreview=defineAsyncComponent(() => import('@/components/common/MarkdownPreview.vue'));

//data
const router=useRouter();
const route=useRoute();
const isLoading=ref<boolean>(true);
const contestMenuDisabled = ref<boolean>(false);
const active=ref<boolean>(true);
const contestPassword = ref<string>('');
const contestData = ref<ContestVO>({} as ContestVO);

const routeName = ref<string>('ContestDetail');
const contestID=ref<number>(0);

onMounted(()=>{
  //获取contestID和设置routeName
  contestID.value = Number(route.params.contestID);
  console.log('now router:', route.name);
  console.log('contestID:', contestID.value);
  routeName.value = route.name as string;
  if (routeName.value == 'ContestProblemDetail') {
    routeName.value = 'ContestProblemList';
  }
  //获取contest信息
  initContestInfo(contestID.value);
})

//methods
//获取contest信息
const initContestInfo=(contestID: number)=>{
  isLoading.value=true;
  getContestInfo(contestID).then(res=>{
    contestData.value = res.data.data;
    active.value = contestData.value.status === '进行中';
    if(!contestData.value.access){
      ElMessage.error('对不起，请先到比赛首页输入比赛密码进行注册！');
      routeName.value="ContestDetail";
      contestMenuDisabled.value = true;
    }
    if(contestData.value.status === '未开始'){
      ElMessage.warning('比赛还未开始');
      routeName.value="ContestDetail";
      contestMenuDisabled.value = true;
    }
  }).catch(e=>{
    console.log(e);
  }).finally(()=>{
    isLoading.value=false;
  })
}
//切换选项卡
const handleTabClick=(tab: any)=>{
  let name = tab.paneName;
  if(name != route.name){
    router.push({ name: name });
  }
};
//注册报名比赛
const handleRegisterContest=()=>{
  registerContest({
    contestId:contestID.value,
    password:contestPassword.value
  }).then(res=>{
    ElMessage.success(res.data.msg);
    contestData.value.access = true;
  }).catch(e=>{
    console.log(e);
  })
}
//跳转到用户主页
const toUserInfo = (username: string) => {
  router.push({ name: "UserHome", query: { username: username } });
};

watch(() => route.name, (newName:any) => {
  routeName.value = newName as string;
  if (routeName.value == 'ContestProblemDetail') {
    routeName.value = 'ContestProblemList';
  }
})
</script>

<style lang="css" scoped>
.contest-container {
  width: 100%;
  height: 100%;
}

:deep(.status-list-container){
  padding: 0px;
}

:deep(.el-tabs__nav-wrap){
  background: #fff;
  border-radius: 3px;
}

:deep(.el-tabs--top .el-tabs__item.is-top:nth-child(2)){
  padding-left: 20px;
}

@media screen and (min-width: 1024px) {
  .contest-container {
    padding: 10px 50px;
  }
}
</style>
