import { ref } from 'vue';

/**
 * 在异步操作执行期间展示加载状态
 */
export function useLoading(initialValue = false) {
  const loading = ref(initialValue);

  const setLoading = (value: boolean) => {
    loading.value = value;
  };

  const withLoading = async <T>(task: () => T | Promise<T>): Promise<T> => {
    loading.value = true;
    try {
      return await task();
    } finally {
      loading.value = false;
    }
  };

  return { loading, setLoading, withLoading };
}
