import request from './request';
import type { AxiosResponse } from 'axios';

type SubmissionResult = { code: number; msg: string; data: null };

/** 重新评测原提交，预期执行标识用于拒绝重复或过期操作 */
export function rejudgeSubmission(id: number, executionId: string | null): Promise<AxiosResponse<SubmissionResult>> {
  return request({
    url: `/admin/submission/${id}/rejudge`,
    method: 'POST',
    data: { executionId },
  });
}

/** 人工设置最终状态，只有部分正确需要提交分数 */
export function updateSubmissionStatus(
  id: number,
  data: { executionId: string | null; status: number; score: number | null },
): Promise<AxiosResponse<SubmissionResult>> {
  return request({ url: `/admin/submission/${id}/status`, method: 'PUT', data });
}
