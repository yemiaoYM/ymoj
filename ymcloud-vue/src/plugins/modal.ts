import { ElMessageBox } from 'element-plus';

/** 后台操作确认，取消或关闭时由调用方中止操作 */
export default {
  confirm(message: string, title = '系统提示', confirmButtonText = '确定') {
    return ElMessageBox.confirm(message, title, {
      confirmButtonText,
      cancelButtonText: '取消',
      type: 'warning',
    });
  },
};
