import request from './request';

/**
 * 重新评测原提交，预期执行标识用于拒绝重复或过期操作
 * @param id 原正式提交编号
 * @param executionId 当前执行标识，历史提交为 null
 * @returns 操作结果
 */
export function rejudgeSubmission(id: number, executionId: string | null) {
  return request({
    url: `/admin/submission/${id}/rejudge`,
    method: 'POST',
    data: { executionId },
  });
}

/**
 * 人工设置最终状态，只有部分正确需要提交分数
 * @param id 原正式提交编号
 * @param data 当前执行标识、最终状态及部分正确分数
 * @returns 操作结果
 */
export function updateSubmissionStatus(
  id: number,
  data: { executionId: string | null; status: number; score: number | null },
) {
  return request({ url: `/admin/submission/${id}/status`, method: 'PUT', data });
}
