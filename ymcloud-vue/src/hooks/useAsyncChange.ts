import { onBeforeUnmount, ref } from 'vue';

export type ChangeResult = boolean | void;

/**
 * 复用异步交互的等待、取消及失败边界,业务成功后才允许组件提交值
 */
export function useAsyncChange() {
  const pending = ref(false);
  let disposed = false;

  const beforeChange = async (action: () => ChangeResult | Promise<ChangeResult>): Promise<boolean> => {
    if (pending.value || disposed) return false;
    pending.value = true;
    try {
      const result = await action();
      return !disposed && result !== false;
    } catch {
      //确认取消或请求失败时阻止变更,接口错误由 request 统一提示
      return false;
    } finally {
      pending.value = false;
    }
  };

  onBeforeUnmount(() => { disposed = true; });
  return { pending, beforeChange };
}
