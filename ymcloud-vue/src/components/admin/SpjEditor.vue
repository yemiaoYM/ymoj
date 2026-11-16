<template>
  <x-flex vertical class="spj-editor" role="group" :size="10">
    <x-flex class="spj-editor-toolbar" justify="space-between" align="center">
      <x-flex align="center">
        <x-flex align="center" :wrap="false" :size="6">
          <span style="font-size: 16px">特殊判题程序代码</span>
          <el-tooltip content="使用testlib,编译语言为C++17" placement="top" :trigger="['hover', 'focus']">
            <el-icon size="20" color="#409eff" tabindex="0" aria-label="编译环境"><WarningFilled /></el-icon>
          </el-tooltip>
        </x-flex>
      </x-flex>
      <x-flex>
        <el-button :disabled="disabled || pending || code.length > 0" @click="code = template">填入示例</el-button>
        <el-button type="primary" :icon="StarFilled" :loading="pending" :disabled="disabled || !code.trim()" @click="handleCompile">
          编译检查
        </el-button>
      </x-flex>
    </x-flex>
    <Codemirror
      v-model:value="code"
      style="overflow: hidden;font-size: 16px;"
      :options="options"
      height="360px"
      width="100%"
    />
  </x-flex>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, watch } from 'vue';
import { StarFilled, WarningFilled } from '@element-plus/icons-vue';
import Codemirror from 'codemirror-editor-vue3';
import 'codemirror/lib/codemirror.css';
import 'codemirror/theme/solarized.css';
import 'codemirror/theme/monokai.css';
import 'codemirror/mode/clike/clike.js';
import 'codemirror/addon/fold/foldgutter.css';
import 'codemirror/addon/fold/foldgutter.js';
import 'codemirror/addon/fold/brace-fold.js';
import 'codemirror/addon/edit/matchbrackets.js';
import 'codemirror/addon/edit/closebrackets.js';
import { compileSpj } from '@/api/problem';
import { useAsyncChange } from '@/hooks/useAsyncChange';
import { useThemeStore } from '@/stores/theme';
import { ElNotification } from 'element-plus';

const props = defineProps<{ disabled: boolean }>();
const code = defineModel<string>('code', { required: true });
const language = defineModel<string>('language', { required: true });
const emit = defineEmits<{ busy: [value: boolean] }>();
const theme = useThemeStore();
const { pending, beforeChange } = useAsyncChange();
let revision = 0;

const options = computed(() => ({
  mode: 'text/x-c++src',
  theme: theme.isDark ? 'monokai' : 'solarized',
  lineNumbers: true,
  foldGutter: true,
  gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
  lineWrapping: true,
  matchBrackets: true,
  autoCloseBrackets: true,
  screenReaderLabel: '特殊判题程序代码',
  tabSize: 4,
  indentUnit: 4,
  readOnly: props.disabled || pending.value,
}));

const template = `#include "testlib.h"

/*
 * 示例：比较一个整数答案，多解题请替换为题目自己的校验逻辑
 * inf 为输入，ouf 为选手输出，ans 为标准答案
 */
int main(int argc, char *argv[]) {
    registerTestlibCmd(argc, argv);
    long long expected = ans.readLong();
    long long actual = ouf.readLong();
    if (actual != expected) {
        quitf(_wa, "expected %lld, found %lld", expected, actual);
    }
    quitf(_ok, "answer is correct");
}
`;

watch([code, language], () => {
  revision++;
});
watch(pending, value => emit('busy', value), { flush: 'sync' });
onBeforeUnmount(() => {
  revision++;
  emit('busy', false);
});

/**
 * 编译当前编辑内容，切换题目或离开页面后不写回旧结果
 */
async function handleCompile() {
  const current = revision;
  await beforeChange(async () => {
    const response = await compileSpj({ language: language.value, code: code.value });
    if (current !== revision) {
      return false;
    }
    const result: { success: boolean; message: string } = response.data.data;
    if (result.success) {
      ElNotification({
        title: '编译成功',
        message: '特殊判题程序代码编译成功',
        duration: 0,
        type: 'success',
        position: 'bottom-right',
      });
    } else {
      ElNotification({
        title: '编译失败',
        message: h('pre', { style: { whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' } }, result.message),
        duration: 0,
        type: 'error',
        position: 'bottom-right',
      });
    }
  });
}
</script>

<style scoped>
.spj-editor {
  width: 100%;
  min-width: 0;
  padding: 10px;
  box-sizing: border-box;
  border: 1px solid var(--ym-editor-border);
  border-radius: 8px;
  background-color: var(--ym-surface);
}

.spj-editor-toolbar {
  padding-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
