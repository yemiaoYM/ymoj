<template>
  <el-switch
    class="theme-toggle"
    size="large"
    aria-label="深色模式"
    :width="56"
    :model-value="theme.isDark"
    @change="theme.setPreference($event ? 'dark' : 'light')"
  >
    <template #active-action>
      <el-icon class="theme-glyph moon" aria-hidden="true"><Moon /></el-icon>
    </template>
    <template #inactive-action>
      <el-icon class="theme-glyph sun" aria-hidden="true"><Sunny /></el-icon>
    </template>
  </el-switch>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme';
import { Moon, Sunny } from '@element-plus/icons-vue';

const theme = useThemeStore();
</script>

<style scoped>
.theme-toggle {
  --el-switch-off-color: var(--el-color-primary-light-8);
  --el-switch-border-color: var(--el-color-primary-light-5);
  width: 56px;
  height: 32px;
  flex-shrink: 0;
}

.theme-toggle :deep(.el-switch__core) {
  transition: background-color var(--el-transition-duration),
    border-color var(--el-transition-duration),
    box-shadow var(--el-transition-duration);
}

.theme-toggle:hover :deep(.el-switch__core) {
  border-color: var(--el-color-primary);
  box-shadow: var(--el-box-shadow-light);
}

.theme-glyph {
  font-size: 14px;
  animation: theme-orbit 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.sun {
  color: var(--el-color-warning-dark-2);
}

.moon {
  color: var(--el-color-primary);
}

@keyframes theme-orbit {
  from {
    opacity: 0;
    transform: rotate(-90deg) scale(0.5);
  }
  to {
    opacity: 1;
    transform: rotate(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .theme-glyph {
    animation: none;
  }

  .theme-toggle :deep(.el-switch__core),
  .theme-toggle :deep(.el-switch__action) {
    transition: none;
  }
}
</style>
