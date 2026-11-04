import { computed, nextTick, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { ElMessage } from 'element-plus';

export type ThemePreference = 'light' | 'dark';

export const useThemeStore = defineStore('theme', () => {
  // index.html 在首屏绘制前完成存储边界校验
  const preference = ref(document.documentElement.dataset.themePreference as ThemePreference);
  const resolvedTheme = computed(() => preference.value);
  const isDark = computed(() => resolvedTheme.value === 'dark');

  watch(resolvedTheme, theme => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    document.documentElement.style.colorScheme = theme;
  }, { immediate: true, flush: 'sync' });

  async function setPreference(value: ThemePreference) {
    const updateTheme = async () => {
      preference.value = value;
      document.documentElement.dataset.themePreference = value;
      await nextTick();
    };
    if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) {
      await updateTheme();
    } else {
      const transition = document.startViewTransition(updateTheme);
      // 连续切换或页面隐藏时跳过快照动画，主题更新仍正常完成
      transition.ready.catch(() => transition.skipTransition());
      await transition.updateCallbackDone;
    }
    try {
      localStorage.setItem('ymoj-theme', value);
    } catch {
      // 浏览器禁止存储时保留本次选择，并告知无法跨刷新保存
      ElMessage.warning('当前浏览器无法保存主题偏好，本次切换仍然有效');
    }
  }

  return { preference, resolvedTheme, isDark, setPreference };
});
