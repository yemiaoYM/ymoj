<template>
  <div class="container home-columns">
    <x-flex class="home-main" vertical :size="20">
      <el-card v-if="bannerList.length" :body-style="{ padding: '0' }">
        <el-carousel class="home-banner" trigger="click" arrow="hover" height="100%">
          <el-carousel-item v-for="item in bannerList" :key="item">
            <el-image class="banner-image" alt="YMOJ 首页轮播图" fit="cover" :src="item" />
          </el-carousel-item>
        </el-carousel>
      </el-card>
      <AnnouncementFeed />
    </x-flex>
    <x-flex class="home-side" vertical :size="20">
      <!-- 最近一周AC排名 -->
      <el-card>
        <template #header>
          <x-flex align="center" :size="16">
            <el-icon color="#ffc107" :size="24">
              <Coin />
            </el-icon>
            <h2 class="section-title">用户 AC 数量排名</h2>
            <el-tooltip content="排名在每日凌晨1点更新" placement="top">
              <el-icon aria-label="排名在每日凌晨1点更新" tabindex="0" :size="18">
                <WarningFilled />
              </el-icon>
            </el-tooltip>
          </x-flex>
        </template>
        <el-skeleton v-if="rankLoading" :rows="10" animated />
        <div v-else>
          <el-table
            v-if="userAcRank && userAcRank.length > 0"
            size="large"
            :data="userAcRank"
          >
            <el-table-column label="排名" align="center" width="64">
              <template #default="{ $index }">
                <el-icon
                  v-if="$index === 0"
                  color="#ffc107"
                  :size="24"
                >
                  <component :is="Ribbon" />
                </el-icon>
                <el-icon
                  v-else-if="$index === 1"
                  color="#c0c4cc"
                  :size="24"
                >
                  <component :is="Ribbon" />
                </el-icon>
                <el-icon
                  v-else-if="$index === 2"
                  color="#cd7f32"
                  :size="24"
                >
                  <component :is="Ribbon" />
                </el-icon>
                <span v-else>{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="username"
              label="用户名"
              min-width="110"
              align="left"
            >
              <template #default="{ row }">
                <x-flex align="center" :size="8" :wrap="false">
                  <Avatar
                    :src="row.avatar"
                    :text="row.username"
                    :size="32"
                  />
                  <el-link
                    class="user-link"
                    type="primary"
                    underline="never"
                    :href="router.resolve({ name: 'UserHome', query: { username: row.username } }).href"
                    :title="row.username"
                  >
                    {{ row.username }}
                  </el-link>
                  <el-tag
                    v-if="row.nickname"
                    class="user-nickname"
                    disable-transitions
                    effect="dark"
                    :title="row.nickname"
                  >
                    {{ row.nickname }}
                  </el-tag>
                </x-flex>
              </template>
            </el-table-column>
            <el-table-column prop="count" label="AC 数" align="center" width="84">
              <template #default="{ row }">
                <el-text tag="b">{{ row.count }}</el-text>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-else
            description="暂无排名，快去解题吧！"
            :image-size="80"
          />
        </div>
      </el-card>
      <!-- 近期比赛 -->
      <el-card>
        <template #header>
          <x-flex align="center" :size="16">
            <el-icon color="#52c41a" :size="24">
              <Calendar />
            </el-icon>
            <h2 class="section-title">近期比赛</h2>
          </x-flex>
        </template>
        <el-skeleton v-if="contestLoading" :rows="10" animated />
        <div v-else>
          <x-flex vertical :size="16">
            <el-card
              v-for="contest in competitions"
              :key="contest.id"
              class="contest-card"
              shadow="hover"
              :style="{
                borderLeft:
                  contest.status === '已结束'
                    ? '4px solid #aab4bf'
                    : '4px solid #25bb9b',
              }"
            >
              <!-- 比赛名称和进行状态 -->
              <x-flex justify="space-between" align="center" :size="8">
                <el-link
                  class="contest-title"
                  underline="never"
                  :href="router.resolve({ name: 'ContestDetail', params: { contestID: contest.id } }).href"
                >
                  {{ contest.title }}
                </el-link>
                <x-flex align="center" :size="8">
                  <el-tooltip
                    effect="light"
                    content="需要密码才能加入比赛"
                    placement="top"
                  >
                    <el-icon v-if="contest.visibility" aria-label="需要密码才能加入比赛" tabindex="0" :size="18">
                      <Lock />
                    </el-icon>
                  </el-tooltip>
                  <el-tag
                    effect="dark"
                    disable-transitions
                    :type="getContestStatusType(contest.status)"
                    round
                  >
                    {{ contest.status }}
                  </el-tag>
                </x-flex>
              </x-flex>

              <x-flex class="contest-tags" :size="8">
                <el-tag disable-transitions effect="dark" color="#ffc171">
                  {{ formatContestMode(contest.mode) }}
                </el-tag>
                <el-tag disable-transitions effect="dark" color="#409eff">
                  {{ formatContestStyle(contest.style) }}
                </el-tag>
                <el-tag disable-transitions effect="dark" color="#25bb9b">
                  {{ contest.sourceId === 0 ? "原创" : "重建赛" }}
                </el-tag>
                <el-tag v-if="contest.rated" disable-transitions effect="dark" color="#fe8053">
                  Rated
                </el-tag>
              </x-flex>
              <el-divider />
              <x-flex justify="space-between" align="center">
                <x-flex align="center" :size="6">
                  <el-icon><Clock /></el-icon>
                  <el-text size="small">{{ formatContestDuration(contest.duration) }}</el-text>
                </x-flex>
                <x-flex align="center" :size="6">
                  <el-icon><UserFilled /></el-icon>
                  <el-text size="small">{{ contest.participantCount }} 人</el-text>
                </x-flex>
              </x-flex>
            </el-card>
            <el-empty
              v-if="!competitions.length"
              description="暂无比赛"
              :image-size="80"
            />
          </x-flex>
        </div>
      </el-card>
    </x-flex>
  </div>
  <SiteFiling />
</template>

<script setup lang="ts">
//import
import { ref, onMounted, onBeforeUnmount, defineAsyncComponent } from "vue";
import { useRouter } from "vue-router";
import { Ribbon } from "@vicons/ionicons5";
import SiteFiling from "@/components/oj/SiteFiling.vue";
import AnnouncementFeed from "@/components/oj/AnnouncementFeed.vue";
import {
  getContestTableList,
  getUserAcRank,
} from "@/api/api";
import type {
  UserAcRankVO,
  ContestTableVO,
} from "@/type/types";
import {
  formatContestMode,
  formatContestStyle,
  formatContestDuration,
  getContestStatusType,
} from "@/utils/utils";
import { getPublicCarousel } from "@/api/carousel";
import banner from "@/assets/image/banner.webp";


const Avatar = defineAsyncComponent(
  () => import("@/components/common/Avatar.vue")
);

//data
const router = useRouter();

const bannerList = ref<string[]>([]);
let disposed = false;

const competitions = ref<ContestTableVO[]>([] as ContestTableVO[]);
const userAcRank = ref<UserAcRankVO[]>([] as UserAcRankVO[]);
//loading
const rankLoading = ref<boolean>(false);
const contestLoading = ref<boolean>(false);

//methods
onMounted(() => {
  handleLoadCarousel();
  //获取用户ac数量排名
  handleGetUserAcRank();
  //获取最近的3场比赛
  handleGetRencentContest();
});

async function handleLoadCarousel() {
  try {
    const response = await getPublicCarousel();
    if (!disposed) {
      const config = response.data.data;
      bannerList.value = config.configured ? config.images.map(image => image.url) : [banner];
    }
  } catch(e) {
    console.log(e);
  }
}

onBeforeUnmount(() => { disposed = true; });

//获取最近的3场比赛
const handleGetRencentContest = () => {
  contestLoading.value = true;
  getContestTableList(null, 1, 3)
    .then((res) => {
      competitions.value = res.data.data.records;
    })
    .catch((err) => {
      console.log(err);
    })
    .finally(() => {
      contestLoading.value = false;
    });
};
//获取用户ac数量排名
const handleGetUserAcRank = () => {
  rankLoading.value = true;
  getUserAcRank()
    .then((res) => {
      userAcRank.value = res.data.data;
    })
    .catch((err) => {
      console.log(err);
    })
    .finally(() => {
      rankLoading.value = false;
    });
};
</script>

<style scoped>
.container {
  width: 100%;
  max-width: 1520px;
  margin: 0 auto;
  padding: 24px 40px;
}

.home-banner {
  aspect-ratio: 16 / 9;
}
.banner-image {
  width: 100%;
  height: 100%;
  display: block;
  background: var(--ym-page);
}
.section-title {
  font-size: 20px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.home-columns {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.home-main {
  flex: 5;
  min-width: 0;
}
.home-side {
  flex: 3;
  min-width: 0;
}

.user-link {
  flex: 1;
  min-width: 0;
  justify-content: flex-start;
}
.user-link :deep(.el-link__inner) {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.user-nickname {
  max-width: 40%;
  flex-shrink: 0;
}

.user-nickname :deep(.el-tag__content) {
  line-height: normal;
  overflow: hidden;
  text-overflow: ellipsis;
}
.contest-title {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 16px;
  font-weight: 600;
}
.contest-card {
  border-radius: 8px;
}
.contest-tags {
  margin-top: 12px;
}
.contest-card :deep(.el-divider) {
  margin: 16px 0;
}

/* 小屏幕布局 */
@media (max-width: 1000px) {
  .home-columns {
    flex-direction: column;
  }
  .home-main,
  .home-side {
    width: 100%;
  }
  .container {
    padding: 16px;
  }
}
@media (max-width: 600px) {
  .container {
    padding: 12px;
  }
  .home-side :deep(.el-card__header),
  .home-side :deep(.el-card__body) {
    padding: 16px;
  }
}
</style>
