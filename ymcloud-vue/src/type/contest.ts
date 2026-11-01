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
 * 克隆只复制配置与题目关联，时间和密码由新创建者明确填写
 */
export type CloneContestRequest = {
  title: string;
  visibility: number;
  password: string | null;
  startTime: string;
  endTime: string;
};
