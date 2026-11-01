<template>
  <!-- 沿用编辑器内置菜单层级，避免被预览区的代码工具条遮挡。 -->
  <el-dropdown
    trigger="click"
    placement="bottom-start"
    max-height="min(320px, 60vh)"
    :teleported="false"
    :popper-style="{ zIndex: 20000 }"
    :disabled="disabled"
    @command="emit('command', $event)"
  >
    <NormalToolbar :title="title" :aria-label="title" :disabled="disabled">
      <x-flex inline align="center" :wrap="false" :size="2">
        <component :is="icon" class="md-editor-icon" aria-hidden="true" />
        <ChevronDown :size="12" aria-hidden="true" />
      </x-flex>
      <span class="md-editor-toolbar-item-name">{{ title }}</span>
    </NormalToolbar>
    <template #dropdown>
      <el-dropdown-menu class="markdown-toolbar-menu">
        <el-dropdown-item
          v-for="item in items"
          :key="item.command"
          :command="item.command"
          :icon="item.icon"
        >{{ item.label }}</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import type { Component } from 'vue';
import { NormalToolbar } from 'md-editor-v3';
import { ChevronDown } from '@lucide/vue';

defineOptions({ inheritAttrs: false });
defineProps<{
  title: string;
  icon: Component;
  items: { label: string; command: string; icon: Component }[];
  disabled: boolean;
}>();
const emit = defineEmits<{ command: [command: string] }>();
</script>

<style scoped>
.markdown-toolbar-menu :deep(.el-icon) {
  font-size: 16px;
  margin-right: 8px;
  flex-shrink: 0;
}

/* 列表图标在编辑器内有默认内边距，下拉菜单中统一为完整的 16px 图形。 */
.markdown-toolbar-menu :deep(.el-icon svg) {
  width: 1em;
  height: 1em;
  padding: 0;
}
</style>
