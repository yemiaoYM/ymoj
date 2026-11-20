import { onBeforeUnmount } from 'vue';
import { useLoading } from './useLoading';

export type ChangeResult = boolean | void;

/**
 * 复用异步交互的等待、取消及失败边界，业务成功后才允许组件提交值
 */
export function useAsyncChange() {
  const { loading: pending, withLoading } = useLoading();
  let disposed = false;

  const beforeChange = async (action: () => ChangeResult | Promise<ChangeResult>): Promise<boolean> => {
    if (pending.value || disposed) return false;
    try {
      const result = await withLoading(action);
      return !disposed && result !== false;
    } catch {
      // 取消或请求失败时保留原值，接口错误由请求层提示
      return false;
    }
  };

  onBeforeUnmount(() => { disposed = true; });
  return { pending, beforeChange };
}
