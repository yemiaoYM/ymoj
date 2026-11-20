import type { Ref } from 'vue';
import type { FormInstance } from 'element-plus';
import { useDialogState } from './useDialogState';

/** 新增时重置表单，编辑时展示已经载入的记录 */
export function useFormDialog<T extends object>(
  form: Ref<T>,
  formRef: Ref<FormInstance | undefined>,
  initialFormData: T,
) {
  const { dialog, openDialog: showDialog, closeDialog } = useDialogState();

  const openDialog = (title: string) => {
    form.value = { ...initialFormData };
    formRef.value?.clearValidate();
    showDialog(title);
  };

  return { dialog, openDialog, showDialog, closeDialog };
}
