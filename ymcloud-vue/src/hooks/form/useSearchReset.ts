import type { Ref } from 'vue';
import type { FormInstance } from 'element-plus';

/** 重置筛选表单与分页，再按页面原有的查询条件加载列表 */
export function useSearchReset(
  queryFormRef: Ref<FormInstance | undefined>,
  page: Ref<{ pageNo?: number; pageSize?: number }>,
  pageSize: number,
  getList: () => Promise<void>,
) {
  const resetQuery = () => {
    queryFormRef.value?.resetFields();
    page.value.pageNo = 1;
    page.value.pageSize = pageSize;
    return getList();
  };

  return { resetQuery };
}
