<template>
  <div class="dashboard-container">
    <n-flex vertical :size="24">
      <!-- 欢迎横幅 -->
      <el-card class="welcome-card">
        <n-flex align="center" justify="space-between">
          <n-flex align="center" :size="16">
            <Avatar :text="userStore.user.username" :src="userStore.user.avatar" :size="64" />
            <n-flex vertical :size="4">
              <el-text size="large" tag="b">欢迎回来，{{ userStore.user.username }}</el-text>
              <el-text type="info">今天是个美好的一天，开始您的工作吧！</el-text>
            </n-flex>
          </n-flex>
          <n-flex vertical :size="8" style="text-align: right;">
            <el-text type="primary" size="large">{{ currentTime }}</el-text>
            <el-text type="info" size="large">{{ currentDate }}</el-text>
          </n-flex>
        </n-flex>
      </el-card>

      <!-- 统计卡片区域 -->
      <n-flex :size="16">
        <InfoCard
            color="#909399"
              :icon-component="People"
            message="总用户数"
            iconSize="50"
            :value="1"
          ></InfoCard>
          <InfoCard
            color="#67C23A"
            :icon-component="ReorderFour"
            message="今日总题数"
            iconSize="50"
            :value="1"
          ></InfoCard>
          <InfoCard
            color="#409EFF"
            :icon-component="Trophy"
            message="最近两周比赛"
            iconSize="50"
            :value="1"
          ></InfoCard>
      </n-flex>

      <!-- 主要内容区域 -->
      <n-flex :size="16">
        <!-- 左侧内容 -->
        <n-flex vertical :size="16" style="flex: 2;">
          <!-- 快速操作 -->
          <el-card>
            <template #header>
              <n-flex align="center" :size="8">
                <el-icon><Operation /></el-icon>
                <span>快速操作</span>
              </n-flex>
            </template>
            <n-flex :size="12">
              <el-button type="primary" :icon="Plus" @click="router.push('/admin/problem/add')">
                添加题目
              </el-button>
              <el-button type="success" :icon="Plus" @click="router.push('/admin/contest/add')">
                创建比赛
              </el-button>
              <el-button type="warning" :icon="User" @click="router.push('/admin/user/list')">
                用户管理
              </el-button>
              <el-button type="info" :icon="Monitor" @click="router.push('/admin/monitor/server')">
                系统监控
              </el-button>
            </n-flex>
          </el-card>

          <!-- 最近活动 -->
          <el-card>
            <template #header>
              <n-flex align="center" :size="8">
                <el-icon><Clock /></el-icon>
                <span>最近活动</span>
              </n-flex>
            </template>
            <el-timeline>
              <el-timeline-item timestamp="2024-01-15 10:30" type="primary">
                <el-text>用户 "张三" 提交了题目 "两数之和" 的解答</el-text>
              </el-timeline-item>
              <el-timeline-item timestamp="2024-01-15 09:45" type="success">
                <el-text>比赛 "新年编程挑战赛" 创建成功</el-text>
              </el-timeline-item>
              <el-timeline-item timestamp="2024-01-15 08:20" type="warning">
                <el-text>系统进行了例行维护更新</el-text>
              </el-timeline-item>
              <el-timeline-item timestamp="2024-01-14 16:30" type="info">
                <el-text>新增题目 "动态规划入门" 已发布</el-text>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </n-flex>

        <!-- 右侧内容 -->
        <n-flex vertical :size="16" style="flex: 1;">
          <!-- 登录信息 -->
          <el-card>
            <template #header>
              <n-flex align="center" :size="8">
                <el-icon><InfoFilled /></el-icon>
                <el-text tag="b">登录信息</el-text>
              </n-flex>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="登录IP">
                <el-tag type="info">127.0.0.1</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="操作系统">
                <el-tag>Windows 10</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="浏览器">
                <el-tag type="success">Chrome 139.0.0</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="登录时间">
                <el-tag type="warning">{{ loginTime }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 通知公告 -->
          <el-card>
            <template #header>
              <n-flex align="center" :size="8">
                <el-icon><Bell /></el-icon>
                <el-text tag="b">通知公告</el-text>
              </n-flex>
            </template>
            <n-flex vertical :size="8">
              <el-alert title="系统维护通知" type="info" :closable="false" show-icon>
                <template #default>
                  系统将于本周日凌晨2:00-4:00进行例行维护
                </template>
              </el-alert>
              <el-alert title="新功能上线" type="success" :closable="false" show-icon>
                <template #default>
                  题目标签管理功能已上线，欢迎体验
                </template>
              </el-alert>
            </n-flex>
          </el-card>
        </n-flex>
      </n-flex>
    </n-flex>
  </div>
</template>

<script lang="ts" setup>
//import
import { ref, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { 
  User, Plus, Operation, Clock, 
  Monitor, InfoFilled, Bell
} from '@element-plus/icons-vue';
import { People,ReorderFour,Trophy } from '@vicons/ionicons5';
import { useUserStore } from '@/stores/user';
import { useRouter } from 'vue-router';

const InfoCard = defineAsyncComponent(() => import('@/components/admin/InfoCard.vue'));
const Avatar = defineAsyncComponent(() => import('@/components/common/Avatar.vue'));

//data
const userStore = useUserStore();
const router = useRouter();

//时间相关
const currentTime = ref('');
const currentDate = ref('');
const loginTime = ref('2024-01-15 08:30:25');

//更新时间
const updateTime = () => {
  const now = new Date();
  currentTime.value = now.toLocaleTimeString('zh-CN', { 
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
  currentDate.value = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  });
};

//methods
// 快速操作
const quickAction = (action: string) => {
  switch (action) {
    case 'add-problem':
      router.push('/admin/problem/add');
      break;
    case 'add-contest':
      router.push('/admin/contest/add');
      break;
    case 'user-manage':
      router.push('/admin/user/list');
      break;
    case 'system-monitor':
      router.push('/admin/monitor/server');
      break;
  }
};

// 生命周期
onMounted(() => {
  updateTime();
  const timer = setInterval(updateTime, 1000);
  
  onUnmounted(() => {
    clearInterval(timer);
  });
});
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
</style>
