<!--
  轻量级大文本查看器：只渲染可视区域附近的正文和行号，不随文件总行数增加 DOM。

  <PlainTextViewer :content="text" />
  <PlainTextViewer :content="text" aria-label="输入文件" :tab-size="4" />

  组件 ref 可调用 focus()、scrollToTop()、scrollToLine(line, options)。
-->
<template>
  <div ref="rootRef" class="plain-text-viewer">
    <aside
      v-if="showLineNumbers"
      class="plain-text-viewer__gutter"
      :style="gutterStyle"
      aria-hidden="true"
    >
      <pre class="plain-text-viewer__line-numbers" :style="lineNumbersStyle">{{ visibleLineNumbers }}</pre>
    </aside>

    <div
      ref="scrollerRef"
      class="plain-text-viewer__scroller"
      role="region"
      :aria-label="ariaLabel"
      :aria-describedby="descriptionId"
      tabindex="0"
      @scroll="handleScroll"
    >
      <div class="plain-text-viewer__spacer" :style="spacerStyle" aria-hidden="true" />
      <pre class="plain-text-viewer__content" :style="contentStyle">{{ visibleContent }}</pre>
    </div>

    <span v-if="!content && emptyText" class="plain-text-viewer__empty" aria-hidden="true">
      {{ emptyText }}
    </span>
    <span ref="measureRef" class="plain-text-viewer__measure" aria-hidden="true">0000000000</span>
    <span :id="descriptionId" class="plain-text-viewer__sr-only">{{ accessibleDescription }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
import type { CSSProperties } from 'vue';

interface PlainTextViewerProps {
  content?: string;
  ariaLabel?: string;
  emptyText?: string;
  showLineNumbers?: boolean;
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

const PADDING_X = 16;
const PADDING_Y = 12;
const OVERSCAN_LINES = 5;
const OVERSCAN_COLUMNS = 30;
const LONG_LINE_THRESHOLD = 10_000;

const rootRef = ref<HTMLElement>();
const scrollerRef = ref<HTMLElement>();
const measureRef = ref<HTMLElement>();
const scrollTop = ref(0);
const scrollLeft = ref(0);
const viewportWidth = ref(0);
const viewportHeight = ref(0);
const lineHeight = ref(23.8);
const characterWidth = ref(8.4);
const descriptionId = `plain-text-viewer-${useId()}`;
let resizeObserver: ResizeObserver | undefined;
let scrollFrame: number | undefined;

// 仅保存每行起点，不拆分或复制整份正文。
const textModel = computed(() => {
  const lineStarts = [0];
  let columns = 0;
  let maxColumns = 0;
  let canVirtualizeColumns = true;

  for (let index = 0; index < props.content.length; index++) {
    const code = props.content.charCodeAt(index);
    if (code === 10 || code === 13) {
      maxColumns = Math.max(maxColumns, columns);
      columns = 0;
      if (code === 13 && props.content.charCodeAt(index + 1) === 10) index++;
      lineStarts.push(index + 1);
      continue;
    }

    if (code === 9) {
      const tabSize = normalizedTabSize.value;
      columns += tabSize - columns % tabSize;
      canVirtualizeColumns = false;
    } else {
      columns += code > 127 ? 2 : 1;
      if (code > 127) canVirtualizeColumns = false;
    }
  }

  return {
    lineStarts,
    lineCount: lineStarts.length,
    maxColumns: Math.max(maxColumns, columns),
    canVirtualizeColumns
  };
});

const normalizedTabSize = computed(() => Number.isFinite(props.tabSize)
  ? Math.min(16, Math.max(1, Math.round(props.tabSize)))
  : 4);

const visibleLines = computed(() => {
  const first = Math.floor(Math.max(0, scrollTop.value - PADDING_Y) / lineHeight.value);
  const start = Math.max(0, first - OVERSCAN_LINES);
  const count = Math.ceil(viewportHeight.value / lineHeight.value) + OVERSCAN_LINES * 2;
  return { start, end: Math.min(textModel.value.lineCount, start + Math.max(1, count)) };
});

const visibleColumns = computed(() => {
  if (!textModel.value.canVirtualizeColumns || textModel.value.maxColumns < LONG_LINE_THRESHOLD) {
    return { start: 0, end: Number.POSITIVE_INFINITY };
  }
  const first = Math.floor(Math.max(0, scrollLeft.value - PADDING_X) / characterWidth.value);
  const start = Math.max(0, first - OVERSCAN_COLUMNS);
  const count = Math.ceil(viewportWidth.value / characterWidth.value) + OVERSCAN_COLUMNS * 2;
  return { start, end: start + count };
});

const visibleContent = computed(() => {
  const { lineStarts } = textModel.value;
  const { start, end } = visibleLines.value;
  const columns = visibleColumns.value;

  if (columns.start === 0 && columns.end === Number.POSITIVE_INFINITY) {
    const startOffset = lineStarts[start];
    const endOffset = end < lineStarts.length ? lineStarts[end] : props.content.length;
    return props.content.slice(startOffset, endOffset);
  }

  const lines = new Array<string>(end - start);
  for (let line = start; line < end; line++) {
    const lineStart = lineStarts[line];
    const nextLineStart = line + 1 < lineStarts.length ? lineStarts[line + 1] : props.content.length;
    let lineEnd = nextLineStart;
    while (lineEnd > lineStart && (props.content.charCodeAt(lineEnd - 1) === 10 || props.content.charCodeAt(lineEnd - 1) === 13)) {
      lineEnd--;
    }
    lines[line - start] = props.content.slice(lineStart + columns.start, Math.min(lineEnd, lineStart + columns.end));
  }
  return lines.join('\n');
});

const visibleLineNumbers = computed(() => {
  const { start, end } = visibleLines.value;
  return Array.from({ length: end - start }, (_, index) => String(start + index + 1)).join('\n');
});

const gutterStyle = computed<CSSProperties>(() => ({
  width: `calc(${String(textModel.value.lineCount).length}ch + 28px)`
}));

const virtualTop = computed(() => PADDING_Y + visibleLines.value.start * lineHeight.value);
const contentStyle = computed<CSSProperties>(() => ({
  top: `${virtualTop.value}px`,
  left: `${PADDING_X + visibleColumns.value.start * characterWidth.value}px`,
  tabSize: String(normalizedTabSize.value)
}));
const lineNumbersStyle = computed<CSSProperties>(() => ({
  transform: `translateY(${virtualTop.value - scrollTop.value}px)`
}));
const spacerStyle = computed<CSSProperties>(() => ({
  width: `max(100%, ${PADDING_X * 2 + textModel.value.maxColumns * characterWidth.value}px)`,
  height: `${PADDING_Y * 2 + textModel.value.lineCount * lineHeight.value}px`
}));

const accessibleDescription = computed(() => props.content
  ? `共 ${textModel.value.lineCount} 行`
  : `共 1 行，${props.emptyText || '内容为空'}`);

const updateMetrics = () => {
  const scroller = scrollerRef.value;
  const measure = measureRef.value;
  if (!scroller || !measure) return;
  const style = window.getComputedStyle(measure);
  const measuredLineHeight = Number.parseFloat(style.lineHeight);
  const measuredCharacterWidth = measure.getBoundingClientRect().width / 10;
  if (Number.isFinite(measuredLineHeight)) lineHeight.value = measuredLineHeight;
  if (Number.isFinite(measuredCharacterWidth) && measuredCharacterWidth > 0) characterWidth.value = measuredCharacterWidth;
  viewportWidth.value = scroller.clientWidth;
  viewportHeight.value = scroller.clientHeight;
};

const handleScroll = () => {
  if (scrollFrame !== undefined) return;
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = undefined;
    scrollTop.value = scrollerRef.value?.scrollTop || 0;
    scrollLeft.value = scrollerRef.value?.scrollLeft || 0;
  });
};

const focus = (options?: FocusOptions) => scrollerRef.value?.focus(options);
const scrollToTop = (behavior: ScrollBehavior = 'auto') => scrollerRef.value?.scrollTo({ top: 0, left: 0, behavior });
const scrollToLine = (line: number, options: ScrollToLineOptions = {}) => {
  const scroller = scrollerRef.value;
  if (!scroller || !Number.isFinite(line)) return;
  const target = Math.min(textModel.value.lineCount, Math.max(1, Math.trunc(line)));
  const block = options.block || 'start';
  const offset = block === 'center'
    ? (scroller.clientHeight - lineHeight.value) / 2
    : block === 'end' ? scroller.clientHeight - lineHeight.value - PADDING_Y : PADDING_Y;
  scroller.scrollTo({
    top: Math.max(0, PADDING_Y + (target - 1) * lineHeight.value - offset),
    behavior: options.behavior || 'auto'
  });
};

watch(() => props.content, () => nextTick(updateMetrics));
onMounted(() => {
  updateMetrics();
  if (typeof ResizeObserver !== 'undefined' && rootRef.value) {
    resizeObserver = new ResizeObserver(updateMetrics);
    resizeObserver.observe(rootRef.value);
  }
});
onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame);
});

defineExpose({ focus, scrollToTop, scrollToLine });
</script>

<style scoped>
.plain-text-viewer {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-bg-color, #fff);
}

.plain-text-viewer__gutter {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  min-width: 48px;
  height: 100%;
  overflow: hidden;
  border-right: 1px solid var(--el-border-color-lighter, #ebeef5);
  color: var(--el-text-color-secondary, #909399);
  background: var(--el-fill-color-light, #f5f7fa);
  user-select: none;
}

.plain-text-viewer__scroller {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  outline: none;
  scrollbar-color: var(--el-scrollbar-bg-color, #c0c4cc) transparent;
  scrollbar-gutter: stable;
}

.plain-text-viewer__spacer {
  pointer-events: none;
}

.plain-text-viewer__line-numbers,
.plain-text-viewer__content,
.plain-text-viewer__measure {
  box-sizing: border-box;
  margin: 0;
  font-family: Consolas, "SFMono-Regular", "Liberation Mono", "Courier New", monospace;
  font-size: 14px;
  font-variant-ligatures: none;
  line-height: 1.7;
  white-space: pre;
}

.plain-text-viewer__line-numbers {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  padding: 0 12px 0 8px;
  font-variant-numeric: tabular-nums;
  text-align: right;
  will-change: transform;
}

.plain-text-viewer__content {
  position: absolute;
  color: inherit;
}

.plain-text-viewer__empty {
  position: absolute;
  top: 50%;
  left: 50%;
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 13px;
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.plain-text-viewer__measure {
  position: absolute;
  visibility: hidden;
  pointer-events: none;
}

.plain-text-viewer__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

@media (max-width: 600px) {
  .plain-text-viewer__line-numbers,
  .plain-text-viewer__content,
  .plain-text-viewer__measure {
    font-size: 13px;
  }
}

@media (forced-colors: active) {
  .plain-text-viewer:focus-within {
    outline: 2px solid Highlight;
    outline-offset: -2px;
  }
}
</style>
