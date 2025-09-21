<template>
  <div class="container">
    <!-- 左半部分 -->
    <n-flex vertical style="flex: 5;" :size="20">
      <!-- 欢迎使用YMOJ -->
      <el-card style="text-align: center;">
        <n-image :src="banner" lazy preview-disabled style="border-radius: 8px; display: block;object-fit: cover;" />
      </el-card>
      <!-- 最近一周AC排名 -->
      <el-card>
        <template #header>
          <n-flex align="center">
            <el-icon :size="24" color="#ffc107">
              <Coin />
            </el-icon>
            <span style="font-size: 20px; font-weight: 500; margin-left: 8px;">用户ac数量排名</span>
            <el-tooltip content="排名在每日凌晨1点更新" placement="top">
              <el-icon size="20" style="margin-left: 10px;">
                <WarningFilled />
              </el-icon>
            </el-tooltip>
          </n-flex>
        </template>
        <el-skeleton v-if="rankLoading" :rows="10" animated />
        <div v-else>
          <el-table v-if="userAcRank && userAcRank.length > 0" :data="userAcRank" size="large"
            :header-row-style="{ fontSize: '15px' }" style="width: 100%">
            <el-table-column label="Rank" align="center" width="100">
              <template #default="{ $index }">
                <n-icon v-if="$index === 0" :size="24" color="#ffc107" :component="Ribbon" />
                <n-icon v-else-if="$index === 1" :size="24" color="#c0c4cc" :component="Ribbon" />
                <n-icon v-else-if="$index === 2" :size="24" color="#cd7f32" :component="Ribbon" />
                <span v-else>{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户名" min-width="120" align="center">
              <template #default="{ row }">
                <n-flex align="center" justify="center">
                  <Avatar 
                    :src="row.avatar" 
                    :text="row.username" 
                    :size="35" 
                    style="margin-right: 5px;" 
                  />
                  <span @click="toUserInfo(row.username)"
                    style="cursor: pointer;font-weight: bold;color: #409eff;font-size: 15px;">
                    {{ row.username }}
                  </span>
                  <el-tag v-if="row.nickname" disable-transitions effect="dark" style="margin-left: 10px;" color="#21c1c6">
                    <span style="font-size: 13px;">{{ row.nickname }}</span>
                  </el-tag>
                </n-flex>
              </template>
            </el-table-column>
            <el-table-column prop="count" label="AC题目数量" align="center">
              <template #default="{ row }">
                <span style="font-weight: bold;">{{ row.count }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无排名，快去解题吧！" style="padding: 40px 0;" />
        </div>
      </el-card>
      <!-- 公告 -->
      <el-card>
        <template #header>
          <n-flex align="center">
            <el-icon :size="24" color="#ffc107">
              <Bell />
            </el-icon>
            <span style="font-size: 20px; font-weight: 500; margin-left: 8px;">公告</span>
          </n-flex>
        </template>
        <el-skeleton v-if="announcementLoading" :rows="10" animated />
        <div v-else>
          <el-timeline style="width: 100%;">
            <el-timeline-item v-for="announcement in announcements" :key="announcement.title"
              :timestamp="announcement.publishTime" placement="top">
              <el-card>
                <n-flex align="center">
                  <h2>{{ announcement.title }}</h2>
                  <el-icon v-if="announcement.isPinned" size="20" color="#ffc107">
                    <StarFilled />
                  </el-icon>
                </n-flex>
                <div style="margin-top: 10px;border: 1px solid #c6e2ff;padding: 10px;">
                  <MarkdownPreview :content="announcement.content"></MarkdownPreview>
                </div>
                <div style="margin-top: 15px;">
                  by <el-tag disable-transitions color="#409eff" style="color: white;">{{ announcement.authorUsername
                    }}</el-tag>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!announcements.length" description="暂无公告" />
        </div>
      </el-card>
    </n-flex>
    <!-- 右半部分 -->
    <n-flex vertical style="flex: 3;" :size="20">
      <!-- 支持的远程评测平台 -->
      <el-card>
        <template #header>
          <n-flex align="center">
            <el-icon :size="24" color="#2080f0">
              <Monitor />
            </el-icon>
            <span style="font-size: 20px; font-weight: 500; margin-left: 8px;">远程评测</span>
            <el-tooltip content="将在下几个版本支持,尽请期待" placement="top">
              <el-icon size="20" style="margin-left: 10px;">
                <WarningFilled />
              </el-icon>
            </el-tooltip>
          </n-flex>
        </template>
        <n-flex :size="25">
          <n-flex v-for="platform in platforms" :key="platform.name" align="center" justify="center">
            <n-image lazy :src="platform.logo" style="width: 32px; height: 32px; border-radius: 5px;"
              preview-disabled />
            <span>{{ platform.name }}</span>
          </n-flex>
        </n-flex>
      </el-card>
      <!-- 近期比赛 -->
      <el-card>
        <template #header>
          <n-flex align="center">
            <el-icon :size="24" color="#52c41a">
              <Calendar />
            </el-icon>
            <span style="font-size: 20px; font-weight: 500; margin-left: 8px;">近期比赛</span>
          </n-flex>
        </template>
        <el-skeleton v-if="contestLoading" :rows="10" animated />
        <div v-else>
          <n-flex vertical :size="16">
            <el-card shadow="hover" v-for="contest in competitions" :key="contest.id" size="small" :style="{
              borderLeft: contest.status === '已结束' ? '4px solid #aab4bf' : '4px solid #25bb9b',
              borderRadius: '8px',
            }">
              <!-- 比赛名称和进行状态 -->
              <n-flex justify="space-between" align="center">
                <span style="font-weight: 600;cursor: pointer;" @click="toContestDetail(contest.id)">{{ contest.title
                  }}</span>
                <el-tooltip effect="light" content="需要密码才能加入比赛" placement="top">
                  <el-icon v-show="contest.visibility" size="20">
                    <Lock />
                  </el-icon>
                </el-tooltip>
                <el-tag effect="dark" size="large" disable-transitions :type="getContestStatusType(contest.status)"
                  round>
                  {{ contest.status }}
                </el-tag>
              </n-flex>

              <n-flex style="margin-top: 12px;">
                <el-tag disable-transitions color="#ffc171" style="color: white;">
                  <span style="display: flex;align-items: center;">
                    <el-icon>
                      <Trophy />
                    </el-icon>{{ formatContestMode(contest.mode) }}
                  </span>
                </el-tag>
                <el-tag disable-transitions color="#409eff"
                  style="color: white;">{{ formatContestStyle(contest.style) }}</el-tag>
                <el-tag disable-transitions color="#25bb9b" style="color: white;">
                  {{ contest.sourceId === 0 ? '原创' : '重建赛' }}
                </el-tag>
                <el-tag v-show="contest.rated" disable-transitions color="#fe8053" style="color: white;">
                  <span style="display: flex;align-items: center;">
                    <el-icon style="margin-right: 2px;">
                      <Flag />
                    </el-icon>Rated</span>
                </el-tag>
              </n-flex>
              <el-divider />
              <n-flex justify="space-between" align="center">
                <span style="display: flex;align-items: center;">
                  <el-icon style="margin-right: 5px;">
                    <Clock />
                  </el-icon>{{ formatContestDuration(contest.duration) }}</span>
                <span style="display: flex;align-items: center;">
                  <el-icon style="margin-right: 5px;">
                    <UserFilled />
                  </el-icon>× {{ contest.participantCount }}</span>
              </n-flex>
            </el-card>
            <el-empty v-if="!competitions.length" description="暂无比赛" style="padding: 20px 0;" />
          </n-flex>
        </div>
      </el-card>
    </n-flex>
  </div>
  <!-- 底部 -->
  <footer>
    <div class="mundb-footer">
      <a href="https://beian.mps.gov.cn/#/query/webSearch?code=" rel="noreferrer" target="_blank" style="margin-right:10px">
        <img src="https://beian.mps.gov.cn/img/logo01.dd7ff50e.png" alt="公安备案"
              style="vertical-align: middle; width: 20px;">
        公安备案中...
      </a>
      <a href="https://beian.miit.gov.cn" rel="noreferrer" target="_blank">冀ICP备2024086612号</a>
    </div>
  </footer>
</template>

<script setup lang="ts">
//import
import { ref, onMounted, defineAsyncComponent } from 'vue';
import { useRouter } from 'vue-router';
import { Ribbon } from '@vicons/ionicons5';
import { getContestTableList, getUserAcRank, getAnnouncementTableList } from '@/api/api';
import type { UserAcRankVO, ContestTableVO, AnnouncementTableVO } from '@/type/types';
import { formatContestMode, formatContestStyle, formatContestDuration, getContestStatusType } from '@/utils/utils';
import luoguLogo from '@/assets/image/luogu-logo.png';
import atcoderLogo from '@/assets/image/atcoder-logo.png';
import banner from '@/assets/image/banner.jpg';
const Avatar = defineAsyncComponent(() => import('@/components/common/Avatar.vue'));
const MarkdownPreview = defineAsyncComponent(() => import('@/components/common/MarkdownPreview.vue'));

//data
const router = useRouter();
const platforms = ref([
  { name: '洛谷', logo: luoguLogo },
  { name: 'AtCoder', logo: atcoderLogo },
]);
const competitions = ref<ContestTableVO[]>([] as ContestTableVO[]);
const announcements = ref<AnnouncementTableVO[]>([] as AnnouncementTableVO[]);
const userAcRank = ref<UserAcRankVO[]>([] as UserAcRankVO[]);
//loading
const rankLoading = ref<boolean>(false);
const contestLoading = ref<boolean>(false);
const announcementLoading = ref<boolean>(false);

//methods
onMounted(() => {
  //获取用户ac数量排名
  handleGetUserAcRank();
  //获取最近的3场比赛
  handleGetRencentContest();
  //获取公告
  handleGetAnnouncements();
})

//获取公告
const handleGetAnnouncements = () => {
  announcementLoading.value = true;
  getAnnouncementTableList().then(res => {
    announcements.value = res.data.data;
  }).catch(err => {
    console.log(err);
  }).finally(() => {
    announcementLoading.value = false;
  })
}
//获取最近的3场比赛
const handleGetRencentContest = () => {
  contestLoading.value = true;
  getContestTableList(null, 1, 3).then(res => {
    competitions.value = res.data.data.records;
  }).catch(err => {
    console.log(err);
  }).finally(() => {
    contestLoading.value = false;
  })
}
//获取用户ac数量排名
const handleGetUserAcRank = () => {
  rankLoading.value = true;
  getUserAcRank().then(res => {
    userAcRank.value = res.data.data;
  }).catch(err => {
    console.log(err);
  }).finally(() => {
    rankLoading.value = false;
  })
}
//跳转到比赛详情
const toContestDetail = (contestID: number) => {
  router.push({ name: 'ContestDetail', params: { contestID: contestID } })
}
//跳转到用户主页
const toUserInfo = (username: string) => {
  router.push({ name: 'UserHome', query: { username: username } })
}
</script>

<style scoped>
.container {
  width: 100%;
  min-height: 100%;
  padding: 20px 40px;
  display: flex;
  gap: 20px;
}

footer {
  margin-top: 2rem;
  color: #555 !important;
  background-color: #fff;
  text-align: center;
}

footer a {
  color: #555;
  text-decoration: none;
}

footer a:hover {
  color: #409eff;
}

footer h1 {
  font-family: -apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC,
    Hiragino Sans GB, Microsoft YaHei, Helvetica Neue, Helvetica, Arial,
    sans-serif, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol;
  font-weight: 300;
  color: #3d3d3d;
  line-height: 1.1;
  font-size: 1.5rem;
}

.mundb-footer {
  padding: 1.5rem 2.5rem;
  width: 100%;
  font-weight: 400;
  font-size: 1rem;
  line-height: 1;
}

/* 小屏幕布局 */
@media (max-width: 1000px) {
  .container {
    flex-direction: column;
    /* 垂直排列 */
    padding: 16px;
  }
}
</style>