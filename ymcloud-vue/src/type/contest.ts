import type { AdminProblemVO } from './types';

/**
 * 后台比赛题目列表项，id 为比赛题目关联 ID，problemId 为原题 ID
 */
export type AdminContestProblemVO = {
  id: number;
  problemId: number;
  displayId: string;
  displayTitle: string;
  color: string;
  problemDisplayId: string;
  problemDisplayTitle: string;
  authorUsername: string;
};

/**
 * 比赛选题查询条件，不传 difficulty 时查询全部难度
 */
export type ContestProblemPageQuery = {
  pageNo: number;
  pageSize: number;
  keyword: string;
  difficulty?: number;
};

/**
 * 可添加到比赛的题目分页结果
 */
export type AvailableProblemPageVO = {
  records: AdminProblemVO[];
  total: number;
  pages: number;
};

export type ContestResult<T> = {
  code: number;
  msg: string;
  data: T;
};
