<template>
  <article ref="articleRef" class="announcement-item">
    <header>
      <x-flex align="center" :size="8">
        <el-tag v-if="announcement.pinned" type="warning" effect="dark">置顶</el-tag>
        <h3>{{ announcement.title }}</h3>
      </x-flex>
      <x-flex class="announcement-meta" align="center" :size="12">
        <time>{{ announcement.updatedTime }}</time>
        <span>{{ announcement.authorUsername }}</span>
      </x-flex>
    </header>
    <div
      class="announcement-body"
      :class="{ 'is-collapsed': overflowing && !expanded }"
      @focusin="onContentFocus"
    >
      <div ref="contentRef">
        <MdPreview
          preview-theme="cyanosis"
          :id="`announcement-${announcement.id}`"
          :model-value="announcement.content"
          :sanitize="DOMPurify.sanitize"
        />
      </div>
    </div>
    <el-button
      v-if="overflowing"
      type="primary"
      link
      :aria-expanded="expanded"
      @click="onToggleExpanded"
    >{{ expanded ? '收起全文' : '展开全文' }}</el-button>
  </article>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { MdPreview } from 'md-editor-v3';
import DOMPurify from 'dompurify';
import type { Announcement } from '@/type/announcement';
import 'md-editor-v3/lib/preview.css';

defineProps<{ announcement: Announcement }>();

const articleRef = ref<HTMLElement>();
const contentRef = ref<HTMLElement>();
const expanded = ref(false);
const overflowing = ref(false);
let resizeObserver: ResizeObserver | undefined;

function onToggleExpanded() {
  expanded.value = !expanded.value;
  if (!expanded.value && articleRef.value && articleRef.value.getBoundingClientRect().top < 0) {
    articleRef.value.scrollIntoView({ block: 'start' });
  }
}

function onContentFocus() {
  // 键盘访问正文链接时展开，避免焦点落在被裁切的内容中。
  expanded.value = true;
}

onMounted(() => {
  // 测量真实排版，图片加载和窄屏换行后也重新判断，不按字数猜测高度。
  resizeObserver = new ResizeObserver(() => {
    const article = articleRef.value;
    const content = contentRef.value;
    if (article && content) {
      const previewHeight = Number.parseFloat(getComputedStyle(article).getPropertyValue('--preview-height'));
      overflowing.value = content.getBoundingClientRect().height > previewHeight + 1;
    }
  });
  if (contentRef.value) {
    resizeObserver.observe(contentRef.value);
  }
});

onBeforeUnmount(() => resizeObserver?.disconnect());
</script>

<style scoped>
.announcement-item {
  --preview-height: 220px;
  padding: 24px 0;
  scroll-margin-top: 80px;
}
.announcement-item + .announcement-item {
  border-top: 1px solid var(--el-border-color-lighter);
}
.announcement-item h3 {
  margin: 0;
  font-size: 19px;
  line-height: 1.5;
  font-weight: 600;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
}
.announcement-meta {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.announcement-body {
  position: relative;
  margin-top: 16px;
  overflow-wrap: anywhere;
}
.announcement-body.is-collapsed {
  max-height: var(--preview-height);
  overflow: hidden;
}
.announcement-body.is-collapsed::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 48px;
  content: '';
  pointer-events: none;
  background: linear-gradient(transparent, var(--el-bg-color));
}
.announcement-body :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
.announcement-body :deep(.md-editor-preview) {
  font-size: 15px;
  line-height: 1.85;
  color: var(--el-text-color-regular);
}
.announcement-item > .el-button {
  margin-top: 8px;
}
@media (max-width: 600px) {
  .announcement-item {
    --preview-height: 180px;
    padding: 20px 0;
  }
}
</style>
