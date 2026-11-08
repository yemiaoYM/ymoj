<template>
  <div class="contest-container">
    <!-- 加载中 -->
    <el-card v-if="isLoading">
      <el-skeleton style="width: 100%" :rows="15" animated />
    </el-card>
    <!-- 比赛信息 -->
    <x-flex v-else vertical>
      <el-card>
        <!-- 标题 -->
        <template #header>
          <x-flex class="contest-header" justify="center" align="center" :size="16">
            <x-flex class="contest-heading" justify="center" align="center" :size="12">
              <h1>{{ contestData.title }}</h1>
              <!-- 创建者或主办方 -->
              <el-tag
                v-if="contestData.creatorUsername"
                @click="toUserInfo(contestData.creatorUsername)"
                size="large"
                disable-transitions
                effect="dark"
                style="cursor: pointer"
                color="#21c1c6"
              >
                <span style="font-weight: bold">{{ contestData.creatorUsername }}</span>
              </el-tag>
            </x-flex>
            <el-button
              v-if="contestData.canClone"
              v-permission="['contest:contest:add']"
              class="contest-clone-button"
              type="primary"
              plain
              :icon="CopyDocument"
              @click="cloneVisible = true"
            >
              克隆比赛
            </el-button>
          </x-flex>
        </template>
        <!-- 信息 -->
        <x-flex vertical>
          <!-- 标签 -->
          <x-flex :size="10" justify="center">
            <el-tag
              size="large"
              disable-transitions
              color="#ffc171"
              style="color: white"
            >
              <span style="display: flex; align-items: center">
                <el-icon>
                  <Trophy /> </el-icon
                >{{ formatContestMode(contestData.mode) }}
              </span>
            </el-tag>
            <el-tag
              size="large"
              disable-transitions
              color="#409eff"
              style="color: white"
              >{{ formatContestStyle(contestData.style) }}</el-tag
            >
            <el-tag
              size="large"
              disable-transitions
              color="#25bb9b"
              style="color: white"
            >
              {{ contestData.sourceId === ORIGINAL_CONTEST_SOURCE ? "原创" : "重建赛" }}
            </el-tag>
            <el-tag
              size="large"
              v-show="contestData.rated"
              disable-transitions
              color="#fe8053"
              style="color: white"
            >
              <span style="display: flex; align-items: center">
                <el-icon>
                  <Flag /> </el-icon
                >Rated
              </span>
            </el-tag>
          </x-flex>

          <!-- 时间信息：开始时间靠左，结束时间靠右 -->
          <x-flex justify="space-between">
            <x-flex>
              <el-icon color="#67c23a" :size="25"><component :is="Alarm" /></el-icon>
              <span>开始时间 {{ contestData.startTime }}</span>
            </x-flex>
            <x-flex>
              <el-icon color="#f56c6c" :size="25"><component :is="Alarm" /></el-icon>
              <span>结束时间 {{ contestData.endTime }}</span>
            </x-flex>
          </x-flex>

          <!-- 倒计时 -->
          <x-flex justify="center" align="center">
            <!-- 开始倒计时 -->
            <x-flex
              v-if="active"
              align="center"
              style="font-size: 25px; font-weight: 600"
            >
              <el-icon size="30"><component :is="HourglassOutline" /></el-icon>
              <el-countdown
                :value="countdownDeadline"
                :value-style="{ fontSize: '25px', fontWeight: 600, color: 'inherit' }"
              />
            </x-flex>
            <!-- 比赛状态 -->
            <x-flex v-else>
              <el-icon :size="25"><component :is="InfiniteSharp" /></el-icon>
              <span>{{ contestData.status }}</span>
            </x-flex>
          </x-flex>

          <!-- 参与人数 -->
          <x-flex v-if="contestData.count" justify="center" align="center">
            <el-icon size="25" color="#55a8ff"><component :is="People" /></el-icon>
            <span>{{ contestData.count }} 人参与</span>
          </x-flex>
        </x-flex>
      </el-card>

      <CloneContestDialog
        v-model="cloneVisible"
        :source-id="contestID"
        :title="contestData.title"
      />

      <!-- 选项卡 -->
      <el-tabs
        v-if="Object.keys(contestData).length > 0"
        @tab-click="handleTabClick"
        v-model="routeName"
      >
        <!-- 简介 -->
        <el-tab-pane name="ContestDetail" lazy>
          <template #label>
            <el-icon style="margin-right: 5px">
              <Notebook />
            </el-icon>
            <span>简介</span>
          </template>
          <el-card
            v-if="!contestData.access"
            style="text-align: center; width: 500px; margin: 0 auto"
          >
            <el-result
              icon="warning"
              title="需要验证比赛密码"
              sub-title="比赛结束后也可输入正确密码进行只读复盘"
            >
              <template #extra>
                <el-input
                  v-model="contestPassword"
                  type="password"
                  placeholder="请输入比赛密码"
                  @keydown.enter="handleRegisterContest"
                  style="width: 70%; margin-right: 10px"
                />
                <el-button type="primary" @click="handleRegisterContest"
                  >进入</el-button
                >
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
            <el-icon style="margin-right: 5px">
              <Operation />
            </el-icon>
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
            <el-icon style="margin-right: 5px">
              <Menu />
            </el-icon>
            <span>提交</span>
          </template>
        </el-tab-pane>
        <!-- 排名 -->
        <el-tab-pane name="ContestRank" lazy :disabled="contestMenuDisabled">
          <template #label>
            <el-icon style="margin-right: 5px">
              <Histogram />
            </el-icon>
            <span>排名</span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <!-- 路由视图 -->
      <router-view class="router-view"
        v-if="contestData.access && !contestMenuDisabled"
      ></router-view>
    </x-flex>
  </div>
</template>

<script setup lang="ts">
import { ORIGINAL_CONTEST_SOURCE } from '@/constants/contest';
//import
import { onMounted, ref, watch, defineAsyncComponent } from "vue";
import CloneContestDialog from "@/components/admin/CloneContestDialog.vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { CopyDocument } from "@element-plus/icons-vue";
import {
  Alarm,
  HourglassOutline,
  InfiniteSharp,
  People,
} from "@vicons/ionicons5";
import type { ContestVO } from "@/type/types";
import { formatContestMode, formatContestStyle } from "@/utils/utils";
import { getContestInfo, registerContest } from "@/api/api";
const MarkdownPreview = defineAsyncComponent(
  () => import("@/components/common/markdown/MarkdownPreview.vue")
);

//data
const router = useRouter();
const route = useRoute();
const isLoading = ref<boolean>(true);
const cloneVisible = ref(false);
const contestMenuDisabled = ref<boolean>(false);
const active = ref<boolean>(true);
const countdownDeadline = ref(0);
const contestPassword = ref<string>("");
const contestData = ref<ContestVO>({} as ContestVO);

const routeName = ref<string>("ContestDetail");
const contestID = ref<number>(0);

onMounted(() => {
  //获取contestID和设置routeName
  contestID.value = Number(route.params.contestID);
  console.log("now router:", route.name);
  console.log("contestID:", contestID.value);
  routeName.value = route.name as string;
  if (routeName.value == "ContestProblemDetail") {
    routeName.value = "ContestProblemList";
  }
  //获取contest信息
  initContestInfo(contestID.value);
});

//methods
//获取contest信息
const initContestInfo = (contestID: number) => {
  isLoading.value = true;
  getContestInfo(contestID)
    .then((res) => {
      contestData.value = res.data.data;
      active.value = contestData.value.status === "进行中";
      contestMenuDisabled.value = !contestData.value.access;
      // 以服务端剩余时间换算本地截止点，避免客户端时钟偏差。
      const remaining = new Date(contestData.value.endTime).getTime()
        - new Date(contestData.value.now).getTime();
      countdownDeadline.value = Date.now() + Math.max(0, remaining);
      if (!contestData.value.access) {
        ElMessage.error("对不起，请先到比赛首页输入比赛密码进行注册！");
        routeName.value = "ContestDetail";
        contestMenuDisabled.value = true;
      }
      if (contestData.value.status === "未开始" && !contestData.value.canManage) {
        ElMessage.warning("比赛还未开始");
        routeName.value = "ContestDetail";
        contestMenuDisabled.value = true;
      }
    })
    .catch((e) => {
      console.log(e);
    })
    .finally(() => {
      isLoading.value = false;
    });
};
//切换选项卡
const handleTabClick = (tab: any) => {
  let name = tab.paneName;
  if (name != route.name) {
    router.push({ name: name });
  }
};
//注册报名比赛
const handleRegisterContest = () => {
  registerContest({
    contestId: contestID.value,
    password: contestPassword.value,
  })
    .then((res) => {
      ElMessage.success(res.data.msg);
      contestPassword.value = "";
      initContestInfo(contestID.value);
    })
    .catch((e) => {
      console.log(e);
    });
};
//跳转到用户主页
const toUserInfo = (username: string) => {
  router.push({ name: "UserHome", query: { username: username } });
};

watch(
  () => route.name,
  (newName: any) => {
    routeName.value = newName as string;
    if (routeName.value == "ContestProblemDetail") {
      routeName.value = "ContestProblemList";
    }
  }
);
</script>

<style lang="css" scoped>
.contest-container {
  width: 100%;
  height: 100%;
}

.contest-header {
  position: relative;
  padding-inline: 140px;
  min-height: 32px;
}

.contest-heading {
  min-width: 0;
  max-width: 100%;
  text-align: center;
}

.contest-heading h1 {
  margin: 0;
  font-size: 30px;
  font-weight: bold;
  overflow-wrap: anywhere;
}

.contest-clone-button {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
}

:deep(.status-list-container) {
  padding: 0px;
}

:deep(.el-tabs__nav-wrap) {
  background: var(--ym-surface);
  border-radius: 3px;
}

:deep(.el-tabs--top .el-tabs__item.is-top:nth-child(2)) {
  padding-left: 20px;
}

:deep(.router-view .status-list-container){
  padding: 0px;
}

@media screen and (max-width: 640px) {
  .contest-header {
    padding-inline: 0;
  }

  .contest-heading {
    flex-basis: 100%;
  }

  .contest-clone-button {
    position: static;
    margin-left: auto;
    transform: none;
  }
}

@media screen and (min-width: 1024px) {
  .contest-container {
    padding: 10px 50px;
  }
}
</style>
