<template>
  <div class="code-highlight">
    <el-button class="copy-button" type="primary" :icon="DocumentCopy" @click="onCopy">
      复制
    </el-button>

    <PlainTextViewer
      v-if="largeCode"
      class="large-code"
      empty-text=""
      :content="source"
    />

    <el-scrollbar v-else max-height="800px">
      <pre class="line-numbers">{{ lineNumbers }}</pre>
      <pre v-if="highlighted !== null"><code v-html="highlighted"></code></pre>
      <pre v-else><code>{{ source }}</code></pre>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue';
import { ElMessage } from 'element-plus';
import { DocumentCopy } from '@element-plus/icons-vue';
import useClipboard from 'vue-clipboard3';
import { highlightCode } from '@/utils/codeHighlight';

const PlainTextViewer = defineAsyncComponent(
  () => import('./PlainTextViewer.vue')
);

const { toClipboard } = useClipboard();

const props = withDefaults(
  defineProps<{
    code: string;
    language?: string;
  }>(),
  {
    language: 'C'
  }
);

// 保留源代码缩进和首尾空行，仅统一换行符
const source = computed(() => props.code.replace(/\r\n?/g, '\n'));

const lineCount = computed(
  () => (source.value.match(/\n/g)?.length ?? 0) + 1
);

// 超大源码使用虚拟查看器，避免大量 DOM 节点和同步高亮
const largeCode = computed(
  () => source.value.length > 100_000 || lineCount.value > 2_000
);

const lineNumbers = computed(() =>
  Array.from({ length: lineCount.value }, (_, index) => index + 1).join('\n')
);

const highlighted = computed(() => {
  if (largeCode.value) {
    return null;
  }
  return highlightCode(source.value, props.language);
});

const onCopy = async () => {
  try {
    await toClipboard(props.code);
    ElMessage.success('复制成功');
  } catch {
    ElMessage.warning('复制失败，请手动复制');
  }
};
</script>

<style scoped>
.code-highlight {
  position: relative;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
  color: var(--ym-code-text);
  background: var(--ym-code-surface);
}

.copy-button {
  position: absolute;
  top: 8px;
  right: 16px;
  z-index: 2;
  height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-light);
  opacity: 0;
  pointer-events: none;
  transform: translateY(-4px);
  transition: opacity 0.15s, transform 0.15s;
}

.code-highlight:hover .copy-button,
.code-highlight:focus-within .copy-button {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

@media (hover: none) {
  .copy-button {
    opacity: 1;
    pointer-events: auto;
    transform: translateY(0);
  }
}

.code-highlight :deep(.el-scrollbar__view) {
  display: flex;
  width: max-content;
  min-width: 100%;
  min-height: 52px;
}

pre {
  margin: 0;
  padding: 4px 16px;
  font-size: 16px;
  line-height: 26px !important;
  tab-size: 4;
  white-space: pre;
  font-family:
    Consolas,
    "SFMono-Regular",
    Menlo,
    "Liberation Mono",
    monospace;
}

code {
  font: inherit;
}

.line-numbers {
  position: sticky;
  left: 0;
  z-index: 1;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  background: var(--ym-code-gutter);
  text-align: right;
  user-select: none;
}

.large-code {
  height: min(600px, 70vh);
}

/* 沿用原组件的 Atom One Light 配色，仅作用于本组件的高亮结果。 */
:deep(.hljs-comment), :deep(.hljs-quote) {
  color: var(--ym-code-comment);
  font-style: italic;
}
:deep(.hljs-keyword), :deep(.hljs-doctag) {
  color: var(--ym-code-keyword);
}
:deep(.hljs-string), :deep(.hljs-regexp), :deep(.hljs-meta .hljs-string) {
  color: var(--ym-code-string);
}
:deep(.hljs-number), :deep(.hljs-type), :deep(.hljs-variable), :deep(.hljs-attr) {
  color: var(--ym-code-number);
}
:deep(.hljs-title), :deep(.hljs-meta), :deep(.hljs-symbol) {
  color: var(--ym-code-title);
}
:deep(.hljs-built_in), :deep(.hljs-title.class_) {
  color: var(--ym-code-type);
}
:deep(.hljs-literal) {
  color: var(--ym-code-literal);
}
:deep(.hljs-subst), :deep(.hljs-name) {
  color: var(--ym-code-name);
}
</style>
