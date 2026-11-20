import { ref, shallowRef } from 'vue';

/**
 * 在表格勾选变化时同步主键、选中行及工具栏按钮状态
 */
export function useTableSelection<T, ID extends string | number = string | number>(getRowId: (row: T) => ID) {
  const ids = shallowRef<ID[]>([]);
  const selectedRows = shallowRef<T[]>([]);
  const single = ref(true);
  const multiple = ref(true);

  const handleSelectionChange = (selection: T[]) => {
    selectedRows.value = selection;
    ids.value = selection.map(getRowId).filter(id => typeof id === 'string' || typeof id === 'number');
    single.value = selection.length !== 1;
    multiple.value = selection.length === 0;
  };

  const clearSelection = () => handleSelectionChange([]);

  return { ids, selectedRows, single, multiple, handleSelectionChange, clearSelection };
}
