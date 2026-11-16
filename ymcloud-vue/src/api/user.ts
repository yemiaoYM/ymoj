import type { AxiosResponse } from 'axios';
import request from './request';
import type { SystemSettingResult } from '@/type/types';

/**
 * 上传当前用户确认裁剪后的头像
 * @param file 裁剪后的 PNG 图片
 * @returns 头像保存结果
 */
export function uploadAvatar(file: Blob): Promise<AxiosResponse<SystemSettingResult<null>>> {
  const data = new FormData();
  data.append('file', file, 'avatar.png');
  return request({
    url: '/user/upload-avatar',
    method: 'POST',
    data,
    timeout: 120000,
  });
}
