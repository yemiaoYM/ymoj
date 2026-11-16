import request from '@/api/request';
import type {
  SubmissionPageQuery,
  SubmissionTableVO,
  SubmissionVO,
  JudgeDTO,
  TestJudgeDTO,
  JudgeStatusQuery,
  JudgeResult,
  ProblemSubmissionPageQuery,
  SubmissionStatusDTO,
} from '@/types/submission';
import type { ApiResult, PageVO } from '@/types';

/**
 * 获取提交列表
 * @param params 查询条件
 * @returns 包含提交列表的完整响应
 */
export function getSubmissionTableList(params: SubmissionPageQuery) {
  return request<ApiResult<PageVO<SubmissionTableVO>>>({
    url: '/public/submissions',
    method: 'GET',
    params
  });
}

/**
 * 获取提交详情
 * @param id 提交ID
 * @returns 包含提交详情的完整响应
 */
export function getSubmissionDetail(id: number) {
  return request<ApiResult<SubmissionVO>>({
    url: `/submission/${id}`,
    method: 'GET',
  });
}

/**
 * 更新分享代码状态
 * @param id 提交ID
 * @returns 操作结果，不包含业务数据
 */
export function updateShareCode(id: number) {
  return request<ApiResult<void>>({
    url: `/submission/${id}/share`,
    method: 'PUT',
  });
}

/**
 * 提交题目评测
 * @param data 提交评测信息
 * @returns 包含正式提交编号的完整响应
 */
export function submitJudge(data: JudgeDTO) {
  return request<ApiResult<number>>({
    url: '/judge/submit',
    method: 'POST',
    data
  });
}

/**
 * 测试评测
 * @param data 测试评测信息
 * @returns 包含自测提交编号的完整响应
 */
export function testJudge(data: TestJudgeDTO) {
  return request<ApiResult<number>>({
    url: '/judge/test',
    method: 'POST',
    data
  });
}

/**
 * 定时轮询获取评测状态
 * @param params 评测状态信息
 * @returns 包含当前评测结果的完整响应
 */
export function getJudgeStatus(params: JudgeStatusQuery) {
  return request<ApiResult<JudgeResult>>({
    url: '/judge/status',
    method: 'GET',
    params
  });
}

/**
 * 获取题目提交列表
 * @param params 题目提交列表信息
 * @returns 包含题目提交列表的完整响应
 */
export function getProblemSubmissionList(params: ProblemSubmissionPageQuery) {
  return request<ApiResult<PageVO<SubmissionTableVO>>>({
    url: '/problem/submissions',
    method: 'GET',
    params
  });
}

/**
 * 重新评测原提交，预期执行标识用于拒绝重复或过期操作
 * @param id 原正式提交编号
 * @param executionId 当前执行标识，历史提交为 null
 * @returns 操作结果，不包含业务数据
 */
export function rejudgeSubmission(id: number, executionId: string | null) {
  return request<ApiResult<void>>({
    url: `/admin/submission/${id}/rejudge`,
    method: 'POST',
    data: { executionId },
  });
}

/**
 * 人工设置最终状态，只有部分正确需要提交分数
 * @param id 原正式提交编号
 * @param data 当前执行标识、最终状态及部分正确分数
 * @returns 操作结果，不包含业务数据
 */
export function updateSubmissionStatus(id: number, data: SubmissionStatusDTO) {
  return request<ApiResult<void>>({
    url: `/admin/submission/${id}/status`,
    method: 'PUT',
    data,
  });
}
