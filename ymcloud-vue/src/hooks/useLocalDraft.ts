import { onBeforeUnmount, onMounted, watch } from 'vue';
import { useUserStore } from '@/stores/user';
import Storage from '@/utils/storage';

export function isDraftRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * 新建表单共用的本地草稿
 * 按账号隔离，最后修改后保留七天
 */
export function useLocalDraft<T>(
  kind: 'problem' | 'contest',
  data: () => T,
  active: () => boolean,
  validate: (value: unknown) => value is T,
) {
  const userStore = useUserStore();
  const key = `ymcloud:draft:${kind}:v1:${userStore.user.id}`;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let pending: { data: T; expiresAt: number } | undefined;

  function flush() {
    clearTimeout(timer);
    if (!pending) return;
    try {
      Storage.set(key, pending);
      pending = undefined;
    } catch {
      //草稿属于辅助功能，存储不可用时不打断编辑
      //下一次变更仍可重试
      return;
    }
  }

  function save() {
    if (!active()) return;
    //在当前事件内取快照，避免切换到编辑页后写入另一份表单
    pending = { data: JSON.parse(JSON.stringify(data())), expiresAt: Date.now() + 7 * 86400000 };
    clearTimeout(timer);
    timer = setTimeout(flush, 300);
  }

  function clear() {
    clearTimeout(timer);
    pending = undefined;
    try {
      Storage.remove(key);
    } catch {
      //缓存不可用不阻断表单操作，也不额外提示删除失败
      return;
    }
  }

  function restore(): T | null {
    flush();
    try {
      const cached = Storage.get<unknown>(key);
      if (cached === null) return null;
      if (!isDraftRecord(cached) || typeof cached.expiresAt !== 'number'
        || cached.expiresAt <= Date.now() || !validate(cached.data)) {
        clear();
        return null;
      }
      return cached.data;
    } catch {
      clear();
      return null;
    }
  }

  watch(data, save, { deep: true, flush: 'sync' });
  onMounted(() => window.addEventListener('pagehide', flush));
  onBeforeUnmount(() => {
    flush();
    window.removeEventListener('pagehide', flush);
  });
  return { restore, clear, flush };
}
