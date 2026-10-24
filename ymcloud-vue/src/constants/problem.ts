/** 题目难度等级；前后台筛选和标签共用。 */
export const PROBLEM_DIFFICULTIES = [
  { value: 1, label: '基础', english: 'Basic', type: 'info' },
  { value: 2, label: '进阶', english: 'Intermediate', type: 'success' },
  { value: 3, label: '挑战', english: 'Challenge', type: 'primary' },
  { value: 4, label: '高阶', english: 'Advanced', type: 'warning' },
  { value: 5, label: '大师', english: 'Master', type: 'danger' },
] as const;

/** 判题方式与比赛计分模式分开；当前仅实现 default。 */
export type ProblemJudgeMode = 'default' | 'spj' | 'interactive';
