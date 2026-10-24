<template>
  <div class="code-highlight">
    <PlainTextViewer
      v-if="largeCode"
      class="large-code"
      aria-label="源代码（大文本模式）"
      empty-text=""
      :content="source"
    />
    <el-scrollbar v-else max-height="600px" tabindex="0" role="region" aria-label="源代码">
      <pre class="line-numbers" aria-hidden="true">{{ lineNumbers }}</pre>
      <pre><code v-if="highlighted !== null" v-html="highlighted"></code><code v-else>{{ source }}</code></pre>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue';
import { highlightCode } from '@/utils/codeHighlight';

const PlainTextViewer = defineAsyncComponent(() => import('./PlainTextViewer.vue'));
const props = withDefaults(defineProps<{ code: string; language?: string }>(), {
  language: 'C'
});

// 仅统一换行，不 trim，保留缩进、首尾空行和源码内容。
const source = computed(() => props.code.replace(/\r\n?/g, '\n'));
const lineCount = computed(() => (source.value.match(/\n/g)?.length ?? 0) + 1);
// 超大源码使用现有虚拟查看器，避免同步高亮和成千上万的 token 节点。
const largeCode = computed(() => source.value.length > 100_000 || lineCount.value > 2_000);
const lineNumbers = computed(() => Array.from({ length: lineCount.value }, (_, index) => index + 1).join('\n'));
// v-html 仅接收高亮器转义后的 HTML；未知语言通过插值显示原文。
const highlighted = computed(() => largeCode.value ? null : highlightCode(source.value, props.language));
</script>

<style scoped>
.code-highlight {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
  color: #383a42;
  background: #fafafa;
}

.code-highlight :deep(.el-scrollbar__view) {
  display: flex;
  width: max-content;
  min-width: 100%;
}

pre {
  margin: 0;
  padding: 8px 12px;
  font: 14px / 1.7 Consolas, 'SFMono-Regular', 'Liberation Mono', monospace;
  tab-size: 4;
  white-space: pre;
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
  background: #f1f1f1;
  text-align: right;
  user-select: none;
}

.large-code {
  height: min(600px, 70vh);
}

/* 沿用原组件的 Atom One Light 配色，仅作用于本组件的高亮结果。 */
:deep(.hljs-comment), :deep(.hljs-quote) {
  color: #a0a1a7;
  font-style: italic;
}
:deep(.hljs-keyword), :deep(.hljs-doctag) {
  color: #a626a4;
}
:deep(.hljs-string), :deep(.hljs-regexp), :deep(.hljs-meta .hljs-string) {
  color: #50a14f;
}
:deep(.hljs-number), :deep(.hljs-type), :deep(.hljs-variable), :deep(.hljs-attr) {
  color: #986801;
}
:deep(.hljs-title), :deep(.hljs-meta), :deep(.hljs-symbol) {
  color: #4078f2;
}
:deep(.hljs-built_in), :deep(.hljs-title.class_) {
  color: #c18401;
}
:deep(.hljs-literal) {
  color: #0184bb;
}
:deep(.hljs-subst), :deep(.hljs-name) {
  color: #e45649;
}
</style>
