import { shallowRef, watchEffect, type Directive, type ShallowRef, type WatchStopHandle } from 'vue';
import { useUserStore } from '@/stores/user';

/**
 * 根据权限控制操作入口，权限变化后同步恢复或隐藏
 */
type PermissionEntry = {
  permissions: ShallowRef<string[]>;
  stop: WatchStopHandle;
};

const entries = new WeakMap<HTMLElement, PermissionEntry>();

export const permission: Directive<HTMLElement, string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore();
    const permissions = shallowRef(binding.value);
    const originalDisplay = el.style.display;
    const stop = watchEffect(() => {
      el.style.display = userStore.hasEveryPermission(permissions.value) ? originalDisplay : 'none';
    });
    entries.set(el, { permissions, stop });
  },
  updated(el, binding) {
    const entry = entries.get(el);
    if (entry) {
      entry.permissions.value = binding.value;
    }
  },
  unmounted(el) {
    entries.get(el)?.stop();
    entries.delete(el);
  },
};
