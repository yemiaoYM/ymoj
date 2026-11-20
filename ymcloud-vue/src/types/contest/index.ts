import type { AdminProblemPageQuery } from '@/types/problem';
import type { PageQuery } from '@/types';

/**
 * 公开比赛详情
 */
export type ContestVO = {
  /**
   * 比赛创建者用户名或主办方
   */
  creatorUsername: string;
  /**
   * 比赛标题
   */
  title: string;
  /**
   * 比赛说明内容
   */
  description: string;
  /**
   * 来源比赛ID（0表示原创，其它为克隆赛ID）
   */
  sourceId: number;
  /**
   * 是否计入Rating积分（0=否，1=是）
   */
  rated: number;
  /**
   * 赛制模式：0=ACM，1=OI，2=IOI
   */
  mode: number;
  /**
   * 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
   */
  style: number;
  /**
   * 比赛开始时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  startTime: string;
  /**
   * 比赛结束时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  endTime: string;
  /**
   * 当前时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  now: string;
  /**
   * 比赛当前状态
   */
  status: string;
  /**
   * 比赛时长（单位：分钟）
   */
  duration: number;
  /**
   * 访问类型：0公开、1密码、2私有
   */
  visibility: number;
  /**
   * 当前用户是否可赛前预览及管理该比赛
   */
  canManage: boolean;
  /**
   * 当前用户是否具备克隆源比赛的资格，选题另按原题状态检查
   */
  canClone: boolean;
  /**
   * 参与人数
   */
  count: number;
  /**
   * 当前用户的比赛权限
   */
  access: boolean;
};

/**
 * 公开比赛列表项
 */
export type ContestTableVO = {
  /**
   * 比赛id
   */
  id: number;
  /**
   * 比赛创建者用户名或主办方
   */
  creatorUsername: string;
  /**
   * 比赛Logo图片路径
   */
  image: string | null;
  /**
   * 比赛标题
   */
  title: string;
  /**
   * 比赛状态
   */
  status: string;
  /**
   * 来源比赛ID（0表示原创，其它为克隆赛ID）
   */
  sourceId: number;
  /**
   * 是否计入Rating积分（0=否，1=是）
   */
  rated: number;
  /**
   * 赛制模式：0=ACM，1=OI，2=IOI
   */
  mode: number;
  /**
   * 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
   */
  style: number;
  /**
   * 访问权限：0=公开，1=密码，2=私有
   */
  visibility: number;
  /**
   * 比赛开始时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  startTime: string;
  /**
   * 比赛结束时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  endTime: string;
  /**
   * 比赛时长（单位：分钟）
   */
  duration: number;
  /**
   * 参与人数
   */
  participantCount: number;
};

/**
 * 后台比赛列表项
 */
export type AdminContest = {
  /**
   * 比赛ID
   */
  id: number;
  /**
   * 比赛创建者用户ID
   */
  creatorId: number;
  /**
   * 比赛创建者用户名或主办方
   */
  creatorUsername: string;
  /**
   * 比赛Logo图片路径
   */
  image: string | null;
  /**
   * 比赛标题
   */
  title: string;
  /**
   * 比赛说明内容
   */
  description: string;
  /**
   * 来源比赛ID（0表示原创，其它为克隆赛ID）
   */
  sourceId: number;
  /**
   * 是否计入Rating积分（0=否，1=是）
   */
  rated: number;
  /**
   * 赛制模式：0=ACM，1=OI，2=IOI
   */
  mode: number;
  /**
   * 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
   */
  style: number;
  /**
   * 访问权限：0=公开，1=密码，2=私有
   */
  visibility: number;
  /**
   * 比赛密码（密码模式时使用）
   */
  password: string | null;
  /**
   * 比赛开始时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  startTime: string;
  /**
   * 比赛结束时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  endTime: string;
  /**
   * 比赛时长（单位：分钟）
   */
  duration: number;
  /**
   * 创建时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string;
  /**
   * 更新时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  updatedTime: string;
};

/**
 * 保存比赛参数
 */
export type ContestDTO = {
  /**
   * 新增时绑定的已上传正文图片 ID
   */
  fileIds?: string[];
  /**
   * 比赛标题
   */
  title: string;
  /**
   * 公开封面图片地址
   * null 使用原有默认图
   */
  image: string | null;
  /**
   * 比赛说明内容
   */
  description: string;
  /**
   * 是否计入Rating积分（0=否，1=是）
   */
  rated: number;
  /**
   * 赛制模式：0=ACM，1=OI，2=IOI
   */
  mode: number;
  /**
   * 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
   */
  style: number;
  /**
   * 访问权限：0=公开，1=密码，2=私有
   */
  visibility: number;
  /**
   * 比赛密码（密码模式时使用）
   */
  password: string | null;
  /**
   * 比赛开始时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  startTime: string;
  /**
   * 比赛结束时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  endTime: string;
};

/**
 * 报名密码比赛参数
 */
export type RegisterContestDTO = {
  contestId: number;
  password: string;
};

/**
 * 克隆比赛参数
 */
export type CloneContestRequest = {
  /**
   * 新比赛标题
   */
  title: string;
  /**
   * 0公开、1密码、2私有
   */
  visibility: number;
  /**
   * 新密码，不继承源比赛密码
   */
  password: string | null;
  /**
   * 新比赛开始时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  startTime: string;
  /**
   * 新比赛结束时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  endTime: string;
};

/**
 * 后台比赛题目关联
 */
export type AdminContestProblemVO = {
  /**
   * 比赛题目记录id
   */
  id: number;
  /**
   * 该题目在比赛中的顺序id
   */
  displayId: string;
  /**
   * 该题目在比赛中的标题，默认为原名字
   */
  displayTitle: string;
  /**
   * 气球颜色
   */
  color: string | null;
  /**
   * 题目id
   */
  problemId: number;
  /**
   * 该题目原展示id
   */
  problemDisplayId: string;
  /**
   * 该题目原名字
   */
  problemDisplayTitle: string;
  /**
   * 出题人用户名
   */
  authorUsername: string;
};

/**
 * 公开比赛题目列表项
 */
export type ContestProblemTableVO = {
  /**
   * 该题目在比赛中的顺序id
   */
  displayId: string;
  /**
   * 该题目在比赛中的标题，默认为原名字
   */
  displayTitle: string;
  /**
   * 气球颜色
   */
  color: string | null;
  /**
   * 该题目的ac通过数
   */
  ac: number;
  /**
   * 该题目的总提交数
   */
  total: number;
  /**
   * 用户在该题目的通过状态
   */
  accepted: boolean;
};

/**
 * 排名题目信息
 */
export type ContestProblemVO = {
  /**
   * 展示id（A，B，C等等）
   */
  displayId: string;
  /**
   * 题目
   */
  displayTitle: string;
  /**
   * 气球颜色
   */
  color: string | null;
  /**
   * ac数量
   */
  ac: number;
  /**
   * 总提交数
   */
  total: number;
};

/**
 * 用户在比赛单题上的得分与通过状态
 */
export type ContestSubmissionVO = {
  /**
   * 错误数
   */
  errorNum: number;
  /**
   * 是否通过
   */
  isAC: number;
  /**
   * 通过时间与比赛开始时间之差
   */
  actime: number;
  /**
   * 是否为一血
   */
  isFirstAC: number;
  /**
   * 本题获取的最高分数
   */
  score: number;
};

/**
 * 比赛用户排名
 */
export type ContestRankVO = {
  /**
   * 比赛排名
   */
  rank: number;
  /**
   * 用户名
   */
  username: string;
  /**
   * 昵称
   */
  nickname: string | null;
  /**
   * 学校
   */
  school: string | null;
  /**
   * 通过数
   */
  ac: number;
  /**
   * 提交总数
   */
  total: number;
  /**
   * 总时间=所有AC题目的耗时+罚时
   */
  totalTime: number;
  /**
   * 总分数
   */
  totalScore: number;
  /**
   * 各个题目的提交情况
   * key为比赛题目的displayId
   */
  submissionInfo: Record<string, ContestSubmissionVO>;
};

/**
 * 比赛排名结果
 */
export type ContestRankTableVO = {
  /**
   * 赛制模式：0=ACM，1=OI，2=IOI
   */
  mode: number;
  /**
   * 比赛题目信息
   */
  problemInfoData: ContestProblemVO[];
  /**
   * 用户排名信息
   */
  userRankData: ContestRankVO[];
  /**
   * 总记录数
   */
  total: number;
};

/**
 * 比赛列表查询条件
 */
export type ContestPageQuery = PageQuery & {
  /**
   * 关键字
   */
  keyword?: string | null;
};

/**
 * 比赛单题提交查询条件
 */
export type ContestProblemSubmissionPageQuery = PageQuery & {
  /**
   * 题目唯一标识
   */
  displayId: string;
  /**
   * 比赛id
   */
  contestId: number;
};

/**
 * 比赛提交查询条件
 */
export type ContestSubmissionPageQuery = PageQuery & {
  /**
   * 比赛id
   */
  contestId: number;
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
 * 比赛排名查询条件
 */
export type ContestRankPageQuery = PageQuery & {
  /**
   * 比赛id
   */
  contestId: number;
  /**
   * 用户名称或者学校名称
   */
  keyword?: string | null;
};

/**
 * 后台比赛详情，结构锁定状态由服务端计算
 */
export type AdminContestVO = AdminContest & {
  structureLocked: boolean;
};

/**
 * 比赛选题查询使用后台题目查询契约
 */
export type ContestProblemPageQuery = AdminProblemPageQuery;
