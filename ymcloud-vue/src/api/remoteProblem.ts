import type { AxiosResponse } from 'axios';
import request from './request';
import type { RemoteProblemDraft, RemoteProblemRequest } from '@/type/remoteProblem';

export function fetchRemoteProblem(data: RemoteProblemRequest): Promise<AxiosResponse<{
  code: number;
  msg: string;
  data: RemoteProblemDraft;
}>> {
  return request({ url: '/admin/problem/remote', method: 'POST', data, timeout: 20000 });
}
