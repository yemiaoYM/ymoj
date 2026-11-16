<template>
  <div class="contest-page">
    <el-card class="contest-list">
      <template #header>
        <x-flex align="center" justify="space-between" :size="20">
          <h2>比赛列表</h2>
          <el-input
            class="contest-search"
            aria-label="搜索比赛"
            placeholder="搜索比赛名称"
            size="large"
            clearable
            v-model="keyword"
            :prefix-icon="Search"
            :disabled="loading"
            @keyup.enter="handleSearch"
          >
            <template #suffix>
              <el-button type="primary" :disabled="loading" @click="handleSearch">搜索</el-button>
            </template>
          </el-input>
        </x-flex>
      </template>

      <el-skeleton v-if="loading" :rows="12" animated />
      <template v-else>
        <el-empty v-if="loaded && !competitions.length" description="暂无相关比赛" />
        <article v-for="competition in competitions" :key="competition.id" class="contest-row">
          <x-flex align="flex-start" :wrap="false" :size="16">
            <el-image
              class="contest-image"
              fit="contain"
              lazy
              :alt="competition.title"
              :src="competition.image || (theme.isDark ? defaultContestDark : defaultContest)"
            />
            <x-flex class="contest-info" vertical :size="14">
              <x-flex align="center" justify="space-between" :size="12" :wrap="false">
                <RouterLink
                  class="contest-title"
                  :to="{ name: 'ContestDetail', params: { contestID: competition.id } }"
                >{{ competition.title }}</RouterLink>
                <el-button
                  class="contest-status"
                  round
                  :type="competition.status === '进行中' ? 'primary' : competition.status === '未开始' ? 'warning' : 'info'"
                  @click="router.push({ name: 'ContestDetail', params: { contestID: competition.id } })"
                >{{ competition.status }}</el-button>
              </x-flex>
              <x-flex align="center" :size="8">
                <el-tag size="large" disable-transitions type="warning" effect="dark">{{ formatContestMode(competition.mode) }}</el-tag>
                <el-tag size="large" disable-transitions type="primary" effect="dark">{{ formatContestStyle(competition.style) }}</el-tag>
                <el-tag size="large" disable-transitions type="success" effect="dark">{{ competition.sourceId === ORIGINAL_CONTEST_SOURCE ? '原创' : '重建赛' }}</el-tag>
                <el-tag v-if="competition.rated" size="large" disable-transitions type="danger" effect="dark">Rated</el-tag>
                <x-flex class="contest-access" align="center" :size="4" :wrap="false">
                  <el-icon v-if="competition.visibility !== CONTEST_VISIBILITY.PUBLIC"><Lock /></el-icon>
                  <span>{{ competition.visibility === CONTEST_VISIBILITY.PUBLIC ? '公开赛' : competition.visibility === CONTEST_VISIBILITY.PASSWORD ? '密码赛' : '私有赛' }}</span>
                </x-flex>
              </x-flex>
              <x-flex class="contest-time" align="center" :size="8">
                <el-icon><Clock /></el-icon>
                <time>{{ competition.startTime }}</time>
                <span>—</span>
                <time>{{ competition.endTime }}</time>
              </x-flex>
              <x-flex class="contest-meta" align="center" :size="[24, 8]">
                <x-flex align="center" :size="8" :wrap="false">
                  <el-icon><AlarmClock /></el-icon>
                  <span>时长 <strong>{{ formatContestDuration(competition.duration) }}</strong></span>
                </x-flex>
                <x-flex align="center" :size="8" :wrap="false">
                  <el-icon><CollectionTag /></el-icon>
                  <span>主办方 <strong>{{ competition.creatorUsername }}</strong></span>
                </x-flex>
                <x-flex v-if="competition.visibility !== CONTEST_VISIBILITY.PUBLIC" align="center" :size="8" :wrap="false">
                  <el-icon><User /></el-icon>
                  <span>已报名 <strong>{{ competition.participantCount }} 人</strong></span>
                </x-flex>
              </x-flex>
            </x-flex>
          </x-flex>
        </article>
      </template>

      <template v-if="total > 0" #footer>
        <x-flex justify="center">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[5, 10]"
            :pager-count="5"
            :total="total"
            :page-size="pageSize"
            :current-page="pageNo"
            :disabled="loading"
            @update:current-page="handleCurrentChange"
            @update:page-size="handleSizeChange"
          />
        </x-flex>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { CONTEST_VISIBILITY, ORIGINAL_CONTEST_SOURCE } from '@/constants/contest';
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Search, Clock, Lock, AlarmClock, CollectionTag, User } from '@element-plus/icons-vue';
import type { ContestTableVO } from '@/types/contest';
import { getContestTableList } from '@/api/contest';
import { formatContestMode, formatContestStyle, formatContestDuration } from '@/utils/utils';
import defaultContest from '@/assets/image/defaultContest.jpg';
import defaultContestDark from '@/assets/image/defaultContest-dark.webp';
import { useThemeStore } from '@/stores/theme';

const router = useRouter();
const theme = useThemeStore();
const competitions = ref<ContestTableVO[]>([]);
const loading = ref(false);
const loaded = ref(false);
const keyword = ref('');
const pageNo = ref(1);
const pageSize = ref(5);
const total = ref(0);
let confirmedKeyword = '';

onMounted(() => { void loadContests(); });

//查询成功后更新页码和列表，失败保留最近一次结果
const loadContests = async (nextPage = 1, nextSize = pageSize.value, nextKeyword = confirmedKeyword) => {
  if (loading.value) return;
  loading.value = true;
  try {
    const response = await getContestTableList(nextKeyword, nextPage, nextSize);
    competitions.value = response.data.data.records;
    total.value = response.data.data.total;
    pageNo.value = nextPage;
    pageSize.value = nextSize;
    confirmedKeyword = nextKeyword;
    loaded.value = true;
  } catch {
    //请求层统一提示失败，保留列表供用户重试
    return;
  } finally {
    loading.value = false;
  }
};
const handleSizeChange = (value: number) => {
  void loadContests(1, value);
};
const handleCurrentChange = (value: number) => {
  void loadContests(value);
};
const handleSearch = () => {
  void loadContests(1, pageSize.value, keyword.value.trim());
};
</script>

<style scoped>
.contest-page {
  width: 100%;
  padding: 16px 5%;
}

.contest-list {
  --el-card-padding: 24px;
}

.contest-list h2 {
  margin: 0;
  font-size: 20px;
  font-weight: bold;
  color: var(--el-text-color-primary);
}

.contest-search {
  width: min(440px, 100%);
  height: 48px;
  --el-border-radius-base: 10px;
  --el-input-border-color: var(--ym-search-border);
}

.contest-search :deep(.el-input__wrapper) {
  padding: 0 8px 0 16px;
}

.contest-search :deep(.el-button) {
  min-width: 68px;
  height: 34px;
  border-radius: 8px;
}

.contest-search :deep(.el-input__clear) {
  margin-right: 12px;
}

.contest-row {
  padding: 24px 0;
}

.contest-row:first-child {
  padding-top: 4px;
}

.contest-row:last-child {
  padding-bottom: 4px;
}

.contest-row + .contest-row {
  border-top: 1px solid var(--el-border-color-lighter);
}

.contest-image {
  width: 128px;
  height: 128px;
  flex-shrink: 0;
  border-radius: 8px;
}

.contest-info {
  flex: 1;
  min-width: 0;
}

.contest-title {
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.5;
  text-decoration: none;
  overflow-wrap: anywhere;
}

.contest-title:hover {
  color: var(--el-color-primary);
}

.contest-title:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 4px;
  border-radius: 2px;
}

.contest-status {
  flex-shrink: 0;
  width: 100px;
  height: 40px;
}

.contest-access,
.contest-meta {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.contest-access {
  margin-left: 4px;
}

.contest-time {
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.contest-meta .el-icon,
.contest-time .el-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.contest-meta strong {
  margin-left: 6px;
  font-weight: 400;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .contest-page {
    padding: 12px;
  }

  .contest-list {
    --el-card-padding: 16px;
  }

  .contest-search {
    width: 100%;
  }

  .contest-image {
    width: 72px;
    height: 72px;
  }

  .contest-title {
    font-size: 16px;
  }

  .contest-list :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
  }
}
</style>
