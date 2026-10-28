import type { AxiosResponse } from 'axios';
import request from './request';
import type { SystemSettingResult } from '@/type/types';

export function uploadContestImage(file: Blob): Promise<AxiosResponse<SystemSettingResult<string>>> {
  const data = new FormData();
  data.append('file', file, 'contest.png');
  return request({ url: '/admin/contest/image', method: 'POST', data, timeout: 120000 });
}
