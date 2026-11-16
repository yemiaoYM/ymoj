import type { PageQuery } from '@/types';

/**
 * 提交列表项
 */
export type SubmissionTableVO = {
  /**
   * 当前用户是否可重评该提交，功能权限仍由管理入口检查
   */
  canRejudge: boolean;
  /**
   * 当前执行标识，历史提交为 null，用于管理操作的并发检查
   */
  executionId: string | null;
  /**
   * 提交记录 ID
   */
  submissionId: number;
  /**
   * 题目标题
   */
  problemTitle: string;
  /**
   * 题目展示ID，例如 ABC100-A
   */
  displayId: string;
  /**
   * 用户名
   */
  username: string;
  /**
   * 编程语言名称
   */
  language: string;
  /**
   * 代码长度，单位：字符
   */
  codeLength: number;
  /**
   * 评测状态
   */
  status: string;
  /**
   * 得分，ACM 模式为 NULL，OI 模式使用
   */
  score: number | null;
  /**
   * 执行耗时(ms)
   */
  time: number | null;
  /**
   * 内存使用(KB)
   */
  memory: number | null;
  /**
   * 执行判题的机器标识
   */
  judgeServer: string | null;
  /**
   * 提交时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string;
};

/**
 * 提交详情
 */
export type SubmissionVO = {
  /**
   * 提交id
   */
  submissionId: number;
  /**
   * 题目标题
   */
  problemTitle: string;
  /**
   * 题目展示ID，例如 ABC100-A
   */
  displayId: string;
  /**
   * 用户名
   */
  username: string;
  /**
   * 编程语言名称
   */
  language: string;
  /**
   * 用户提交代码（个人或者允许分享查看）
   */
  code: string;
  /**
   * 代码长度，单位：字符
   */
  codeLength: number;
  /**
   * 评测状态
   */
  status: string;
  /**
   * 得分，ACM 模式为 NULL，OI 模式使用
   */
  score: number | null;
  /**
   * 执行耗时(ms)
   */
  time: number | null;
  /**
   * 内存使用(KB)
   */
  memory: number | null;
  /**
   * 编译错误/运行时错误信息
   */
  errorMessage: string | null;
  /**
   * 是否公开代码
   */
  share: boolean;
  /**
   * 执行判题的机器标识
   */
  judgeServer: string | null;
  /**
   * 提交时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string;
  /**
   * 各个评测点结果
   */
  judgeCases: JudgeCaseVO[];
};

/**
 * 单个测试点评测结果
 */
export type JudgeCaseVO = {
  /**
   * 样例在评测中的顺序编号（从1开始）
   */
  caseIndex: number;
  /**
   * 评测结果
   */
  status: string;
  /**
   * 运行耗时(ms)
   */
  time: number | null;
  /**
   * 运行内存(KB)
   */
  memory: number | null;
  /**
   * 该样例得分（OI模式下有效）
   */
  score: number | null;
};

/**
 * 轮询评测结果
 */
export type JudgeResult = {
  /**
   * 评测状态
   */
  status: string;
  /**
   * 运行时间
   */
  time: number | null;
  /**
   * 运行内存
   */
  memory: number | null;
  /**
   * 用户输入（只在自测模式下生效）
   */
  input: string | null;
  /**
   * 程序输出（只在自测模式下生效）
   */
  output: string | null;
  /**
   * 期望输出（只在自测模式下生效）
   */
  expectedOutput: string | null;
  /**
   * 错误内容
   */
  errorMessage: string | null;
};

/**
 * 正式提交参数
 */
export type JudgeDTO = {
  /**
   * 是否为比赛提交
   */
  isContest: boolean;
  /**
   * 所属比赛id
   */
  contestId?: number | null;
  /**
   * 题目唯一标识，当为比赛题目时，作为比赛题目的索引
   */
  problemId: string;
  /**
   * 语言唯一标识
   */
  language: string;
  /**
   * 用户代码
   */
  code: string;
};

/**
 * 在线自测参数
 */
export type TestJudgeDTO = {
  /**
   * 语言唯一标识
   */
  language: string;
  /**
   * 用户代码
   */
  code: string;
  /**
   * 用户输入
   */
  input?: string | null;
  /**
   * 期望输出
   */
  expectedOutput?: string | null;
};

/**
 * 提交列表查询条件
 */
export type SubmissionPageQuery = PageQuery & {
  /**
   * 是否仅查询当前用户提交记录
   */
  isMine: boolean;
  /**
   * 评测状态
   */
  status?: number | null;
  /**
   * 题目或题目唯一标识
   */
  problemKey?: string | null;
  /**
   * 用户名
   */
  username?: string | null;
};

/**
 * 普通题目提交查询条件
 */
export type ProblemSubmissionPageQuery = PageQuery & {
  /**
   * 题目唯一标识
   */
  problemId: string;
};

/**
 * 评测状态查询参数
 */
export type JudgeStatusQuery = {
  submissionId: number;
};

/**
 * 人工设置提交结果，执行标识用于并发控制
 */
export type SubmissionStatusDTO = {
  executionId: string | null;
  status: number;
  score: number | null;
};
