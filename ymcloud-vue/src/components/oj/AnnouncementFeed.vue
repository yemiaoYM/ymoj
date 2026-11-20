<template>
  <section ref="sectionRef" class="announcement-section" aria-labelledby="announcement-heading" :aria-busy="loading">
    <el-card shadow="always">
      <template #header>
        <x-flex align="center" :size="16">
          <el-icon color="#ffc107" :size="24"><Bell /></el-icon>
          <h2 id="announcement-heading" class="section-title">公告</h2>
        </x-flex>
      </template>
      <!-- 骨架屏和错误提示-->
      <el-skeleton v-if="loading && !loaded" animated :rows="6" />
      <el-alert
        v-if="failed"
        class="load-error"
        title="公告加载失败"
        type="warning"
        show-icon
        :closable="false"
      >
        <p>{{ loaded ? '当前仍为上次加载的内容，请重试。' : '暂时无法获取公告，请稍后重试。' }}</p>
        <el-button type="primary" link :disabled="loading" @click="onRetry">重新加载</el-button>
      </el-alert>

      <div v-loading="loading && loaded">
        <AnnouncementItem
          v-for="announcement in announcements"
          :key="announcement.id"
          :announcement="announcement"
        />
        <el-empty v-if="loaded && !failed && !announcements.length" description="暂无公告" :image-size="80" />
      </div>

      <template v-if="total > pageSize" #footer>
        <x-flex justify="center">
          <el-pagination
            layout="prev, pager, next"
            size="small"
            background
            aria-label="公告分页"
            :current-page="pageNo"
            :page-size="pageSize"
            :total="total"
            :pager-count="5"
            :disabled="loading"
            @current-change="onPageChange"
          />
        </x-flex>
      </template>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { Bell } from '@element-plus/icons-vue';
import { getAnnouncementTableList } from '@/api/announcement';
import type { PublicAnnouncement } from '@/types/announcement';
import AnnouncementItem from './AnnouncementItem.vue';

const pageSize = 5;
const sectionRef = ref<HTMLElement>();
const announcements = ref<PublicAnnouncement[]>([]);
const pageNo = ref(1);
const total = ref(0);
const loading = ref(false);
const loaded = ref(false);
const failed = ref(false);
let requestedPage = 1;
let disposed = false;

async function handleLoadAnnouncements(targetPage: number, scroll = false) {
  requestedPage = targetPage;
  loading.value = true;
  failed.value = false;
  try {
    const response = await getAnnouncementTableList({ pageNo: targetPage });
    if (disposed) {
      return;
    }
    const page = response.data.data;
    const lastPage = Math.max(1, page.pages);
    if (targetPage > lastPage) {
      // 管理员删除或下架后末页可能消失，重新获取最后一页。
      await handleLoadAnnouncements(lastPage, scroll);
      return;
    }
    announcements.value = page.records;
    total.value = page.total;
    pageNo.value = targetPage;
    loaded.value = true;
    if (scroll) {
      sectionRef.value?.scrollIntoView({ block: 'start' });
    }
  } catch {
    if (!disposed) {
      failed.value = true;
    }
  } finally {
    loading.value = false;
  }
}

function onPageChange(page: number) {
  if (page !== pageNo.value) {
    handleLoadAnnouncements(page, true);
  }
}

function onRetry() {
  handleLoadAnnouncements(requestedPage, loaded.value);
}

onMounted(() => handleLoadAnnouncements(1));
onBeforeUnmount(() => {
  disposed = true;
});
</script>

<style scoped>
.announcement-section {
  min-width: 0;
  scroll-margin-top: 80px;
}
.section-title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.announcement-section :deep(.el-card__body) {
  padding: 0 20px;
}
.announcement-section :deep(.el-skeleton) {
  padding: 24px 0;
}
.load-error {
  margin: 20px 0;
}
@media (max-width: 600px) {
  .announcement-section :deep(.el-card__header) {
    padding: 16px;
  }
  .announcement-section :deep(.el-card__body) {
    padding: 0 16px;
  }
}
</style>
