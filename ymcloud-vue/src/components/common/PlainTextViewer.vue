<!--
  PlainTextViewer：轻量级只读纯文本查看器。
  使用两个 <pre> 文本节点分别渲染行号和内容，不会为每一行创建 Vue 节点。

  最简用法：
  <PlainTextViewer :content="text" />

  属性用法：
  <PlainTextViewer
    :content="text"
    aria-label="输入文件内容"
    empty-text="文件内容为空"
    :show-line-numbers="true"
    :tab-size="4"
  />

  通过组件 ref 可调用：focus()、scrollToTop()、scrollToLine(line, options)。
-->
<template>
  <div
    ref="viewportRef"
    class="plain-text-viewer"
    :class="{ 'plain-text-viewer--without-line-numbers': !showLineNumbers }"
    :style="viewerStyle"
    role="region"
    :aria-label="ariaLabel"
    :aria-describedby="descriptionId"
    aria-readonly="true"
    tabindex="0"
    translate="no"
    spellcheck="false"
  >
    <span :id="descriptionId" class="plain-text-viewer__sr-only">
      {{ accessibleDescription }}
    </span>

    <div class="plain-text-viewer__row">
      <pre
        v-if="showLineNumbers"
        class="plain-text-viewer__line-numbers"
        :style="lineNumberStyle"
        aria-hidden="true"
      >{{ viewerMeta.lineNumbers }}</pre>
      <pre ref="contentRef" class="plain-text-viewer__content">{{ content }}</pre>
    </div>

    <span v-if="!content && emptyText" class="plain-text-viewer__empty" aria-hidden="true">
      {{ emptyText }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import type { CSSProperties } from 'vue';

interface PlainTextViewerProps {
  /** 要显示的纯文本内容。 */
  content?: string;
  /** 查看区域的无障碍名称。 */
  ariaLabel?: string;
  /** content 为空时的提示；传入空字符串可隐藏提示。 */
  emptyText?: string;
  /** 是否显示行号。 */
  showLineNumbers?: boolean;
  /** Tab 显示宽度，有效范围为 1-16。 */
  tabSize?: number;
}

interface ScrollToLineOptions {
  behavior?: ScrollBehavior;
  block?: 'start' | 'center' | 'end';
}

const props = withDefaults(defineProps<PlainTextViewerProps>(), {
  content: '',
  ariaLabel: '文本内容',
  emptyText: '文件内容为空',
  showLineNumbers: true,
  tabSize: 4
});

const viewportRef = ref<HTMLElement>();
const contentRef = ref<HTMLElement>();
const descriptionId = `plain-text-viewer-${useId()}`;

// 行号始终合并在一个文本节点中，避免长文本产生大量 DOM 节点。
const viewerMeta = computed(() => {
  let lineCount = 1;
  for (let index = 0; index < props.content.length; index++) {
    const character = props.content.charCodeAt(index);
    if (character === 10 || (character === 13 && props.content.charCodeAt(index + 1) !== 10)) {
      lineCount++;
    }
  }

  if (!props.showLineNumbers) {
    return { lineCount, lineNumbers: '' };
  }

  const numbers = new Array<string>(lineCount);
  for (let line = 1; line <= lineCount; line++) {
    numbers[line - 1] = String(line);
  }

  return {
    lineCount,
    lineNumbers: numbers.join('\n')
  };
});

const normalizedTabSize = computed(() => Number.isFinite(props.tabSize)
    ? Math.min(16, Math.max(1, Math.round(props.tabSize)))
    : 4);

const viewerStyle = computed<CSSProperties>(() => ({
  tabSize: String(normalizedTabSize.value)
}));

const lineNumberStyle = computed<CSSProperties>(() => ({
  width: `calc(${String(viewerMeta.value.lineCount).length}ch + 28px)`
}));

const accessibleDescription = computed(() => {
  const summary = `共 ${viewerMeta.value.lineCount} 行`;
  return props.content ? summary : `${summary}，${props.emptyText || '内容为空'}`;
});

const focus = (options?: FocusOptions) => viewportRef.value?.focus(options);

const scrollToTop = (behavior: ScrollBehavior = 'auto') => {
  viewportRef.value?.scrollTo({ top: 0, left: 0, behavior });
};

const scrollToLine = (line: number, options: ScrollToLineOptions = {}) => {
  const viewport = viewportRef.value;
  const content = contentRef.value;
  if (!viewport || !content || !Number.isFinite(line)) return;

  const targetLine = Math.min(viewerMeta.value.lineCount, Math.max(1, Math.trunc(line)));
  const computedStyle = window.getComputedStyle(content);
  const lineHeight = Number.parseFloat(computedStyle.lineHeight);
  const paddingTop = Number.parseFloat(computedStyle.paddingTop) || 0;
  if (!Number.isFinite(lineHeight)) return;

  const lineTop = paddingTop + (targetLine - 1) * lineHeight;
  const block = options.block || 'start';
  const offset = block === 'center'
    ? (viewport.clientHeight - lineHeight) / 2
    : block === 'end'
      ? viewport.clientHeight - lineHeight - paddingTop
      : paddingTop;

  viewport.scrollTo({
    top: Math.max(0, lineTop - offset),
    behavior: options.behavior || 'auto'
  });
};

defineExpose({ focus, scrollToTop, scrollToLine });
</script>

<style scoped>
.plain-text-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-bg-color, #ffffff);
  outline: none;
  scrollbar-color: var(--el-scrollbar-bg-color, #c0c4cc) transparent;
  scrollbar-gutter: stable;
  -webkit-overflow-scrolling: touch;
}

.plain-text-viewer:focus-visible {
  box-shadow: inset 0 0 0 2px var(--el-color-primary-light-5, #79bbff);
}

.plain-text-viewer__row {
  display: flex;
  width: max-content;
  min-width: 100%;
  min-height: 100%;
  align-items: stretch;
}

.plain-text-viewer__line-numbers,
.plain-text-viewer__content {
  box-sizing: border-box;
  margin: 0;
  font-family: Consolas, "SFMono-Regular", "Liberation Mono", "Courier New", monospace;
  font-size: 14px;
  font-variant-ligatures: none;
  line-height: 1.7;
  white-space: pre;
}

.plain-text-viewer__line-numbers {
  position: sticky;
  left: 0;
  z-index: 1;
  flex: 0 0 auto;
  min-width: 48px;
  padding: 12px 12px 12px 8px;
  border-right: 1px solid var(--el-border-color-lighter, #ebeef5);
  color: var(--el-text-color-secondary, #909399);
  background: var(--el-fill-color-light, #f5f7fa);
  font-variant-numeric: tabular-nums;
  text-align: right;
  user-select: none;
}

.plain-text-viewer__content {
  flex: 1 0 auto;
  min-width: 0;
  padding: 12px 16px;
}

.plain-text-viewer--without-line-numbers .plain-text-viewer__content {
  min-width: 100%;
}

.plain-text-viewer__empty {
  position: absolute;
  top: 50%;
  left: 50%;
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 13px;
  pointer-events: none;
  transform: translate(-50%, -50%);
  user-select: none;
}

.plain-text-viewer__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.plain-text-viewer__content::selection {
  color: inherit;
  background: var(--el-color-primary-light-7, #c6e2ff);
}

@media (max-width: 600px) {
  .plain-text-viewer__line-numbers,
  .plain-text-viewer__content {
    font-size: 13px;
  }

  .plain-text-viewer__line-numbers {
    padding-right: 10px;
    padding-left: 6px;
  }

  .plain-text-viewer__content {
    padding-right: 12px;
    padding-left: 12px;
  }
}

@media (forced-colors: active) {
  .plain-text-viewer:focus-visible {
    outline: 2px solid Highlight;
    outline-offset: -2px;
  }
}
</style>
