<template>
  <div class="container">
    <n-flex class="container-content" vertical>
      <!-- 头像部分 -->
      <n-flex justify="center" style="margin-top: 10px;">
        <Avatar
          :src="userForm.avatar || ''" 
          :text="userForm.username" 
          :size="150"
        />
      </n-flex>
      <!-- 用户信息部分 -->
      <n-card class="card-container">
        <!-- 最近登录时间 -->
        <n-flex justify="center" class="last-login-container">
          <el-popover placement="top" trigger="hover" :content="userForm.loginDate" :width="160">
            <template #reference>
              <el-tag type="success" class="last-login-time" effect="plain" round>
                <div style="display: flex; align-items: center;">
                  <span class="online-indicator"></span>
                  <span style="margin-left: 5px;">最近上线时间: {{ formatRelativeTime(userForm.loginDate) }}</span>
                </div>
              </el-tag>
            </template>
          </el-popover>
        </n-flex>
        <!-- 用户信息头部 -->
        <n-flex justify="center" align="center" style="display: flex;">
          <n-icon size="40" :component="PersonCircleOutline" />
          <span style="font-size: 30px;font-weight: bold;">{{ userForm.username }}</span>
          <n-icon v-if="userForm.sex === '男'" size="30" :component="Male" color="#409eff" />
          <n-icon v-if="userForm.sex === '女'" size="30" :component="Female" color="#F56C6C" />
        </n-flex>
        <!-- 学校信息 -->
        <n-flex v-if="userForm.school" justify="center" align="center" style="margin-top: 5px; font-size: 15px;">
          <n-icon size="25" :component="School" />{{ userForm.school }}
        </n-flex>
        <!-- 附属信息 -->
        <n-flex justify="center" align="center" style="margin-top: 10px;">
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
        </n-flex>
        <hr style="margin-top: 10px;">
        <!-- 已解决和总提交数 -->
        <n-flex style="display: flex; margin-top: 10px; text-align: center;">
          <n-card class="status-card" hoverable style="background-color: #67c23a;">
            <n-flex vertical align="center">
              <span>
                <el-icon size="20" color="white" style="vertical-align: middle;">
                  <SuccessFilled />
                </el-icon>
                已解决
              </span>
              <div style="font-size: 28px; font-weight: bold; margin-top: 5px;">{{ userForm.ac }}</div>
            </n-flex>
          </n-card>
          <n-card class="status-card" hoverable style="background-color: #87ceeb;">
            <n-flex vertical align="center">
              <span>
                <el-icon size="20" color="white" style="vertical-align: middle;">
                  <Grid />
                </el-icon>
                总提交数
              </span>
              <div style="font-size: 28px; font-weight: bold; margin-top: 5px;">{{ userForm.total }}</div>
            </n-flex>
          </n-card>
        </n-flex>
        <!-- 最近一年提交热力表 -->
        <n-card title="最近一年提交热力表" style="margin-top: 20px;">
          <CalendarHeatMap :values="heatmapData" :end-date="endDate" />
        </n-card>
        <!-- 个人简介和做题情况 -->
        <n-card style="margin-top: 20px;">
          <n-tabs type="card">
            <n-tab-pane name="1" tab="个人简介">
              <MarkdownPreview v-if="userForm.signature" :content="userForm.signature" />
            </n-tab-pane>
            <n-tab-pane name="2" tab="做题情况">
              <template v-if="userForm.problemId && userForm.problemId.length > 0">
                <n-flex justify="center" align="center" style="margin-top: 10px;">
                  <span>已解决问题列表</span>
                  <el-button type="primary" :icon="Refresh" @click="initUserInfo" circle />
                </n-flex>
                <n-flex justify="center" style="margin-top: 10px;">
                  <n-button color="#67c23a" v-for="problem in userForm.problemId" @click="goToProblem(problem)"
                    style="margin: 5px;">
                    {{ problem }}
                  </n-button>
                </n-flex>
              </template>
              <template v-else>
                这家伙太懒了，还没有做过题...
              </template>
            </n-tab-pane>
          </n-tabs>
        </n-card>
      </n-card>
    </n-flex>
  </div>
</template>

<script setup lang="ts">
//import
import { ref, watch, defineAsyncComponent } from 'vue';
const Avatar = defineAsyncComponent(() => import('@/components/common/Avatar.vue'));
const MarkdownPreview = defineAsyncComponent(() => import('@/components/common/MarkdownPreview.vue'));
const CalendarHeatMap = defineAsyncComponent(() => import('@/components/common/CalendarHeatMap.vue'));
import type { UserInfoVO } from '@/type/types';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { School, PersonCircleOutline, Female, Male, Refresh, AtCircle } from '@vicons/ionicons5';
import { getUserInfoByUsername, getUserCalendarHeatmap } from '@/api/api';
import { formatRelativeTime } from '@/utils/utils';

//data
const router = useRouter();
const route = useRoute();
const userForm = ref<UserInfoVO>({} as UserInfoVO);
const heatmapData = ref([]);
const endDate = ref(new Date().toString());

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
  width: 100%;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border: none;
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
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);

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