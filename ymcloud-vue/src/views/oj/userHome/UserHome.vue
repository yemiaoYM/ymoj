<template>
  <div class="container">
    <x-flex class="container-content" vertical>
      <!-- 头像部分 -->
      <x-flex justify="center" style="margin-top: 10px;">
        <Avatar
          :src="userForm.avatar || ''" 
          :text="userForm.username" 
          :size="150"
        />
      </x-flex>
      <!-- 用户信息部分 -->
      <el-card class="card-container" shadow="never">
        <!-- 最近登录时间 -->
        <x-flex justify="center" class="last-login-container">
          <el-popover placement="top" trigger="hover" :content="userForm.loginDate" :width="160">
            <template #reference>
              <el-tag type="success" class="last-login-time" effect="plain" round>
                <div style="display: flex; align-items: center;">
                  <span class="online-indicator"></span>
                  <span style="margin-left: 5px;">最近上线时间: {{ userForm.loginDate ? formatRelativeTime(userForm.loginDate) : '暂无记录' }}</span>
                </div>
              </el-tag>
            </template>
          </el-popover>
        </x-flex>
        <!-- 用户信息头部 -->
        <x-flex justify="center" align="center" style="display: flex;">
          <el-icon size="40"><component :is="PersonCircleOutline" /></el-icon>
          <span style="font-size: 30px;font-weight: bold;">{{ userForm.username }}</span>
          <el-icon v-if="userForm.sex === '男'" size="30" color="#409eff"><component :is="Male" /></el-icon>
          <el-icon v-if="userForm.sex === '女'" size="30" color="#F56C6C"><component :is="Female" /></el-icon>
        </x-flex>
        <!-- 学校信息 -->
        <x-flex v-if="userForm.school" justify="center" align="center" style="margin-top: 5px; font-size: 15px;">
          <el-icon size="25"><component :is="School" /></el-icon>{{ userForm.school }}
        </x-flex>
        <!-- 附属信息 -->
        <x-flex justify="center" align="center" style="margin-top: 10px;">
          <!-- 昵称 -->
          <el-tag v-if="userForm.nickname" size="large" disable-transitions effect="dark" style="margin-left: 10px;"
            color="#21c1c6">
            <span style="font-weight: bold;">{{ userForm.nickname }}</span>
          </el-tag>
          <!-- 博客 -->
          <el-tag v-if="userForm.blog" size="large" disable-transitions effect="dark" style="margin-left: 10px;"
            color="#dd6161">
            <el-link target="_blank" :href="userForm.blog" underline="never" :icon="AtCircle"
              style="color: #fff;">我的博客</el-link>
          </el-tag>
        </x-flex>
        <hr style="margin-top: 10px;">
        <!-- 已解决和总提交数 -->
        <x-flex style="display: flex; margin-top: 10px; text-align: center;">
          <el-card class="status-card" style="background-color: #67c23a;" shadow="hover">
            <x-flex vertical align="center">
              <span>
                <el-icon size="20" color="white" style="vertical-align: middle;">
                  <SuccessFilled />
                </el-icon>
                已解决
              </span>
              <div style="font-size: 28px; font-weight: bold; margin-top: 5px;">{{ userForm.ac }}</div>
            </x-flex>
          </el-card>
          <el-card class="status-card" style="background-color: #87ceeb;" shadow="hover">
            <x-flex vertical align="center">
              <span>
                <el-icon size="20" color="white" style="vertical-align: middle;">
                  <Grid />
                </el-icon>
                总提交数
              </span>
              <div style="font-size: 28px; font-weight: bold; margin-top: 5px;">{{ userForm.total }}</div>
            </x-flex>
          </el-card>
        </x-flex>
        <!-- 最近一年提交热力表 -->
        <el-card style="margin-top: 20px;" shadow="never">
          <template #header>最近一年提交热力表</template>
          <CalendarHeatMap :values="heatmapData" :end-date="endDate" />
        </el-card>
        <!-- 个人简介和做题情况 -->
        <el-card style="margin-top: 20px;" shadow="never">
          <el-tabs class="profile-tabs" type="card" v-model="activeTab">
            <el-tab-pane name="1" label="个人简介">
              <MarkdownPreview v-if="userForm.signature" :content="userForm.signature" public-images />
            </el-tab-pane>
            <el-tab-pane name="2" label="做题情况">
              <template v-if="userForm.problemId && userForm.problemId.length > 0">
                <x-flex justify="center" align="center" style="margin-top: 10px;">
                  <span>已解决问题列表</span>
                  <el-button type="primary" :icon="Refresh" @click="initUserInfo" circle />
                </x-flex>
                <x-flex justify="center" style="margin-top: 10px;">
                  <el-button
                    v-for="problem in userForm.problemId"
                    :key="problem"
                    style="margin: 5px;"
                    type="success"
                    @click="goToProblem(problem)"
                  >
                    {{ problem }}
                  </el-button>
                </x-flex>
              </template>
              <template v-else>
                这家伙太懒了，还没有做过题...
              </template>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-card>
    </x-flex>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, watch, defineAsyncComponent } from 'vue';
const Avatar = defineAsyncComponent(() => import('@/components/common/Avatar.vue'));
const MarkdownPreview = defineAsyncComponent(() => import('@/components/common/markdown/MarkdownPreview.vue'));
const CalendarHeatMap = defineAsyncComponent(() => import('@/components/common/CalendarHeatMap.vue'));
import type { UserInfoVO, UserCalendarHeatmapVO } from '@/types/user';
import { getUserInfoByUsername, getUserCalendarHeatmap } from '@/api/user';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { School, PersonCircleOutline, Female, Male, Refresh, AtCircle } from '@vicons/ionicons5';
import { formatRelativeTime } from '@/utils/utils';

//data
const router = useRouter();
const route = useRoute();
const userForm = ref<UserInfoVO>({} as UserInfoVO);
const heatmapData = ref<UserCalendarHeatmapVO['dataList']>([]);
const endDate = ref(new Date().toString());
const activeTab = ref('1');

//methods
//初始化用户信息
const initUserInfo = () => {
  const username = route.query.username;
  if (!username || Array.isArray(username)) {
    ElMessage.error('用户名参数错误');
    return;
  }
  handGetUserHomeInfo(username);
  handGetUserCalendarHeatmap(username);
}
//获取用户信息
const handGetUserHomeInfo = (username: string) => {
  getUserInfoByUsername(username).then(res => {
    userForm.value = res.data.data;
  }).catch(e => {
    console.log("用户获取用户信息失败", e);
  })
}
//获取用户日历热力图
const handGetUserCalendarHeatmap = (username: string) => {
  getUserCalendarHeatmap(username).then(res => {
    heatmapData.value = res.data.data.dataList;
    endDate.value = res.data.data.date;
  }).catch(e => {
    console.log("用户获取日历热力图失败", e);
  })
}
//跳转至题目详情
const goToProblem = (problemId: string) => {
  router.push({ name: 'ProblemDetail', params: { problemID: problemId } });
}
//监听路由参数变化
watch(
  () => route.query.username,
  (newUsername, oldUsername) => {
    if (newUsername !== oldUsername) {
      initUserInfo();
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.container {
  height: 100%;
  width: 100%;
  padding: 20px;
}

.container-content {
  max-width: 900px;
  margin: 0 auto;
}

.card-container {
  position: relative;
  line-height: 1.6;
  width: 100%;
  border-radius: 12px;
  box-shadow: 0 4px 12px var(--ym-profile-shadow);
  border: none;
}

.card-container :deep(.el-card__body) {
  padding: 20px 24px;
}

.card-container :deep(.el-card__header) {
  padding: 19px 24px 20px;
  border-bottom: 0;
  font-size: 18px;
  font-weight: 500;
}

.card-container :deep(.el-card__header + .el-card__body) {
  padding-top: 0;
}

.profile-tabs {
  --el-tabs-header-height: 42px;
}

.profile-tabs :deep(.el-tabs__item) {
  --el-color-primary: #18a058;
}

.profile-tabs :deep(.el-tabs__item:not(.is-active)) {
  background-color: var(--ym-profile-fill);
}

.last-login-container {
  position: absolute;
  top: 15px;
  right: 15px;
}

.status-card {
  flex: 1;
  color: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px var(--ym-profile-shadow);

  span {
    font-size: 17px;
    font-weight: bold;
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.online-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #67C23A;
  vertical-align: middle;
}

/* 响应式设计：当屏幕宽度小于等于 768px 时生效 */
@media (max-width: 768px) {
  .last-login-container {
    position: static;
  }

  .status-card {
    flex: none;
    width: 100%;
  }
}
</style>
