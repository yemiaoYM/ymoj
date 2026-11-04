<template>
  <div class="dashboard-container">
    <x-flex vertical :size="24">
      <!-- 欢迎横幅 -->
      <el-card class="welcome-card">
        <x-flex align="center" justify="space-between">
          <x-flex align="center" :size="16">
            <Avatar
              :text="userStore.user.username"
              :src="userStore.user.avatar"
              :size="64"
            />
            <x-flex vertical :size="4">
              <el-text size="large" tag="b"
                >欢迎回来，{{ userStore.user.username }}</el-text
              >
              <el-text type="info"
                >今天是个美好的一天，开始您的工作吧！</el-text
              >
            </x-flex>
          </x-flex>
          <x-flex vertical :size="8" style="text-align: right">
            <el-text type="primary" size="large">{{ currentTime }}</el-text>
            <el-text type="info" size="large">{{ currentDate }}</el-text>
          </x-flex>
        </x-flex>
      </el-card>

      <!-- 统计卡片区域 -->
      <x-flex :size="16">
        <InfoCard
          color="#909399"
          :icon-component="People"
          message="总用户数"
          iconSize="50"
          :value="statsData.totalUsers"
          :loading="statsLoading"
        />
        <InfoCard
          color="#67C23A"
          :icon-component="ReorderFour"
          message="题目总数"
          iconSize="50"
          :value="statsData.totalProblems"
          :loading="statsLoading"
        />
        <InfoCard
          color="#409EFF"
          :icon-component="Trophy"
          message="比赛总数"
          iconSize="50"
          :value="statsData.totalContests"
          :loading="statsLoading"
        />
      </x-flex>

      <!-- 主要内容区域 -->
      <x-flex :size="16">
        <!-- 左侧内容 -->
        <x-flex vertical :size="16" style="flex: 2">
          <!-- 快速操作 -->
          <el-card>
            <template #header>
              <x-flex align="center" :size="8">
                <el-icon><Operation /></el-icon>
                <span>快速操作</span>
              </x-flex>
            </template>
            <x-flex :size="12" wrap>
              <el-button
                type="primary"
                :icon="Plus"
                @click="router.push('/admin/problem/add')"
              >
                添加题目
              </el-button>
              <el-button
                type="success"
                :icon="Plus"
                @click="router.push('/admin/contest/add')"
              >
                创建比赛
              </el-button>
              <el-button
                type="warning"
                :icon="User"
                @click="router.push('/admin/user/list')"
              >
                用户管理
              </el-button>
              <el-button
                type="info"
                :icon="Monitor"
                @click="router.push('/admin/monitor/server')"
              >
                系统监控
              </el-button>
              <el-button
                type="danger"
                :icon="Bell"
                @click="router.push('/admin/announcement/list')"
              >
                公告管理
              </el-button>
              <el-button
                type="primary"
                :icon="Edit"
                @click="router.push('/admin/problem/tag')"
              >
                标签管理
              </el-button>
            </x-flex>
          </el-card>

          <!-- 提交趋势图 -->
          <el-card>
            <template #header>
              <x-flex align="center" justify="space-between">
                <x-flex align="center" :size="8">
                  <el-icon><TrendCharts /></el-icon>
                  <span>提交趋势</span>
                </x-flex>
                <el-text type="info" size="small">最近2周</el-text>
              </x-flex>
            </template>
            <div v-loading="chartLoading">
              <SubmissionChart
                :dates="chartData.dates"
                :totalSubmissions="chartData.totalSubmissions"
                :acceptedSubmissions="chartData.acceptedSubmissions"
              />
            </div>
          </el-card>
        </x-flex>

        <!-- 右侧内容 -->
        <x-flex vertical :size="16" style="flex: 1">
          <!-- 登录信息 -->
          <el-card>
            <template #header>
              <x-flex align="center" :size="8">
                <el-icon><InfoFilled /></el-icon>
                <el-text tag="b">登录信息</el-text>
              </x-flex>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="用户名">
                <span>{{ userStore.user.username }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="角色">
                <span>{{ userStore.user.roles.join(',') }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="登录IP">
                <span>{{ userStore.user.ipaddr }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="浏览器">
                <span>{{ userStore.user.browser }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="登录地点">
                <span>{{ userStore.user.loginLocation }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="操作系统">
                <span>{{ userStore.user.os }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="登录时间">
                <span>{{ userStore.user.loginDateTime }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 最新公告 -->
          <el-card>
            <template #header>
              <x-flex align="center" justify="space-between">
                <x-flex align="center" :size="8">
                  <el-icon><Bell /></el-icon>
                  <el-text tag="b">最新公告</el-text>
                </x-flex>
                <el-button
                  type="primary"
                  text
                  size="small"
                  @click="router.push('/admin/announcement/list')"
                >
                  查看全部
                </el-button>
              </x-flex>
            </template>
            <div v-loading="announcementsLoading">
              <x-flex vertical :size="8">
                <div
                  v-for="announcement in latestAnnouncements"
                  :key="announcement.id"
                  class="announcement-item"
                >
                  <x-flex align="center" justify="space-between">
                    <x-flex align="center" :size="8">
                      <el-tag
                        v-if="announcement.pinned"
                        type="danger"
                        size="small"
                      >
                        置顶
                      </el-tag>
                      <el-text
                        class="announcement-title"
                        :title="announcement.title"
                      >
                        {{ announcement.title }}
                      </el-text>
                    </x-flex>
                    <el-text type="info" size="small">
                      {{ formatDate(announcement.updatedTime) }}
                    </el-text>
                  </x-flex>
                </div>
                <el-empty
                  v-if="latestAnnouncements.length === 0"
                  description="暂无公告"
                  :image-size="60"
                />
              </x-flex>
            </div>
          </el-card>
        </x-flex>
      </x-flex>
    </x-flex>
  </div>
</template>

<script lang="ts" setup>
//import
import { ref, onMounted, onUnmounted, defineAsyncComponent } from "vue";
import {
  User,
  Plus,
  Operation,
  Monitor,
  InfoFilled,
  Bell,
  Edit,
  TrendCharts,
} from "@element-plus/icons-vue";
import { People, ReorderFour, Trophy } from "@vicons/ionicons5";
import { useUserStore } from "@/stores/user";
import { useRouter } from "vue-router";
import type { Announcement } from "@/type/types";
import {
  getAdminAnnouncementList,
  getAdminDashboardStats,
  getAdminSubmissionChart,
} from "@/api/api";
const SubmissionChart = defineAsyncComponent(
  () => import("@/components/admin/SubmissionChart.vue")
);
const InfoCard = defineAsyncComponent(
  () => import("@/components/admin/InfoCard.vue")
);
const Avatar = defineAsyncComponent(
  () => import("@/components/common/Avatar.vue")
);
//data
const userStore = useUserStore();
const router = useRouter();
//时间相关
const currentTime = ref("");
const currentDate = ref("");
//统计数据
const statsData = ref({
  totalUsers: 0,
  totalProblems: 0,
  totalContests: 0,
});
const statsLoading = ref(false);
//折线图数据
const chartData = ref({
  dates: [],
  totalSubmissions: [],
  acceptedSubmissions: [],
});
const chartLoading = ref(false);
//最新公告
const latestAnnouncements = ref<Announcement[]>([]);
const announcementsLoading = ref(false);

//methods
onMounted(() => {
  updateTime();
  //初始化Dashboard数据
  initDashboard();
});
//更新时间
const updateTime = () => {
  const now = new Date();
  currentTime.value = now.toLocaleTimeString("zh-CN", {
    hour12: false,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  currentDate.value = now.toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "long",
  });
};
const timer = setInterval(updateTime, 1000);
onUnmounted(() => {
  clearInterval(timer);
});
//初始化数据
const initDashboard = () => {
  loadStatsData();
  loadChartData();
  loadLatestAnnouncements();
};
//获取统计数据
const loadStatsData = async () => {
  statsLoading.value = true;
  try {
    const res = await getAdminDashboardStats();
    statsData.value = {
      totalUsers: res.data.data.totalUsers || 0,
      totalProblems: res.data.data.totalProblems || 0,
      totalContests: res.data.data.totalContests || 0,
    };
  } catch (e) {
    console.log(e);
  } finally {
    statsLoading.value = false;
  }
};
//获取折线图数据
const loadChartData = async () => {
  chartLoading.value = true;
  try {
    const res = await getAdminSubmissionChart();
    chartData.value = {
      dates: res.data.data.dates || [],
      totalSubmissions: res.data.data.totalSubmissions || [],
      acceptedSubmissions: res.data.data.acceptedSubmissions || [],
    };
  } catch (e) {
    console.log(e);
  } finally {
    chartLoading.value = false;
  }
};
//获取最新公告
const loadLatestAnnouncements = async () => {
  announcementsLoading.value = true;
  try {
    const res = await getAdminAnnouncementList({
      pageNo: 1,
      pageSize: 5,
      status: true,
    });
    latestAnnouncements.value = res.data.data.records || [];
  } catch (e) {
    console.log(e);
    latestAnnouncements.value = [];
  } finally {
    announcementsLoading.value = false;
  }
};

//格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return "";
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (days === 0) return "今天";
  if (days === 1) return "昨天";
  if (days < 7) return `${days}天前`;
  return date.toLocaleDateString("zh-CN");
};
</script>

<style scoped lang="css">
.dashboard-container {
  padding: 24px;
}

.welcome-card :deep(.el-card__body) {
  padding: 24px;
}

.stat-card {
  flex: 1;
  min-width: 240px;
  transition: transform 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.announcement-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--ym-divider-soft);
}

.announcement-item:last-child {
  border-bottom: none;
}

.announcement-title {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.announcement-title:hover {
  color: var(--ym-link);
}


:deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--ym-divider-soft);
}

:deep(.el-card__body) {
  padding: 20px;
}

:deep(.el-descriptions__label) {
  font-weight: 500;
}

:deep(.el-button + .el-button) {
  margin-left: 8px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .dashboard-container {
    padding: 16px;
  }

  :deep(.x-flex[style*="flex: 2"]) {
    flex: 1 !important;
  }

  :deep(.x-flex[style*="flex: 1"]) {
    flex: 1 !important;
    margin-top: 16px;
  }
}
</style>
