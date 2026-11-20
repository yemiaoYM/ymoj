import { reactive } from 'vue';

/** 管理新增、编辑弹窗的标题及开关 */
export function useDialogState() {
  const dialog = reactive({ title: '', visible: false });

  const openDialog = (title: string) => {
    dialog.title = title;
    dialog.visible = true;
  };

  const closeDialog = () => {
    dialog.visible = false;
  };

  return { dialog, openDialog, closeDialog };
}
