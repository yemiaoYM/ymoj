/**
 * 比赛入口访问类型，与后端 ContestConstants 保持一致
 */
export const CONTEST_VISIBILITY = {
  PUBLIC: 0,
  PASSWORD: 1,
  PRIVATE: 2,
} as const;

/**
 * 克隆比赛保存实际源比赛 ID，原创比赛使用此值
 */
export const ORIGINAL_CONTEST_SOURCE = 0;

/**
 * 比赛计分模式
 */
export const CONTEST_MODE = {
  ACM: 0,
  OI: 1,
  IOI: 2,
} as const;
