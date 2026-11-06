/**
 * 评测状态契约，对应后端 JudgeStatus 的 code/status
 * 接口返回英文状态名，筛选和人工改判提交数字编码
 */
export const JUDGE_STATUS = {
  NOT_SUBMITTED: {
    value: -10,
    label: 'Not Submitted'
  },
  CANCELLED: {
    value: -4,
    label: 'Cancelled'
  },
  PRESENTATION_ERROR: {
    value: -3,
    label: 'Presentation Error'
  },
  COMPILE_ERROR: {
    value: -2,
    label: 'Compile Error'
  },
  WRONG_ANSWER: {
    value: -1,
    label: 'Wrong Answer'
  },
  ACCEPTED: {
    value: 0,
    label: 'Accepted'
  },
  TIME_LIMIT_EXCEEDED: {
    value: 1,
    label: 'Time Limit Exceeded'
  },
  MEMORY_LIMIT_EXCEEDED: {
    value: 2,
    label: 'Memory Limit Exceeded'
  },
  RUNTIME_ERROR: {
    value: 3,
    label: 'Runtime Error'
  },
  SYSTEM_ERROR: {
    value: 4,
    label: 'System Error'
  },
  PENDING: {
    value: 5,
    label: 'Pending'
  },
  COMPILING: {
    value: 6,
    label: 'Compiling'
  },
  JUDGING: {
    value: 7,
    label: 'Judging'
  },
  PARTIAL_ACCEPTED: {
    value: 8,
    label: 'Partial Accepted'
  },
  SUBMITTING: {
    value: 9,
    label: 'Submitting'
  },
  SUBMITTED_FAILED: {
    value: 10,
    label: 'Submitted Failed'
  },
  NULL: {
    value: 15,
    label: 'No Status'
  },
} as const;

/**
 * 人工改判仅允许最终状态，保持原有选项顺序
 */
export const FINAL_JUDGE_STATUS_OPTIONS = [
  JUDGE_STATUS.ACCEPTED,
  JUDGE_STATUS.WRONG_ANSWER,
  JUDGE_STATUS.TIME_LIMIT_EXCEEDED,
  JUDGE_STATUS.MEMORY_LIMIT_EXCEEDED,
  JUDGE_STATUS.RUNTIME_ERROR,
  JUDGE_STATUS.COMPILE_ERROR,
  JUDGE_STATUS.PRESENTATION_ERROR,
  JUDGE_STATUS.PARTIAL_ACCEPTED,
  JUDGE_STATUS.SYSTEM_ERROR,
  JUDGE_STATUS.SUBMITTED_FAILED,
  JUDGE_STATUS.CANCELLED,
] as const;

export type FinalJudgeStatusCode = typeof FINAL_JUDGE_STATUS_OPTIONS[number]['value'];

/**
 * 提交列表沿用既有筛选范围和顺序
 */
export const JUDGE_STATUS_FILTER_OPTIONS = [
  JUDGE_STATUS.ACCEPTED,
  JUDGE_STATUS.WRONG_ANSWER,
  JUDGE_STATUS.TIME_LIMIT_EXCEEDED,
  JUDGE_STATUS.MEMORY_LIMIT_EXCEEDED,
  JUDGE_STATUS.RUNTIME_ERROR,
  JUDGE_STATUS.COMPILE_ERROR,
  JUDGE_STATUS.PRESENTATION_ERROR,
  JUDGE_STATUS.PARTIAL_ACCEPTED,
  JUDGE_STATUS.SUBMITTED_FAILED,
  JUDGE_STATUS.SYSTEM_ERROR,
] as const;
